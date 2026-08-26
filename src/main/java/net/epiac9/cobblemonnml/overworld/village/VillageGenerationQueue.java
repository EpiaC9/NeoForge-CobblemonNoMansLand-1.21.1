package net.epiac9.cobblemonnml.overworld.village;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.job.VillageNeighborScanJob;
import net.epiac9.cobblemonnml.overworld.village.job.VillageRouteJob;
import net.epiac9.cobblemonnml.overworld.village.job.VillageRoadBuildJob;
import net.epiac9.cobblemonnml.overworld.village.job.VillageRecoveryJob;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRoute;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageGenerationQueue {
    /*
     * Each server tick has a small bounded work budget.
     * Neighbor discovery and road placement are progressive.
     * A* planning remains limited to at most one route request
     * per tick and is itself hard-bounded.
     */
    private static final int WORK_UNITS_PER_TICK = 8;

    private static final Map<MinecraftServer, QueueState> SERVER_QUEUES =
            new IdentityHashMap<>();

    private VillageGenerationQueue() {
    }
    // NEIGHBOR SCANS
    public static void enqueueNeighborScan(
            MinecraftServer server,
            UUID sourceStructureId
    ) {
        if (server == null || sourceStructureId == null) {
            return;
        }

        QueueState state = state(server);

        if (!state.queuedScanIds.add(sourceStructureId)) {
            return;
        }

        state.neighborScans.addLast(
                new VillageNeighborScanJob(
                        sourceStructureId
                )
        );

        DebugLog.log(
                "[CobblemonNML] Queued village neighbor scan for structure "
                        + sourceStructureId
                        + "."
        );
    }
    // ROUTE REQUESTS
    public static void enqueueRoute(
            MinecraftServer server,
            VillageRouteJob routeJob
    ) {
        if (server == null || routeJob == null) {
            return;
        }

        QueueState state = state(server);

        if (!state.queuedRouteKeys.add(
                routeJob.connectionKey()
        )) {
            return;
        }

        VillageNetworkSavedData data =
                VillageNetworkSavedData.get(
                        server.overworld()
                );

        if (data.hasConnection(
                routeJob.sourceStructureId(),
                routeJob.destinationStructureId()
        )) {
            state.queuedRouteKeys.remove(
                    routeJob.connectionKey()
            );
            return;
        }

        if (!data.hasStructure(
                routeJob.sourceStructureId()
        )
                || !data.hasStructure(
                routeJob.destinationStructureId()
        )) {
            state.queuedRouteKeys.remove(
                    routeJob.connectionKey()
            );
            data.removeConnectionState(
                    routeJob.connectionKey()
            );
            return;
        }

        data.markConnectionQueued(
                routeJob.connectionKey()
        );

        state.routeJobs.addLast(routeJob);

        DebugLog.log(
                "[CobblemonNML] Queued village route "
                        + routeJob.sourceStructureId()
                        + " <-> "
                        + routeJob.destinationStructureId()
                        + "."
        );
    }
    // TICK
    public static void tick(ServerLevel overworld) {
        if (overworld == null) {
            return;
        }

        MinecraftServer server =
                overworld.getServer();

        QueueState state =
                state(server);

        if (!state.recoveryInitialized) {
            state.recoveryInitialized = true;

            recoverPersistedConnections(
                    overworld,
                    state
            );
        }

        int remainingBudget =
                WORK_UNITS_PER_TICK;
        // RESTART RECOVERY PREPARATION
        while (remainingBudget > 0
                && !state.recoveryJobs.isEmpty()) {
            VillageRecoveryJob recoveryJob =
                    state.recoveryJobs.peekFirst();

            boolean complete =
                    recoveryJob.processOneUnit(
                            overworld
                    );

            remainingBudget--;

            if (!complete) {
                continue;
            }

            state.recoveryJobs.removeFirst();
            state.queuedRecoveryKeys.remove(
                    recoveryJob.connectionKey()
            );
        }
        // NEIGHBOR DISCOVERY
        while (remainingBudget > 0
                && !state.neighborScans.isEmpty()) {
            VillageNeighborScanJob job =
                    state.neighborScans.peekFirst();

            boolean complete =
                    job.processOneUnit(overworld);

            remainingBudget--;

            if (!complete) {
                continue;
            }

            state.neighborScans.removeFirst();
            state.queuedScanIds.remove(
                    job.sourceStructureId()
            );
        }
        // BOUNDED ROUTE PLANNING
        /*
         * Plan at most one route request per tick. Each A* search
         * is itself hard-bounded to the source structure's 3x3
         * chunk neighborhood and a maximum expanded-node count.
         */
        if (remainingBudget > 0
                && !state.routeJobs.isEmpty()) {
            VillageRouteJob routeJob =
                    state.routeJobs.removeFirst();

            state.queuedRouteKeys.remove(
                    routeJob.connectionKey()
            );

            routeJob.processOneUnit(
                    overworld
            );
        }
        // PROMOTE PLANNED ROUTES TO ROAD BUILD JOBS
        while (!state.plannedRoutes.isEmpty()) {
            VillageRoute plannedRoute =
                    state.plannedRoutes.removeFirst();

            if (!state.queuedRoadBuildKeys.add(
                    plannedRoute.connectionKey()
            )) {
                continue;
            }

            VillageNetworkSavedData data =
                    VillageNetworkSavedData.get(
                            overworld
                    );

            if (data.hasConnection(
                    plannedRoute.sourceStructureId(),
                    plannedRoute.destinationStructureId()
            )) {
                state.queuedRoadBuildKeys.remove(
                        plannedRoute.connectionKey()
                );
                continue;
            }

            data.markConnectionBuilding(
                    plannedRoute.connectionKey()
            );

            state.roadBuildJobs.addLast(
                    new VillageRoadBuildJob(
                            plannedRoute
                    )
            );

            DebugLog.log(
                    "[CobblemonNML] Queued village road build "
                            + plannedRoute.sourceStructureId()
                            + " <-> "
                            + plannedRoute.destinationStructureId()
                            + "."
            );
        }
        // PROGRESSIVE ROAD PLACEMENT
        while (remainingBudget > 0
                && !state.roadBuildJobs.isEmpty()) {
            VillageRoadBuildJob roadJob =
                    state.roadBuildJobs.peekFirst();

            boolean complete =
                    roadJob.processOneUnit(
                            overworld
                    );

            remainingBudget--;

            if (!complete) {
                continue;
            }

            state.roadBuildJobs.removeFirst();
            state.queuedRoadBuildKeys.remove(
                    roadJob.connectionKey()
            );
        }
    }
    // ROUTE QUEUE ACCESS
    public static int getPendingRouteCount(
            MinecraftServer server
    ) {
        QueueState state =
                SERVER_QUEUES.get(server);

        return state == null
                ? 0
                : state.routeJobs.size();
    }

    public static VillageRouteJob pollNextRoute(
            MinecraftServer server
    ) {
        QueueState state =
                SERVER_QUEUES.get(server);

        if (state == null) {
            return null;
        }

        VillageRouteJob job =
                state.routeJobs.pollFirst();

        if (job != null) {
            state.queuedRouteKeys.remove(
                    job.connectionKey()
            );
        }

        return job;
    }
    // ROAD BUILD QUEUE
    public static int getPendingRoadBuildCount(
            MinecraftServer server
    ) {
        QueueState state =
                SERVER_QUEUES.get(server);

        return state == null
                ? 0
                : state.roadBuildJobs.size();
    }
    // PLANNED ROUTES
    public static void enqueuePlannedRoute(
            MinecraftServer server,
            VillageRoute route
    ) {
        if (server == null || route == null) {
            return;
        }

        QueueState state =
                state(server);

        state.plannedRoutes.addLast(route);
    }

    public static int getPlannedRouteCount(
            MinecraftServer server
    ) {
        QueueState state =
                SERVER_QUEUES.get(server);

        return state == null
                ? 0
                : state.plannedRoutes.size();
    }

    public static VillageRoute pollNextPlannedRoute(
            MinecraftServer server
    ) {
        QueueState state =
                SERVER_QUEUES.get(server);

        if (state == null) {
            return null;
        }

        return state.plannedRoutes.pollFirst();
    }
    // RESTART RECOVERY
    private static void recoverPersistedConnections(
            ServerLevel overworld,
            QueueState state
    ) {
        VillageNetworkSavedData data =
                VillageNetworkSavedData.get(
                        overworld
                );

        int recovered = 0;

        for (VillageConnectionKey key
                : data.getRecoverableConnections()) {
            VillageStructureInstance source =
                    data.getStructure(
                            key.first()
                    );

            VillageStructureInstance destination =
                    data.getStructure(
                            key.second()
                    );

            if (source == null
                    || destination == null) {
                data.removeConnectionState(
                        key
                );
                continue;
            }

            if (!state.queuedRecoveryKeys.add(
                    key
            )) {
                continue;
            }

            ChunkPos sourceChunk =
                    representativeChunk(
                            source
                    );

            state.recoveryJobs.addLast(
                    new VillageRecoveryJob(
                            key,
                            source.id(),
                            destination.id(),
                            sourceChunk
                    )
            );

            recovered++;
        }

        if (recovered > 0) {
            DebugLog.log(
                    "[CobblemonNML] Recovered "
                            + recovered
                            + " unfinished village connection(s) after server start; "
                            + "preparing their 3x3 routing neighborhoods."
            );
        }
    }

    private static ChunkPos representativeChunk(
            VillageStructureInstance structure
    ) {
        BoundingBox bounds =
                structure.bounds();

        int centerX =
                bounds.minX()
                        + (bounds.maxX() - bounds.minX()) / 2;

        int centerZ =
                bounds.minZ()
                        + (bounds.maxZ() - bounds.minZ()) / 2;

        return new ChunkPos(
                centerX >> 4,
                centerZ >> 4
        );
    }
    // CLEAR TRANSIENT STATE
    public static void clear(
            MinecraftServer server
    ) {
        if (server == null) {
            return;
        }

        SERVER_QUEUES.remove(server);
    }
    // INTERNAL STATE
    private static QueueState state(
            MinecraftServer server
    ) {
        return SERVER_QUEUES.computeIfAbsent(
                server,
                ignored -> new QueueState()
        );
    }

    private static final class QueueState {
        private boolean recoveryInitialized;

        private final Deque<VillageRecoveryJob> recoveryJobs =
                new ArrayDeque<>();

        private final Set<VillageConnectionKey> queuedRecoveryKeys =
                new LinkedHashSet<>();

        private final Deque<VillageNeighborScanJob> neighborScans =
                new ArrayDeque<>();

        private final Set<UUID> queuedScanIds =
                new LinkedHashSet<>();

        private final Deque<VillageRouteJob> routeJobs =
                new ArrayDeque<>();

        private final Set<VillageConnectionKey> queuedRouteKeys =
                new LinkedHashSet<>();

        private final Deque<VillageRoute> plannedRoutes =
                new ArrayDeque<>();

        private final Deque<VillageRoadBuildJob> roadBuildJobs =
                new ArrayDeque<>();

        private final Set<VillageConnectionKey> queuedRoadBuildKeys =
                new LinkedHashSet<>();
    }
}
