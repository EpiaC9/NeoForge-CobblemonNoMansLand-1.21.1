package net.epiac9.cobblemonnml.overworld.village.routing;

import net.minecraft.core.BlockPos;

import java.util.Objects;

public record VillageRouteNode(
        BlockPos pos,
        SegmentType segmentType
) {
    public VillageRouteNode {
        Objects.requireNonNull( pos, "pos" );
        Objects.requireNonNull( segmentType, "segmentType" );
        pos = pos.immutable();
    }

    public enum SegmentType {
        GROUND,
        WATER_CROSSING,
        HEIGHT_GAP
    }
}
