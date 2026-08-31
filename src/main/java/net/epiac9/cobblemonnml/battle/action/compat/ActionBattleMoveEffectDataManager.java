package net.epiac9.cobblemonnml.battle.action.compat;

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
import java.util.List;
import java.util.Map;

public final class ActionBattleMoveEffectDataManager {
    private static final String DIRECTORY = "action_battle/move_effects";
    private static volatile Map<String, List<ActionBattleMoveEffectData>> definitions = Map.of();

    private ActionBattleMoveEffectDataManager() {}

    public static void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            definitions = Map.of();
            return;
        }
        Map<String, List<ActionBattleMoveEffectData>> loaded = new HashMap<>();
        List<Map.Entry<ResourceLocation, Resource>> resources = new ArrayList<>(resourceManager.listResources(
                DIRECTORY, id -> id.getPath().endsWith(".json")
        ).entrySet());
        resources.sort(Map.Entry.comparingByKey());
        for (Map.Entry<ResourceLocation, Resource> entry : resources) {
            String moveName = moveName(entry.getKey());
            if (moveName == null || loaded.containsKey(moveName)) {
                DebugLog.log("[CobblemonNML] Rejected duplicate/invalid ACTION move-effect resource: " + entry.getKey());
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                List<ActionBattleMoveEffectData> definition = parseAll(JsonParser.parseReader(reader).getAsJsonObject());
                if (definition.isEmpty()) {
                    DebugLog.log("[CobblemonNML] Rejected invalid ACTION move-effect definition: " + entry.getKey());
                    continue;
                }
                loaded.put(moveName, definition);
            } catch (Exception exception) {
                DebugLog.log("[CobblemonNML] Failed to load ACTION move-effect definition " + entry.getKey(), exception);
            }
        }
        definitions = Map.copyOf(loaded);
        DebugLog.log("[CobblemonNML] Loaded " + definitions.size() + " ACTION move-effect override(s).");
    }

    public static ActionBattleMoveEffectData get(String moveName) {
        List<ActionBattleMoveEffectData> entries = getAll(moveName);
        return entries.isEmpty() ? null : entries.getFirst();
    }

    public static List<ActionBattleMoveEffectData> getAll(String moveName) {
        return moveName == null ? List.of() : definitions.getOrDefault(moveName, List.of());
    }

    static List<ActionBattleMoveEffectData> parseAll(JsonObject json) {
        if (json == null) return List.of();
        List<ActionBattleMoveEffectData> parsed = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            for (var element : json.getAsJsonArray("effects")) {
                if (!element.isJsonObject()) return List.of();
                ActionBattleMoveEffectData entry = parse(element.getAsJsonObject());
                if (entry == null) return List.of();
                parsed.add(entry);
            }
            return parsed.isEmpty() ? List.of() : List.copyOf(parsed);
        }
        ActionBattleMoveEffectData single = parse(json);
        return single == null ? List.of() : List.of(single);
    }

    static ActionBattleMoveEffectData parse(JsonObject json) {
        if (json == null || !json.has("effect") || !json.has("trigger") || !json.has("target") || !json.has("chance")) return null;
        float chance = json.get("chance").getAsFloat();
        if (chance <= 0.0F || chance > 1.0F) return null;
        return new ActionBattleMoveEffectData(
                json.get("effect").getAsString(),
                json.get("trigger").getAsString(),
                json.get("target").getAsString(),
                chance,
                json.has("secondary") && json.get("secondary").getAsBoolean()
        );
    }

    private static String moveName(ResourceLocation id) {
        if (id == null) return null;
        String path = id.getPath();
        String prefix = DIRECTORY + "/";
        String suffix = ".json";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) return null;
        String value = path.substring(prefix.length(), path.length() - suffix.length());
        return value.isBlank() || value.contains("/") ? null : value;
    }
}
