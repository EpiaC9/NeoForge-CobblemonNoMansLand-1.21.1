package net.epiac9.cobblemonnml.battle.action.compat;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ActionBattleMoveEffectDataManager {
    private static final String DIRECTORY = "action_battle/move_effects";
    private static volatile Map<String, ActionBattleMoveEffectData> definitions = Map.of();

    private ActionBattleMoveEffectDataManager() {}

    public static void reload(ResourceManager resourceManager) {
        if (resourceManager == null) {
            definitions = Map.of();
            return;
        }
        Map<String, ActionBattleMoveEffectData> loaded = new HashMap<>();
        List<Map.Entry<ResourceLocation, Resource>> resources = new ArrayList<>(resourceManager.listResources(
                DIRECTORY, id -> id.getPath().endsWith(".json")
        ).entrySet());
        resources.sort(Map.Entry.comparingByKey());
        for (Map.Entry<ResourceLocation, Resource> entry : resources) {
            String moveName = moveName(entry.getKey());
            if (moveName == null || loaded.containsKey(moveName)) {
                DebugLog.log("[CobblemonNML] Rejected duplicate/invalid NML ACTION extension resource: " + entry.getKey());
                continue;
            }
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                ActionBattleMoveEffectData definition = parse(JsonParser.parseReader(reader).getAsJsonObject());
                if (definition == null || definition.typeEffects().isEmpty()) {
                    DebugLog.log("[CobblemonNML] Rejected empty/legacy ACTION move-effect definition: " + entry.getKey());
                    continue;
                }
                loaded.put(moveName, definition);
            } catch (Exception exception) {
                DebugLog.log("[CobblemonNML] Failed to load NML ACTION extension definition " + entry.getKey(), exception);
            }
        }
        definitions = Map.copyOf(loaded);
        DebugLog.log("[CobblemonNML] Loaded " + definitions.size() + " NML ACTION move extension(s).");
    }

    public static ActionBattleMoveEffectData get(String moveName) {
        return moveName == null ? null : definitions.get(moveName);
    }

    public static List<ActionBattleMoveEffectData> getAll(String moveName) {
        ActionBattleMoveEffectData value = get(moveName);
        return value == null ? List.of() : List.of(value);
    }

    public static Set<String> typeEffectRoutes(String moveName) {
        ActionBattleMoveEffectData value = get(moveName);
        return value == null ? Set.of() : value.typeEffects();
    }

    static ActionBattleMoveEffectData parse(JsonObject json) {
        if (json == null) return null;
        LinkedHashSet<String> routes = new LinkedHashSet<>();
        JsonElement single = json.get("type_effect");
        if (single != null && single.isJsonPrimitive()) routes.add(single.getAsString());
        JsonElement multiple = json.get("type_effects");
        if (multiple != null && multiple.isJsonArray()) {
            JsonArray array = multiple.getAsJsonArray();
            for (JsonElement element : array) {
                if (element == null || !element.isJsonPrimitive()) return null;
                routes.add(element.getAsString());
            }
        }
        ActionBattleMoveEffectData parsed = new ActionBattleMoveEffectData(routes);
        return parsed.typeEffects().isEmpty() ? null : parsed;
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
