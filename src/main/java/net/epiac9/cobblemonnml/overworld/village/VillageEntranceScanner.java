package net.epiac9.cobblemonnml.overworld.village;

import net.epiac9.cobblemonnml.block.VillageEntranceMarkerBlock;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public final class VillageEntranceScanner {
    private VillageEntranceScanner() {
    }

    public static List<VillageEntrance> captureAndRemove(
            ServerLevel level,
            BoundingBox bounds
    ) {
        if (level == null || bounds == null) {
            return List.of();
        }

        List<VillageEntrance> entrances = new ArrayList<>();

        BlockPos min = new BlockPos(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ()
        );

        BlockPos max = new BlockPos(
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
        );

        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(mutablePos);

            if (!state.is(ModBlocks.VILLAGE_ENTRANCE_MARKER.get())) {
                continue;
            }

            Direction facing = Direction.NORTH;

            if (state.hasProperty(VillageEntranceMarkerBlock.FACING)) {
                facing = state.getValue(VillageEntranceMarkerBlock.FACING);
            }

            if (facing.getAxis().isVertical()) {
                continue;
            }

            entrances.add(
                    new VillageEntrance(
                            mutablePos.immutable(),
                            facing
                    )
            );

            level.setBlock(
                    mutablePos,
                    Blocks.AIR.defaultBlockState(),
                    3
            );
        }

        return List.copyOf(entrances);
    }
}
