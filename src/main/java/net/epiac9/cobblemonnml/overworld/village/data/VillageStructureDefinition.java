package net.epiac9.cobblemonnml.overworld.village.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record VillageStructureDefinition(
        ResourceLocation structureId,
        boolean enabled
) {
    public VillageStructureDefinition {
        Objects.requireNonNull( structureId, "structureId" );
    }
}
