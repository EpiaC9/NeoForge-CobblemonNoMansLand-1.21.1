package net.epiac9.cobblemonnml.events.quest.npc;

import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.minecraft.resources.ResourceLocation;

public record QuestNpcDefinition(
        ResourceLocation id,
        ResourceLocation npcPreset,
        ResourceLocation questId,
        DungeonTier tier
) {
}
