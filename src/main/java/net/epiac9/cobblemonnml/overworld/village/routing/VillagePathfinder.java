package net.epiac9.cobblemonnml.overworld.village.routing;

import net.epiac9.cobblemonnml.overworld.village.VillageEntrance;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class VillagePathfinder {
    private static final int BASE_STEP_COST = 10;
    private static final int ONE_BLOCK_ELEVATION_COST = 8;
    private static final int TWO_BLOCK_ELEVATION_COST = 40;
    private static final int WATER_COST = 16;
    private static final int EXISTING_ROAD_DISCOUNT = 7;
    private static final int MAX_ELEVATION_DELTA = 2;

    /*
     * Fallback-only penalty. This is intentionally much larger
     * than any normal bounded route, so a fully connected route
     * from any entrance pair always wins over a gap route.
     */
    private static final int HEIGHT_GAP_COST = 1_000_000;

    private static final int MAX_EXPANDED_NODES = 4096;
    private static final int ENTRANCE_LEAD_OUT_MIN = 2;
    private static final int ENTRANCE_LEAD_OUT_MAX = 4;

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private final VillageTerrainSampler terrainSampler;

    public VillagePathfinder() {
        this(
                new VillageTerrainSampler()
        );
    }

    public VillagePathfinder(
            VillageTerrainSampler terrainSampler
    ) {
        if (terrainSampler == null) {
            throw new IllegalArgumentException(
                    "terrainSampler cannot be null."
            );
        }

        this.terrainSampler =
                terrainSampler;
    }

    public Optional<PathResult> findRoute(
            ServerLevel level,
            VillageEntrance startEntrance,
            VillageEntrance endEntrance,
            ChunkPos sourceChunk,
            VillageNetworkSavedData networkData
    ) {
        Optional<PathResult> strict =
                findRouteAttempt(
                        level,
                        startEntrance,
                        endEntrance,
                        sourceChunk,
                        networkData,
                        false
                );

        if (strict.isPresent()) {
            return strict;
        }

        DebugLog.log(
                "[CobblemonNML] Normal village A* could not connect the entrances. "
                        + "Trying height-gap fallback."
        );

        Optional<PathResult> fallback =
                findRouteAttempt(
                        level,
                        startEntrance,
                        endEntrance,
                        sourceChunk,
                        networkData,
                        true
                );

        fallback.ifPresent(
                result ->
                        DebugLog.log(
                                "[CobblemonNML] Height-gap fallback route planned with "
                                        + result.heightGapCount()
                                        + " disconnected height gap(s)."
                        )
        );

        return fallback;
    }

    private Optional<PathResult> findRouteAttempt(
            ServerLevel level,
            VillageEntrance startEntrance,
            VillageEntrance endEntrance,
            ChunkPos sourceChunk,
            VillageNetworkSavedData networkData,
            boolean allowHeightGaps
    ) {
        if (level == null
                || startEntrance == null
                || endEntrance == null
                || sourceChunk == null) {
            return Optional.empty();
        }

        SearchBounds bounds =
                SearchBounds.fromSourceChunk(
                        sourceChunk
                );

        Optional<EntranceAnchor> startAnchorOptional =
                resolveEntranceAnchor(
                        level,
                        startEntrance,
                        bounds,
                        networkData,
                        allowHeightGaps
                );

        Optional<EntranceAnchor> endAnchorOptional =
                resolveEntranceAnchor(
                        level,
                        endEntrance,
                        bounds,
                        networkData,
                        allowHeightGaps
                );

        if (startAnchorOptional.isEmpty()
                || endAnchorOptional.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Village A* failed before search: "
                            + "startAnchor="
                            + (startAnchorOptional.isPresent() ? "ok" : "missing")
                            + ", endAnchor="
                            + (endAnchorOptional.isPresent() ? "ok" : "missing")
                            + "."
            );
            return Optional.empty();
        }

        EntranceAnchor startAnchor =
                startAnchorOptional.get();

        EntranceAnchor endAnchor =
                endAnchorOptional.get();

        VillageTerrainSampler.TerrainSample startSample =
                startAnchor.sample();

        VillageTerrainSampler.TerrainSample endSample =
                endAnchor.sample();

        NodeKey startKey =
                new NodeKey(
                        startAnchor.pos().getX(),
                        startAnchor.pos().getZ()
                );

        NodeKey endKey =
                new NodeKey(
                        endAnchor.pos().getX(),
                        endAnchor.pos().getZ()
                );

        PriorityQueue<OpenNode> open =
                new PriorityQueue<>(
                        Comparator
                                .comparingInt(OpenNode::fCost)
                                .thenComparingInt(OpenNode::hCost)
                );

        Map<NodeKey, Integer> gScore =
                new HashMap<>();

        Map<NodeKey, NodeKey> cameFrom =
                new HashMap<>();

        Map<NodeKey, VillageTerrainSampler.TerrainSample> samples =
                new HashMap<>();

        Set<NodeKey> closed =
                new HashSet<>();

        samples.put(
                startKey,
                startSample
        );

        gScore.put(
                startKey,
                0
        );

        int initialHeuristic =
                heuristic(
                        startKey,
                        endKey
                );

        open.add(
                new OpenNode(
                        startKey,
                        initialHeuristic,
                        initialHeuristic
                )
        );

        int expandedNodes = 0;
        int rejectedObstacleNeighbors = 0;
        int rejectedElevationNeighbors = 0;
        int rejectedOutOfBoundsNeighbors = 0;
        int acceptedNeighborRelaxations = 0;

        DebugLog.log(
                "[CobblemonNML] Village A* anchors ("
                        + (allowHeightGaps ? "height-gap-fallback" : "strict")
                        + "): start="
                        + startAnchor.pos()
                        + " y="
                        + startSample.surfacePos().getY()
                        + ", end="
                        + endAnchor.pos()
                        + " y="
                        + endSample.surfacePos().getY()
                        + ", bounds="
                        + bounds
                        + "."
        );

        while (!open.isEmpty()
                && expandedNodes < MAX_EXPANDED_NODES) {
            OpenNode currentOpen =
                    open.poll();

            NodeKey current =
                    currentOpen.key();

            if (!closed.add(current)) {
                continue;
            }

            expandedNodes++;

            if (current.equals(endKey)) {
                DebugLog.log(
                        "[CobblemonNML] Village A* succeeded ("
                                + (allowHeightGaps ? "height-gap-fallback" : "strict")
                                + "): expanded="
                                + expandedNodes
                                + ", acceptedRelaxations="
                                + acceptedNeighborRelaxations
                                + ", rejectedObstacle="
                                + rejectedObstacleNeighbors
                                + ", rejectedElevation="
                                + rejectedElevationNeighbors
                                + ", rejectedOutOfBounds="
                                + rejectedOutOfBoundsNeighbors
                                + "."
                );

                return Optional.of(
                        reconstruct(
                                level,
                                networkData,
                                startEntrance,
                                endEntrance,
                                startAnchor.leadOut(),
                                endAnchor.leadOut(),
                                startKey,
                                endKey,
                                cameFrom,
                                samples,
                                gScore.getOrDefault(
                                        endKey,
                                        Integer.MAX_VALUE
                                ),
                                expandedNodes,
                                allowHeightGaps
                        )
                );
            }

            VillageTerrainSampler.TerrainSample currentSample =
                    samples.computeIfAbsent(
                            current,
                            key ->
                                    terrainSampler.sample(
                                            level,
                                            key.x(),
                                            key.z(),
                                            networkData
                                    )
                    );

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                NodeKey next =
                        new NodeKey(
                                current.x()
                                        + direction.getStepX(),
                                current.z()
                                        + direction.getStepZ()
                        );

                if (!bounds.contains(
                        next.x(),
                        next.z()
                )) {
                    rejectedOutOfBoundsNeighbors++;
                    continue;
                }

                if (closed.contains(next)) {
                    continue;
                }

                VillageTerrainSampler.TerrainSample nextSample =
                        samples.computeIfAbsent(
                                next,
                                key ->
                                        terrainSampler.sample(
                                                level,
                                                key.x(),
                                                key.z(),
                                                networkData
                                        )
                        );

                if (nextSample.majorObstacle()) {
                    rejectedObstacleNeighbors++;
                    continue;
                }

                int diagnosticElevationDelta =
                        Math.abs(
                                nextSample.surfacePos().getY()
                                        - currentSample.surfacePos().getY()
                        );

                if (diagnosticElevationDelta > MAX_ELEVATION_DELTA
                        && !allowHeightGaps) {
                    rejectedElevationNeighbors++;
                    continue;
                }

                int stepCost =
                        stepCost(
                                currentSample,
                                nextSample,
                                allowHeightGaps
                        );

                if (stepCost == Integer.MAX_VALUE) {
                    continue;
                }

                int tentativeG =
                        gScore.getOrDefault(
                                current,
                                Integer.MAX_VALUE
                        );

                if (tentativeG == Integer.MAX_VALUE) {
                    continue;
                }

                tentativeG +=
                        stepCost;

                if (tentativeG
                        >= gScore.getOrDefault(
                                next,
                                Integer.MAX_VALUE
                        )) {
                    continue;
                }

                cameFrom.put(
                        next,
                        current
                );

                gScore.put(
                        next,
                        tentativeG
                );

                acceptedNeighborRelaxations++;

                int hCost =
                        heuristic(
                                next,
                                endKey
                        );

                open.add(
                        new OpenNode(
                                next,
                                tentativeG + hCost,
                                hCost
                        )
                );
            }
        }

        String failureReason =
                expandedNodes >= MAX_EXPANDED_NODES
                        ? "expanded-node-cap"
                        : "open-set-exhausted";

        DebugLog.log(
                "[CobblemonNML] Village A* failed ("
                        + (allowHeightGaps ? "height-gap-fallback" : "strict")
                        + "): reason="
                        + failureReason
                        + ", expanded="
                        + expandedNodes
                        + ", acceptedRelaxations="
                        + acceptedNeighborRelaxations
                        + ", rejectedObstacle="
                        + rejectedObstacleNeighbors
                        + ", rejectedElevation="
                        + rejectedElevationNeighbors
                        + ", rejectedOutOfBounds="
                        + rejectedOutOfBoundsNeighbors
                        + ", start="
                        + startAnchor.pos()
                        + ", end="
                        + endAnchor.pos()
                        + "."
        );

        return Optional.empty();
    }
    // ENTRANCE LEAD-OUT
    private Optional<EntranceAnchor> resolveEntranceAnchor(
            ServerLevel level,
            VillageEntrance entrance,
            SearchBounds bounds,
            VillageNetworkSavedData networkData,
            boolean allowHeightGaps
    ) {
        List<BlockPos> traversableLeadOut =
                new ArrayList<>();

        VillageTerrainSampler.TerrainSample previousSample =
                null;

        EntranceAnchor fallback =
                null;

        for (int distance = 1;
             distance <= ENTRANCE_LEAD_OUT_MAX;
             distance++) {

            BlockPos leadPos =
                    entrance
                            .pos()
                            .relative(
                                    entrance.facing(),
                                    distance
                            );

            if (!bounds.contains(
                    leadPos.getX(),
                    leadPos.getZ()
            )) {
                break;
            }

            VillageTerrainSampler.TerrainSample sample =
                    terrainSampler.sample(
                            level,
                            leadPos.getX(),
                            leadPos.getZ(),
                            networkData
                    );

            if (sample.majorObstacle()) {
                /*
                 * The block immediately outside the structure can
                 * legitimately still be part of its frontage. Skip
                 * unusable frontage and continue looking a few blocks
                 * outward instead of rejecting the entrance outright.
                 */
                continue;
            }

            if (previousSample != null) {
                int elevationDelta =
                        Math.abs(
                                sample.surfacePos().getY()
                                        - previousSample.surfacePos().getY()
                        );

                if (elevationDelta > MAX_ELEVATION_DELTA
                        && !allowHeightGaps) {
                    /*
                     * Do not force a lead-out across a cliff. Keep
                     * looking farther outward for a usable anchor.
                     */
                    continue;
                }
            }

            traversableLeadOut.add(
                    sample.surfacePos()
            );

            previousSample =
                    sample;

            EntranceAnchor candidate =
                    new EntranceAnchor(
                            sample.surfacePos(),
                            sample,
                            List.copyOf(
                                    traversableLeadOut
                            )
                    );

            /*
             * Prefer at least two blocks of straight frontage so the
             * finished road visibly exits the structure before A*
             * starts turning. Keep a one-block candidate only as a
             * fallback when terrain gives us nothing better.
             */
            if (distance >= ENTRANCE_LEAD_OUT_MIN) {
                return Optional.of(candidate);
            }

            fallback =
                    candidate;
        }

        return Optional.ofNullable(
                fallback
        );
    }

    private int stepCost(
            VillageTerrainSampler.TerrainSample current,
            VillageTerrainSampler.TerrainSample next,
            boolean allowHeightGaps
    ) {
        if (next.majorObstacle()) {
            return Integer.MAX_VALUE;
        }

        int elevationDelta =
                Math.abs(
                        next.surfacePos().getY()
                                - current.surfacePos().getY()
                );

        if (elevationDelta > MAX_ELEVATION_DELTA
                && !allowHeightGaps) {
            return Integer.MAX_VALUE;
        }

        int cost =
                BASE_STEP_COST;

        if (elevationDelta > MAX_ELEVATION_DELTA) {
            /*
             * This transition is intentionally allowed only during
             * the fallback search. Its huge cost makes A* use as few
             * disconnected cliff transitions as possible.
             */
            cost +=
                    HEIGHT_GAP_COST
                            + elevationDelta * 100;
        }

        if (elevationDelta == 1) {
            cost +=
                    ONE_BLOCK_ELEVATION_COST;
        } else if (elevationDelta == 2) {
            cost +=
                    TWO_BLOCK_ELEVATION_COST;
        }

        if (next.water()) {
            cost +=
                    WATER_COST;
        }

        if (next.existingRoad()) {
            cost =
                    Math.max(
                            1,
                            cost - EXISTING_ROAD_DISCOUNT
                    );
        }

        return cost;
    }

    private static int heuristic(
            NodeKey from,
            NodeKey to
    ) {
        return (
                Math.abs(from.x() - to.x())
                        + Math.abs(from.z() - to.z())
        ) * BASE_STEP_COST;
    }

    private PathResult reconstruct(
            ServerLevel level,
            VillageNetworkSavedData networkData,
            VillageEntrance startEntrance,
            VillageEntrance endEntrance,
            List<BlockPos> startLeadOut,
            List<BlockPos> endLeadOut,
            NodeKey startKey,
            NodeKey endKey,
            Map<NodeKey, NodeKey> cameFrom,
            Map<NodeKey, VillageTerrainSampler.TerrainSample> samples,
            int totalCost,
            int expandedNodes,
            boolean allowHeightGaps
    ) {
        List<NodeKey> reversed =
                new ArrayList<>();

        NodeKey current =
                endKey;

        reversed.add(current);

        while (!current.equals(startKey)) {
            current =
                    cameFrom.get(current);

            if (current == null) {
                return new PathResult(
                        startEntrance,
                        endEntrance,
                        List.of(),
                        totalCost,
                        expandedNodes,
                        false
                );
            }

            reversed.add(current);
        }

        Collections.reverse(reversed);

        List<VillageRouteNode> nodes =
                new ArrayList<>();

        /*
         * Start on the actual marker connection point, then keep the
         * short straight lead-out before entering the free A* route.
         */
        nodes.add(
                new VillageRouteNode(
                        startEntrance.pos(),
                        VillageRouteNode.SegmentType.GROUND
                )
        );

        for (BlockPos leadPos : startLeadOut) {
            if (leadPos.equals(
                    startEntrance.pos()
            )) {
                continue;
            }

            VillageTerrainSampler.TerrainSample leadSample =
                    terrainSampler.sample(
                            level,
                            leadPos.getX(),
                            leadPos.getZ(),
                            networkData
                    );

            nodes.add(
                    new VillageRouteNode(
                            leadPos,
                            leadSample.water()
                                    ? VillageRouteNode.SegmentType.WATER_CROSSING
                                    : VillageRouteNode.SegmentType.GROUND
                    )
            );
        }

        for (NodeKey key : reversed) {
            VillageTerrainSampler.TerrainSample sample =
                    samples.get(key);

            if (sample == null) {
                continue;
            }

            BlockPos samplePos =
                    sample.surfacePos();

            if (!nodes.isEmpty()
                    && nodes
                            .get(nodes.size() - 1)
                            .pos()
                            .equals(samplePos)) {
                continue;
            }

            nodes.add(
                    new VillageRouteNode(
                            samplePos,
                            sample.water()
                                    ? VillageRouteNode.SegmentType.WATER_CROSSING
                                    : VillageRouteNode.SegmentType.GROUND
                    )
            );
        }

        /*
         * Walk the destination lead-out in reverse so the route
         * approaches the destination structure straight-on.
         */
        for (int i = endLeadOut.size() - 1;
             i >= 0;
             i--) {
            BlockPos leadPos =
                    endLeadOut.get(i);

            if (!nodes.isEmpty()
                    && nodes
                            .get(nodes.size() - 1)
                            .pos()
                            .equals(leadPos)) {
                continue;
            }

            VillageTerrainSampler.TerrainSample leadSample =
                    terrainSampler.sample(
                            level,
                            leadPos.getX(),
                            leadPos.getZ(),
                            networkData
                    );

            nodes.add(
                    new VillageRouteNode(
                            leadPos,
                            leadSample.water()
                                    ? VillageRouteNode.SegmentType.WATER_CROSSING
                                    : VillageRouteNode.SegmentType.GROUND
                    )
            );
        }

        nodes.add(
                new VillageRouteNode(
                        endEntrance.pos(),
                        VillageRouteNode.SegmentType.GROUND
                )
        );

        List<VillageRouteNode> finalizedNodes =
                allowHeightGaps
                        ? markHeightGaps(nodes)
                        : List.copyOf(nodes);

        return new PathResult(
                startEntrance,
                endEntrance,
                finalizedNodes,
                totalCost,
                expandedNodes,
                allowHeightGaps
        );
    }

    private static List<VillageRouteNode> markHeightGaps(
            List<VillageRouteNode> nodes
    ) {
        if (nodes == null
                || nodes.size() < 2) {
            return nodes == null
                    ? List.of()
                    : List.copyOf(nodes);
        }

        List<VillageRouteNode> marked =
                new ArrayList<>(
                        nodes
                );

        for (int i = 1;
             i < marked.size();
             i++) {
            VillageRouteNode previous =
                    marked.get(i - 1);

            VillageRouteNode current =
                    marked.get(i);

            int elevationDelta =
                    Math.abs(
                            current.pos().getY()
                                    - previous.pos().getY()
                    );

            if (elevationDelta <= MAX_ELEVATION_DELTA) {
                continue;
            }

            /*
             * Mark the destination side of the steep transition as
             * a non-buildable gap. The road remains present on both
             * sides, but Step 8 leaves this point physically open.
             */
            marked.set(
                    i,
                    new VillageRouteNode(
                            current.pos(),
                            VillageRouteNode.SegmentType.HEIGHT_GAP
                    )
            );
        }

        return List.copyOf(
                marked
        );
    }

    public record PathResult(
            VillageEntrance sourceEntrance,
            VillageEntrance destinationEntrance,
            List<VillageRouteNode> nodes,
            int totalCost,
            int expandedNodes,
            boolean heightGapFallback
    ) {
        public PathResult {
            nodes = List.copyOf(nodes);
        }

        public boolean isUsable() {
            return !nodes.isEmpty();
        }

        public int heightGapCount() {
            int count = 0;

            for (VillageRouteNode node : nodes) {
                if (node.segmentType()
                        == VillageRouteNode.SegmentType.HEIGHT_GAP) {
                    count++;
                }
            }

            return count;
        }

        public boolean hasHeightGaps() {
            return heightGapCount() > 0;
        }
    }

    private record EntranceAnchor(
            BlockPos pos,
            VillageTerrainSampler.TerrainSample sample,
            List<BlockPos> leadOut
    ) {
        private EntranceAnchor {
            pos = pos.immutable();
            leadOut = List.copyOf(leadOut);
        }
    }

    private record NodeKey(
            int x,
            int z
    ) {
    }

    private record OpenNode(
            NodeKey key,
            int fCost,
            int hCost
    ) {
    }

    public record SearchBounds(
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        static SearchBounds fromSourceChunk(
                ChunkPos sourceChunk
        ) {
            int minChunkX =
                    sourceChunk.x - 1;

            int maxChunkX =
                    sourceChunk.x + 1;

            int minChunkZ =
                    sourceChunk.z - 1;

            int maxChunkZ =
                    sourceChunk.z + 1;

            return new SearchBounds(
                    minChunkX << 4,
                    (maxChunkX << 4) + 15,
                    minChunkZ << 4,
                    (maxChunkZ << 4) + 15
            );
        }

        boolean contains(
                int x,
                int z
        ) {
            return x >= minX
                    && x <= maxX
                    && z >= minZ
                    && z <= maxZ;
        }
    }
}
