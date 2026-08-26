package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record QuestDefinition(
        ResourceLocation id,
        String title,
        String description,
        boolean repeatable,
        List<QuestObjectiveDefinition> objectives,
        List<QuestRewardDefinition> rewards
) {
    public QuestDefinition {
        objectives = List.copyOf(objectives);
        rewards = List.copyOf(rewards);
    }
}
