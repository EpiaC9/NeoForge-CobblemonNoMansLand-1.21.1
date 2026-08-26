package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestRunState {
    private final ResourceLocation questId;
    private final UUID dungeonSessionId;
    private final Map<String, Integer> objectiveProgress = new HashMap<>();

    public QuestRunState(ResourceLocation questId, UUID dungeonSessionId) {
        this.questId = questId;
        this.dungeonSessionId = dungeonSessionId;
    }

    public ResourceLocation getQuestId() {
        return questId;
    }

    public UUID getDungeonSessionId() {
        return dungeonSessionId;
    }

    public int getProgress(String objectiveId) {
        return objectiveProgress.getOrDefault(objectiveId, 0);
    }

    public void setProgress(String objectiveId, int value) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return;
        }
        objectiveProgress.put(objectiveId, Math.max(0, value));
    }

    public Map<String, Integer> getProgressSnapshot() {
        return Map.copyOf(objectiveProgress);
    }

    public boolean isComplete(QuestDefinition definition) {
        if (definition == null) {
            return false;
        }
        for (QuestObjectiveDefinition objective : definition.objectives()) {
            if (getProgress(objective.id()) < objective.target()) {
                return false;
            }
        }
        return true;
    }
}
