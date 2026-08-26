package net.epiac9.cobblemonnml.events.quest.npc;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestNpcTracker {
    private static final Map<UUID, ResourceLocation> QUEST_NPCS = new HashMap<>();

    private QuestNpcTracker() {
    }

    public static void track(UUID entityId, ResourceLocation definitionId) {
        if (entityId == null || definitionId == null) return;
        QUEST_NPCS.put(entityId, definitionId);
    }

    public static void untrack(UUID entityId) {
        if (entityId != null) QUEST_NPCS.remove(entityId);
    }

    public static ResourceLocation getDefinitionId(UUID entityId) {
        return entityId == null ? null : QUEST_NPCS.get(entityId);
    }

    public static Set<UUID> getTrackedNpcs() {
        return new HashSet<>(QUEST_NPCS.keySet());
    }

    public static boolean isTrackedQuestNpc(UUID entityId) {
        return entityId != null && QUEST_NPCS.containsKey(entityId);
    }

    public static int size() {
        return QUEST_NPCS.size();
    }

    public static void clear() {
        QUEST_NPCS.clear();
    }
}
