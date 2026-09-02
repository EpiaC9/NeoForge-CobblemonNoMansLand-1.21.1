package net.epiac9.cobblemonnml.overworld.village.road;

import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageRoadCell;
import net.epiac9.cobblemonnml.overworld.village.data.VillageDataManager;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRoute;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRouteNode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VillageRoadBuilder {
    private VillageRoadBuilder() {
    }
    // BUILD ONE ROUTE NODE
    public static NodeBuildResult buildGroundNode(
            ServerLevel level,
            VillageRoute route,
            int nodeIndex,
            VillageNetworkSavedData networkData
    ) {
        if (level == null
                || route == null
                || networkData == null
                || nodeIndex < 0
                || nodeIndex >= route.nodes().size()) {
            return NodeBuildResult.empty();
        }

        VillageRouteNode node =
                route.nodes().get(nodeIndex);

        if (node.segmentType()
                == VillageRouteNode.SegmentType.WATER_CROSSING) {
            VillageBridgeBuilder.BridgeBuildResult bridgeResult =
                    VillageBridgeBuilder.buildWaterNode(
                            level,
                            route,
                            nodeIndex,
                            networkData
                    );

            return new NodeBuildResult(
                    bridgeResult.changedBlocks(),
                    bridgeResult.unchangedBlocks(),
                    !bridgeResult.complete(),
                    false,
                    bridgeResult.roadCells()
            );
        }

        if (node.segmentType()
                == VillageRouteNode.SegmentType.HEIGHT_GAP) {
            return new NodeBuildResult(
                    0,
                    0,
                    false,
                    true,
                    List.of()
            );
        }

        Set<BlockPos> footprint =
                buildRoadFootprint(
                        route,
                        nodeIndex
                );

        List<VillageRoadCell> placedCells =
                new ArrayList<>();

        int changedBlocks = 0;
        int unchangedBlocks = 0;

        for (BlockPos target : footprint) {
            BlockPos surfacePos =
                    findBuildSurface(
                            level,
                            target
                    );

            if (surfacePos == null) {
                continue;
            }

            if (networkData.isRoadCell(
                    surfacePos
            )) {
                unchangedBlocks++;

                placedCells.add(
                        new VillageRoadCell(
                                surfacePos
                        )
                );

                continue;
            }

            BlockState original =
                    level.getBlockState(
                            surfacePos
                    );

            BlockState roadState =
                    VillageDataManager.resolveRoadSurface(
                            original,
                            level.registryAccess()
                    );

            clearLightVegetationAbove(
                    level,
                    surfacePos
            );

            if (!original.equals(roadState)) {
                level.setBlock(
                        surfacePos,
                        roadState,
                        3
                );

                changedBlocks++;
            } else {
                unchangedBlocks++;
            }

            VillageRoadCell roadCell =
                    new VillageRoadCell(
                            surfacePos
                    );

            networkData.addRoadCell(
                    roadCell
            );

            placedCells.add(
                    roadCell
            );
        }

        return new NodeBuildResult(
                changedBlocks,
                unchangedBlocks,
                false,
                false,
                placedCells
        );
    }
    // 3-WIDE FOOTPRINT
    static Set<BlockPos> buildRoadFootprint(
            VillageRoute route,
            int nodeIndex
    ) {
        Set<BlockPos> result =
                new LinkedHashSet<>();

        VillageRouteNode current =
                route.nodes().get(nodeIndex);

        BlockPos center =
                current.pos();

        result.add(center);

        AxisDirection incoming =
                directionFromPrevious(
                        route,
                        nodeIndex
                );

        AxisDirection outgoing =
                directionToNext(
                        route,
                        nodeIndex
                );

        if (incoming != null) {
            addCrossSection(
                    result,
                    center,
                    incoming
            );
        }

        if (outgoing != null) {
            addCrossSection(
                    result,
                    center,
                    outgoing
            );
        }

        /*
         * A one-node route is unusual, but preserve the 3-wide
         * contract using the source entrance facing.
         */
        if (incoming == null
                && outgoing == null) {
            int stepX =
                    route
                            .sourceEntrance()
                            .facing()
                            .getStepX();

            int stepZ =
                    route
                            .sourceEntrance()
                            .facing()
                            .getStepZ();

            AxisDirection sourceDirection =
                    axisDirection(
                            stepX,
                            stepZ
                    );

            if (sourceDirection != null) {
                addCrossSection(
                        result,
                        center,
                        sourceDirection
                );
            }
        }

        return result;
    }

    private static void addCrossSection(
            Set<BlockPos> result,
            BlockPos center,
            AxisDirection routeDirection
    ) {
        /*
         * Route moving along X -> road width expands along Z.
         * Route moving along Z -> road width expands along X.
         *
         * At corners both incoming and outgoing cross-sections
         * are added, naturally filling the inside of the turn.
         */
        if (routeDirection == AxisDirection.X) {
            result.add(
                    center.offset(
                            0,
                            0,
                            -1
                    )
            );

            result.add(
                    center.offset(
                            0,
                            0,
                            1
                    )
            );
        } else {
            result.add(
                    center.offset(
                            -1,
                            0,
                            0
                    )
            );

            result.add(
                    center.offset(
                            1,
                            0,
                            0
                    )
            );
        }
    }

    private static AxisDirection directionFromPrevious(
            VillageRoute route,
            int nodeIndex
    ) {
        if (nodeIndex <= 0) {
            return null;
        }

        BlockPos previous =
                route
                        .nodes()
                        .get(nodeIndex - 1)
                        .pos();

        BlockPos current =
                route
                        .nodes()
                        .get(nodeIndex)
                        .pos();

        return axisDirection(
                current.getX() - previous.getX(),
                current.getZ() - previous.getZ()
        );
    }

    private static AxisDirection directionToNext(
            VillageRoute route,
            int nodeIndex
    ) {
        if (nodeIndex >= route.nodes().size() - 1) {
            return null;
        }

        BlockPos current =
                route
                        .nodes()
                        .get(nodeIndex)
                        .pos();

        BlockPos next =
                route
                        .nodes()
                        .get(nodeIndex + 1)
                        .pos();

        return axisDirection(
                next.getX() - current.getX(),
                next.getZ() - current.getZ()
        );
    }

    private static AxisDirection axisDirection(
            int deltaX,
            int deltaZ
    ) {
        if (deltaX == 0
                && deltaZ == 0) {
            return null;
        }

        return Math.abs(deltaX)
                >= Math.abs(deltaZ)
                ? AxisDirection.X
                : AxisDirection.Z;
    }
    // SURFACE RESOLUTION
    private static BlockPos findBuildSurface(
            ServerLevel level,
            BlockPos routePos
    ) {
        /*
         * The pathfinder's Y value is already terrain-aware.
         * Descend only a few blocks so replaceable surface
         * decoration (snow layers, grass, flowers, etc.) does
         * not become the road block itself.
         */
        int minY =
                Math.max(
                        level.getMinBuildHeight(),
                        routePos.getY() - 3
                );

        for (int y = routePos.getY();
             y >= minY;
             y--) {
            BlockPos pos =
                    new BlockPos(
                            routePos.getX(),
                            y,
                            routePos.getZ()
                    );

            BlockState state =
                    level.getBlockState(pos);

            if (!level
                    .getFluidState(pos)
                    .isEmpty()) {
                return null;
            }

            if (state.isAir()) {
                continue;
            }

            if (state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS)) {
                return null;
            }

            if (state.canBeReplaced()) {
                continue;
            }

            return pos;
        }

        return null;
    }
    // LIGHT VEGETATION CLEARING
    private static void clearLightVegetationAbove(
            ServerLevel level,
            BlockPos surfacePos
    ) {
        BlockPos above =
                surfacePos.above();

        BlockState aboveState =
                level.getBlockState(above);

        if (aboveState.isAir()) {
            return;
        }

        /*
         * Only remove lightweight replaceable decoration.
         * Leaves and logs are deliberately not bulldozed here;
         * major obstacle avoidance belongs to route planning.
         */
        if (aboveState.canBeReplaced()
                && !aboveState.is(BlockTags.LEAVES)
                && !aboveState.is(BlockTags.LOGS)) {
            level.setBlock(
                    above,
                    Blocks.AIR.defaultBlockState(),
                    3
            );
        }
    }

    private enum AxisDirection {
        X,
        Z
    }
    // RESULT
    public record NodeBuildResult(
            int changedBlocks,
            int unchangedBlocks,
            boolean waterPending,
            boolean heightGapPending,
            List<VillageRoadCell> roadCells
    ) {
        public NodeBuildResult {
            roadCells = List.copyOf(roadCells);
        }

        public static NodeBuildResult empty() {
            return new NodeBuildResult(
                    0,
                    0,
                    false,
                    false,
                    List.of()
            );
        }
    }
}
