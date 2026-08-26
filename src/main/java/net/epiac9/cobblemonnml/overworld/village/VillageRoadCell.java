package net.epiac9.cobblemonnml.overworld.village;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record VillageRoadCell(
        BlockPos pos
) {
    public VillageRoadCell {
        Objects.requireNonNull( pos, "pos" );
        pos = pos.immutable();
    }
}
