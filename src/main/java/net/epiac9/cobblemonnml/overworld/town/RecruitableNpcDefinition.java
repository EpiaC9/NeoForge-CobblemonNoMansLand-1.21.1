package net.epiac9.cobblemonnml.overworld.town;

import net.minecraft.resources.ResourceLocation;

public record RecruitableNpcDefinition(
        String id,
        ResourceLocation sourcePreset,
        ResourceLocation residentPreset,
        String alreadyMovedInDialog
) {
}
