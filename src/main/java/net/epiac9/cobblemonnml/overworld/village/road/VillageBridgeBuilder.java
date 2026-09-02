package net.epiac9.cobblemonnml.overworld.village.road;

import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageRoadCell;
import net.epiac9.cobblemonnml.overworld.village.data.VillageDataManager;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRoute;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class VillageBridgeBuilder {
    private static final int ENVIRONMENT_RADIUS = 5;

    private VillageBridgeBuilder() {
    }
    // BUILD ONE WATER NODE
    public static BridgeBuildResult buildWaterNode(
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
            return BridgeBuildResult.failed();
        }

        BlockPos center =
                route
                        .nodes()
                        .get(nodeIndex)
                        .pos();

        Set<BlockPos> footprint =
                VillageRoadBuilder.buildRoadFootprint(
                        route,
                        nodeIndex
                );

        VillageDataManager.VillageBridgeEnvironment environment =
                resolveEnvironment(
                        level,
                        center
                );

        BlockState deckState =
                VillageDataManager.resolveBridgeDeck(
                        environment
                );

        if (deckState == null) {
            return BridgeBuildResult.failed();
        }

        List<VillageRoadCell> roadCells =
                new ArrayList<>();

        int changedBlocks = 0;
        int unchangedBlocks = 0;
        int unresolvedCells = 0;

        for (BlockPos footprintPos : footprint) {
            /*
             * A WATER_CROSSING node's Y is the sampled water-surface
             * block. Keep every lane of the bridge at that exact Y so
             * its top surface lines up naturally with the dry road.
             */
            BlockPos deckPos =
                    new BlockPos(
                            footprintPos.getX(),
                            center.getY(),
                            footprintPos.getZ()
                    );

            if (networkData.isRoadCell(
                    deckPos
            )) {
                unchangedBlocks++;

                roadCells.add(
                        new VillageRoadCell(
                                deckPos
                        )
                );

                continue;
            }

            if (!canReplaceWithDeck(
                    level,
                    deckPos
            )) {
                unresolvedCells++;
                continue;
            }

            clearLightVegetationAbove(
                    level,
                    deckPos
            );

            BlockState current =
                    level.getBlockState(
                            deckPos
                    );

            if (!current.equals(deckState)) {
                level.setBlock(
                        deckPos,
                        deckState,
                        3
                );
                changedBlocks++;
            } else {
                unchangedBlocks++;
            }

            VillageRoadCell roadCell =
                    new VillageRoadCell(
                            deckPos
                    );

            networkData.addRoadCell(
                    roadCell
            );

            roadCells.add(
                    roadCell
            );
        }

        return new BridgeBuildResult(
                changedBlocks,
                unchangedBlocks,
                unresolvedCells,
                environment,
                roadCells
        );
    }
    // BRIDGE ENVIRONMENT
    static VillageDataManager.VillageBridgeEnvironment resolveEnvironment(
            ServerLevel level,
            BlockPos center
    ) {
        int woodedScore = 0;
        int rockyScore = 0;

        int minY =
                Math.max(
                        level.getMinBuildHeight(),
                        center.getY() - 2
                );

        int maxY =
                Math.min(
                        level.getMaxBuildHeight() - 1,
                        center.getY() + 4
                );

        for (int x = center.getX() - ENVIRONMENT_RADIUS;
             x <= center.getX() + ENVIRONMENT_RADIUS;
             x++) {
            for (int z = center.getZ() - ENVIRONMENT_RADIUS;
                 z <= center.getZ() + ENVIRONMENT_RADIUS;
                 z++) {
                for (int y = minY;
                     y <= maxY;
                     y++) {

                    BlockState state =
                            level.getBlockState(
                                    new BlockPos(
                                            x,
                                            y,
                                            z
                                    )
                            );

                    if (state.is(BlockTags.LOGS)) {
                        woodedScore += 3;
                        continue;
                    }

                    if (state.is(BlockTags.LEAVES)) {
                        woodedScore++;
                        continue;
                    }

                    if (isRocky(state)) {
                        rockyScore++;
                    }
                }
            }
        }

        if (woodedScore >= 3
                && woodedScore > rockyScore) {
            return VillageDataManager.VillageBridgeEnvironment.WOODED;
        }

        if (rockyScore >= 3
                && rockyScore > woodedScore) {
            return VillageDataManager.VillageBridgeEnvironment.ROCKY;
        }

        return VillageDataManager.VillageBridgeEnvironment.NEUTRAL;
    }

    private static boolean isRocky(
            BlockState state
    ) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE);
    }
    // SAFE DECK PLACEMENT
    private static boolean canReplaceWithDeck(
            ServerLevel level,
            BlockPos deckPos
    ) {
        /*
         * Protect block entities and large vegetation. Natural ground,
         * water, air and small replaceable blocks are valid bridge
         * footprint material, including one-block shoreline abutments.
         */
        if (level.getBlockEntity(deckPos) != null) {
            return false;
        }

        BlockState state =
                level.getBlockState(
                        deckPos
                );

        return !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES);
    }

    private static void clearLightVegetationAbove(
            ServerLevel level,
            BlockPos deckPos
    ) {
        BlockPos above =
                deckPos.above();

        BlockState state =
                level.getBlockState(
                        above
                );

        if (state.isAir()) {
            return;
        }

        if (state.canBeReplaced()
                && !state.is(BlockTags.LOGS)
                && !state.is(BlockTags.LEAVES)) {
            level.setBlock(
                    above,
                    Blocks.AIR.defaultBlockState(),
                    3
            );
        }
    }
    // RESULT
    public record BridgeBuildResult(
            int changedBlocks,
            int unchangedBlocks,
            int unresolvedCells,
            VillageDataManager.VillageBridgeEnvironment environment,
            List<VillageRoadCell> roadCells
    ) {
        public BridgeBuildResult {
            roadCells = List.copyOf(
                    roadCells
            );
        }

        public boolean complete() {
            return unresolvedCells == 0;
        }

        public static BridgeBuildResult failed() {
            return new BridgeBuildResult(
                    0,
                    0,
                    1,
                    VillageDataManager.VillageBridgeEnvironment.NEUTRAL,
                    List.of()
            );
        }
    }
}
