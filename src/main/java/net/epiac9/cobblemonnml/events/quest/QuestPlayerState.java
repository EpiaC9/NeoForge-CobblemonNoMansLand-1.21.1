package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class QuestPlayerState {
    private final Map<ResourceLocation, QuestRunState> activeRuns = new HashMap<>();
    private final Map<ResourceLocation, Integer> completionCounts = new HashMap<>();
    private final Map<ResourceLocation, Integer> failureCounts = new HashMap<>();

    public QuestRunState getActive(ResourceLocation questId) {
        return activeRuns.get(questId);
    }

    public void putActive(QuestRunState run) {
        if (run != null && run.getQuestId() != null) {
            activeRuns.put(run.getQuestId(), run);
        }
    }

    public QuestRunState removeActive(ResourceLocation questId) {
        return activeRuns.remove(questId);
    }

    public Collection<QuestRunState> getActiveRuns() {
        return activeRuns.values();
    }

    public int getActiveCount() {
        return activeRuns.size();
    }

    public int getCompletionCount(ResourceLocation questId) {
        return completionCounts.getOrDefault(questId, 0);
    }

    public int getFailureCount(ResourceLocation questId) {
        return failureCounts.getOrDefault(questId, 0);
    }

    public void incrementCompletion(ResourceLocation questId) {
        completionCounts.merge(questId, 1, Integer::sum);
    }

    public void incrementFailure(ResourceLocation questId) {
        failureCounts.merge(questId, 1, Integer::sum);
    }

    Map<ResourceLocation, Integer> completionCounts() {
        return completionCounts;
    }

    Map<ResourceLocation, Integer> failureCounts() {
        return failureCounts;
    }
}
