package net.epiac9.cobblemonnml.overworld.village.job;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageGenerationQueue;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageStructureInstance;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageEntrancePairSelector;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRoute;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class VillageRouteJob implements VillageJob {
    private final VillageConnectionKey connectionKey;
    private final UUID sourceStructureId;
    private final UUID destinationStructureId;
    private final ChunkPos sourceChunk;

    private boolean processed;

    public VillageRouteJob(
            VillageConnectionKey connectionKey,
            UUID sourceStructureId,
            UUID destinationStructureId,
            ChunkPos sourceChunk
    ) {
        this.connectionKey =
                Objects.requireNonNull(
                        connectionKey,
                        "connectionKey"
                );

        this.sourceStructureId =
                Objects.requireNonNull(
                        sourceStructureId,
                        "sourceStructureId"
                );

        this.destinationStructureId =
                Objects.requireNonNull(
                        destinationStructureId,
                        "destinationStructureId"
                );

        this.sourceChunk =
                Objects.requireNonNull(
                        sourceChunk,
                        "sourceChunk"
                );

        if (!connectionKey.equals(
                VillageConnectionKey.of(
                        sourceStructureId,
                        destinationStructureId
                )
        )) {
            throw new IllegalArgumentException(
                    "Route job connection key does not match its structure IDs."
            );
        }
    }

    public VillageConnectionKey connectionKey() {
        return connectionKey;
    }

    public UUID sourceStructureId() {
        return sourceStructureId;
    }

    public UUID destinationStructureId() {
        return destinationStructureId;
    }

    public ChunkPos sourceChunk() {
        return sourceChunk;
    }

    @Override
    public boolean processOneUnit(
            ServerLevel level
    ) {
        if (processed) {
            return true;
        }

        processed = true;

        VillageNetworkSavedData data =
                VillageNetworkSavedData.get(level);

        if (data.hasConnection(
                sourceStructureId,
                destinationStructureId
        )) {
            return true;
        }

        VillageStructureInstance source =
                data.getStructure(
                        sourceStructureId
                );

        VillageStructureInstance destination =
                data.getStructure(
                        destinationStructureId
                );

        if (source == null
                || destination == null) {
            data.removeConnectionState(
                    connectionKey
            );

            DebugLog.log(
                    "[CobblemonNML] Skipped village route because one structure no longer exists: "
                            + sourceStructureId
                            + " <-> "
                            + destinationStructureId
            );
            return true;
        }

        VillageEntrancePairSelector selector =
                new VillageEntrancePairSelector();

        Optional<VillageRoute> routeOptional =
                selector.findBestRoute(
                        level,
                        source,
                        destination,
                        sourceChunk,
                        data
                );

        if (routeOptional.isEmpty()) {
            data.markConnectionDeferred(
                    connectionKey
            );

            DebugLog.log(
                    "[CobblemonNML] No bounded village route found for "
                            + sourceStructureId
                            + " <-> "
                            + destinationStructureId
                            + "."
            );
            return true;
        }

        VillageRoute route =
                routeOptional.get();

        VillageGenerationQueue.enqueuePlannedRoute(
                level.getServer(),
                route
        );

        DebugLog.log(
                "[CobblemonNML] Planned village route "
                        + sourceStructureId
                        + " <-> "
                        + destinationStructureId
                        + ": nodes="
                        + route.nodes().size()
                        + ", cost="
                        + route.totalCost()
                        + ", expanded="
                        + route.expandedNodes()
                        + "."
        );

        return true;
    }
}
