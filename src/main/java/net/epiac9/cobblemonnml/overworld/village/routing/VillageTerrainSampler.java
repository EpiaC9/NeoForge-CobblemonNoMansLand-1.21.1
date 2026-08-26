package net.epiac9.cobblemonnml.overworld.village.routing;

import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

public class VillageTerrainSampler {
    public TerrainSample sample(
            ServerLevel level,
            int x,
            int z,
            VillageNetworkSavedData networkData
    ) {
        int surfaceY =
                level.getHeight(
                        Heightmap.Types.WORLD_SURFACE,
                        x,
                        z
                ) - 1;

        if (surfaceY < level.getMinBuildHeight()) {
            return TerrainSample.blocked(
                    new BlockPos(
                            x,
                            level.getMinBuildHeight(),
                            z
                    )
            );
        }

        BlockPos surfacePos =
                new BlockPos(
                        x,
                        surfaceY,
                        z
                );

        BlockState surfaceState =
                level.getBlockState(surfacePos);

        boolean water =
                !level.getFluidState(surfacePos).isEmpty();

        BlockPos headPos =
                surfacePos.above();

        BlockState headState =
                level.getBlockState(headPos);

        boolean minorVegetation =
                headState.isAir()
                        || headState.canBeReplaced()
                        || headState.is(BlockTags.LEAVES);

        boolean majorObstacle =
                !minorVegetation
                        && !water;

        boolean existingRoad =
                networkData != null
                        && networkData.isRoadCell(surfacePos);

        return new TerrainSample(
                surfacePos,
                water,
                majorObstacle,
                existingRoad
        );
    }

    public record TerrainSample(
            BlockPos surfacePos,
            boolean water,
            boolean majorObstacle,
            boolean existingRoad
    ) {
        public TerrainSample {
            surfacePos = surfacePos.immutable();
        }

        public static TerrainSample blocked(
                BlockPos pos
        ) {
            return new TerrainSample(
                    pos,
                    false,
                    true,
                    false
            );
        }
    }
    public static String describe(
            TerrainSample sample
    ) {
        if (sample == null) {
            return "null";
        }

        return "TerrainSample{pos="
                + sample.surfacePos()
                + ", water="
                + sample.water()
                + ", majorObstacle="
                + sample.majorObstacle()
                + ", existingRoad="
                + sample.existingRoad()
                + "}";
    }

}
