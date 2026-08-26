package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public record QuestObjectiveDefinition(
        String id,
        String type,
        int target,
        ResourceLocation itemId
) {
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "command_counter",
            "trainer_battle",
            "raid_battle",
            "item"
    );

    public static QuestObjectiveDefinition counter(String id, String type, int target) {
        return new QuestObjectiveDefinition(id, type, target, null);
    }

    public static QuestObjectiveDefinition item(String id, ResourceLocation itemId, int target) {
        return new QuestObjectiveDefinition(id, "item", target, itemId);
    }

    public boolean isValid() {
        if (id == null || id.isBlank() || type == null || !SUPPORTED_TYPES.contains(type) || target < 1) {
            return false;
        }
        if ("item".equals(type)) {
            return itemId != null;
        }
        return itemId == null;
    }
}
