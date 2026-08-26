package net.epiac9.cobblemonnml.overworld.town;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;

public final class RecruitableNpcRegistry {
    private static final RecruitableNpcDefinition GRAVEKEEPER = new RecruitableNpcDefinition(
            "gravekeeper",
            ResourceLocation.fromNamespaceAndPath("cobblemonnml", "easy_npc/preset/humanoid/quests_givers/tier_1/gravekeeper_1.npc.nbt"),
            ResourceLocation.fromNamespaceAndPath("cobblemonnml", "easy_npc/preset/humanoid/town/gravekeeper_1.npc.nbt"),
            "already_moved_in"
    );
    private static final Map<String, RecruitableNpcDefinition> DEFINITIONS = Map.of(GRAVEKEEPER.id(), GRAVEKEEPER);
    private static final Map<ResourceLocation, RecruitableNpcDefinition> BY_SOURCE_PRESET = Map.of(GRAVEKEEPER.sourcePreset(), GRAVEKEEPER);

    private RecruitableNpcRegistry() {
    }

    public static RecruitableNpcDefinition get(String id) {
        if (id == null) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : DEFINITIONS.get(normalized);
    }

    public static RecruitableNpcDefinition getBySourcePreset(ResourceLocation preset) {
        return preset == null ? null : BY_SOURCE_PRESET.get(preset);
    }
}
