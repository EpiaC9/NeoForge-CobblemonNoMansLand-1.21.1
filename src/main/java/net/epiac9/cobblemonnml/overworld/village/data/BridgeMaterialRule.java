package net.epiac9.cobblemonnml.overworld.village.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record BridgeMaterialRule(
        ResourceLocation wooded,
        ResourceLocation rocky,
        ResourceLocation fallback
) {
    public BridgeMaterialRule {
        Objects.requireNonNull( wooded, "wooded" );
        Objects.requireNonNull( rocky, "rocky" );
        Objects.requireNonNull( fallback, "fallback" );
    }
}
