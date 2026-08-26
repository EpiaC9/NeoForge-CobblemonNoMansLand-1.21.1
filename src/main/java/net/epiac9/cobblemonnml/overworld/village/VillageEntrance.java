package net.epiac9.cobblemonnml.overworld.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Objects;

public record VillageEntrance(
        BlockPos pos,
        Direction facing
) {
    public VillageEntrance {
        Objects.requireNonNull( pos, "pos" );
        Objects.requireNonNull( facing, "facing" );

        if (facing.getAxis().isVertical()) {
            throw new IllegalArgumentException(
                    "Village entrance facing must be horizontal."
            );
        }

        pos = pos.immutable();
    }
}
