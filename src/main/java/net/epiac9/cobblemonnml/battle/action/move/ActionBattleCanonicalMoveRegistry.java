package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared production cache for Cobblemon's canonical Showdown move registry.
 * Audit code and runtime descriptor construction use this same source.
 */
public final class ActionBattleCanonicalMoveRegistry {
    private static volatile Map<String, ActionBattleCanonicalMoveMetadata> metadataById = Map.of();
    private static volatile Map<String, JsonObject> rawById = Map.of();
    private static volatile boolean loaded;

    private ActionBattleCanonicalMoveRegistry() {}

    public static ActionBattleCanonicalMoveMetadata lookup(String moveId) {
        String id = ActionBattleMoveMetadataRules.canonicalMoveId(moveId);
        if (id.isEmpty()) return ActionBattleCanonicalMoveMetadata.empty();
        ensureLoaded();
        return metadataById.getOrDefault(id, ActionBattleCanonicalMoveMetadata.empty());
    }

    public static Map<String, ActionBattleCanonicalMoveMetadata> allMetadata() {
        ensureLoaded();
        return metadataById;
    }

    /**
     * Audit/debug access to a defensive snapshot of the canonical raw entries.
     * Production battle logic should use {@link #lookup(String)} and typed metadata helpers.
     */
    public static Map<String, JsonObject> rawSnapshot() {
        ensureLoaded();
        LinkedHashMap<String, JsonObject> copy = new LinkedHashMap<>();
        rawById.forEach((id, json) -> copy.put(id, json.deepCopy()));
        return Map.copyOf(copy);
    }

    public static synchronized void refresh() {
        loadRegistry();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (ActionBattleCanonicalMoveRegistry.class) {
            if (!loaded) loadRegistry();
        }
    }

    private static void loadRegistry() {
        LinkedHashMap<String, ActionBattleCanonicalMoveMetadata> parsed = new LinkedHashMap<>();
        LinkedHashMap<String, JsonObject> raw = new LinkedHashMap<>();
        try {
            JsonArray data = ShowdownService.Companion.getService().getRegistryData("move");
            if (data != null) {
                for (JsonElement element : data) {
                    if (element == null || !element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    ActionBattleCanonicalMoveMetadata metadata = ActionBattleCanonicalMoveMetadata.fromShowdown(object);
                    if (metadata.id().isEmpty()) continue;
                    parsed.put(metadata.id(), metadata);
                    raw.put(metadata.id(), object.deepCopy());
                }
            }
        } catch (RuntimeException ignored) {
            // Missing registry data is a safe fallback state. Descriptor construction
            // still works from Move/MoveTemplate fields with empty canonical enrichment.
        }
        metadataById = Map.copyOf(parsed);
        rawById = Map.copyOf(raw);
        loaded = !parsed.isEmpty();
    }
}
