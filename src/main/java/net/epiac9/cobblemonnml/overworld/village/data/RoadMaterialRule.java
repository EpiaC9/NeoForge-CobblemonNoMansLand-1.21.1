package net.epiac9.cobblemonnml.overworld.village.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record RoadMaterialRule(
        String family,
        List<ResourceLocation> inputs,
        ResourceLocation output
) {
    public RoadMaterialRule {
        family = Objects.requireNonNull( family, "family" ).trim();
        inputs = List.copyOf( Objects.requireNonNull( inputs, "inputs" ) );
        Objects.requireNonNull( output, "output" );

        if (family.isEmpty()) {
            throw new IllegalArgumentException( "Road material family cannot be blank." );
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException( "Road material family must contain at least one input block." );
        }
    }
}
