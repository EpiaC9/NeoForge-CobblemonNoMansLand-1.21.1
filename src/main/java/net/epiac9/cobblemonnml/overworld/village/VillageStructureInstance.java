package net.epiac9.cobblemonnml.overworld.village;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VillageStructureInstance(
        UUID id,
        ResourceLocation type,
        BoundingBox bounds,
        List<VillageEntrance> entrances
) {
    public VillageStructureInstance {
        Objects.requireNonNull( id, "id" );
        Objects.requireNonNull( type, "type" );
        Objects.requireNonNull( bounds, "bounds" );
        Objects.requireNonNull( entrances, "entrances" );

        bounds = copyBounds( bounds );

        List<VillageEntrance> safeEntrances = new ArrayList<>();

        for (VillageEntrance entrance : entrances) {
            if (entrance == null) {
                continue;
            }

            safeEntrances.add(
                    new VillageEntrance(
                            entrance.pos(),
                            entrance.facing()
                    )
            );
        }

        entrances = List.copyOf( safeEntrances );
    }

    public BoundingBox boundsCopy() {
        return copyBounds( bounds );
    }

    private static BoundingBox copyBounds(BoundingBox source) {
        return new BoundingBox(
                source.minX(),
                source.minY(),
                source.minZ(),
                source.maxX(),
                source.maxY(),
                source.maxZ()
        );
    }
}
