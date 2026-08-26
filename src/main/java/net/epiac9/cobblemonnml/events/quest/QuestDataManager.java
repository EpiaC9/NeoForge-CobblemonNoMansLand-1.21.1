package net.epiac9.cobblemonnml.events.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestDataManager {
    private static final String DIRECTORY = "quests";
    private static Map<ResourceLocation, QuestDefinition> definitions = Map.of();

    private QuestDataManager() {
    }

    public static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, QuestDefinition> loaded = new HashMap<>();
        if (resourceManager == null) {
            definitions = Map.of();
            return;
        }

        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
                DIRECTORY,
                id -> id.getPath().endsWith(".json")
        );
        List<Map.Entry<ResourceLocation, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Map.Entry.comparingByKey());

        for (Map.Entry<ResourceLocation, Resource> entry : sorted) {
            ResourceLocation questId = toQuestId(entry.getKey());
            if (questId == null || loaded.containsKey(questId)) {
                DebugLog.log("[CobblemonNML] Rejected duplicate/invalid quest resource: " + entry.getKey());
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                QuestDefinition definition = parseDefinition(questId, json);
                if (definition == null) {
                    DebugLog.log("[CobblemonNML] Rejected invalid quest definition: " + questId);
                    continue;
                }
                loaded.put(questId, definition);
            } catch (Exception exception) {
                DebugLog.log("[CobblemonNML] Failed to load quest definition " + questId);
                exception.printStackTrace();
            }
        }

        definitions = Map.copyOf(loaded);
        DebugLog.log("[CobblemonNML] Loaded " + definitions.size() + " quest definition(s).");
    }

    public static QuestDefinition get(ResourceLocation id) {
        return id == null ? null : definitions.get(id);
    }

    public static Map<ResourceLocation, QuestDefinition> snapshot() {
        return definitions;
    }

    static QuestDefinition parseDefinition(ResourceLocation id, JsonObject json) {
        if (id == null || json == null || !json.has("objectives") || !json.has("rewards")) {
            return null;
        }

        String title = json.has("title") ? json.get("title").getAsString() : id.toString();
        String description = json.has("description") ? json.get("description").getAsString() : "";
        boolean repeatable = !json.has("repeatable") || json.get("repeatable").getAsBoolean();

        JsonArray objectiveArray = json.getAsJsonArray("objectives");
        if (objectiveArray == null || objectiveArray.isEmpty()) {
            return null;
        }
        List<QuestObjectiveDefinition> objectives = new ArrayList<>();
        Set<String> objectiveIds = new HashSet<>();
        for (JsonElement element : objectiveArray) {
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject objectiveJson = element.getAsJsonObject();
            if (!objectiveJson.has("id") || !objectiveJson.has("type") || !objectiveJson.has("target")) {
                return null;
            }
            String objectiveId = objectiveJson.get("id").getAsString();
            String objectiveType = objectiveJson.get("type").getAsString();
            int objectiveTarget = objectiveJson.get("target").getAsInt();
            QuestObjectiveDefinition objective;
            if ("item".equals(objectiveType)) {
                if (!objectiveJson.has("item")) {
                    return null;
                }
                ResourceLocation itemId = ResourceLocation.tryParse(objectiveJson.get("item").getAsString());
                objective = QuestObjectiveDefinition.item(objectiveId, itemId, objectiveTarget);
            } else {
                objective = QuestObjectiveDefinition.counter(objectiveId, objectiveType, objectiveTarget);
            }
            if (!objective.isValid() || !objectiveIds.add(objective.id())) {
                return null;
            }
            objectives.add(objective);
        }

        JsonArray rewardArray = json.getAsJsonArray("rewards");
        if (rewardArray == null) {
            return null;
        }
        List<QuestRewardDefinition> rewards = new ArrayList<>();
        for (JsonElement element : rewardArray) {
            if (!element.isJsonObject()) {
                return null;
            }
            QuestRewardDefinition reward = parseReward(element.getAsJsonObject());
            if (reward == null || !reward.isValid()) {
                return null;
            }
            rewards.add(reward);
        }

        return new QuestDefinition(id, title, description, repeatable, objectives, rewards);
    }

    private static QuestRewardDefinition parseReward(JsonObject json) {
        if (json == null || !json.has("type")) {
            return null;
        }
        String type = json.get("type").getAsString();
        if ("item".equals(type)) {
            if (!json.has("item") || !json.has("count")) {
                return null;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(json.get("item").getAsString());
            return QuestRewardDefinition.item(itemId, json.get("count").getAsInt());
        }
        if ("village_unlock".equals(type)) {
            if (!json.has("unlock_id")) {
                return null;
            }
            return QuestRewardDefinition.villageUnlock(json.get("unlock_id").getAsString());
        }
        return null;
    }

    private static ResourceLocation toQuestId(ResourceLocation resourceId) {
        if (resourceId == null) {
            return null;
        }
        String path = resourceId.getPath();
        String prefix = DIRECTORY + "/";
        String suffix = ".json";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return null;
        }
        String questPath = path.substring(prefix.length(), path.length() - suffix.length());
        return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), questPath);
    }
}
