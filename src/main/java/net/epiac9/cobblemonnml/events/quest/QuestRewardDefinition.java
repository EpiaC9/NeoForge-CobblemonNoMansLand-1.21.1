package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.resources.ResourceLocation;

public record QuestRewardDefinition(
        String type,
        ResourceLocation item,
        int count,
        String unlockId
) {
    public static QuestRewardDefinition item(ResourceLocation item, int count) {
        return new QuestRewardDefinition("item", item, count, null);
    }

    public static QuestRewardDefinition villageUnlock(String unlockId) {
        return new QuestRewardDefinition("village_unlock", null, 0, unlockId);
    }

    public boolean isValid() {
        return switch (type) {
            case "item" -> item != null && count >= 1;
            case "village_unlock" -> unlockId != null && !unlockId.isBlank();
            default -> false;
        };
    }
}
