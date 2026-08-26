package net.epiac9.cobblemonnml.events.quest.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class QuestNpcDataManager {
    private static final String DIRECTORY = "quest_npcs";
    private static final String PRESET_PREFIX = "easy_npc/preset/humanoid/quests_givers/";
    private static final String PRESET_SUFFIX = ".npc.nbt";
    private static Map<ResourceLocation, QuestNpcDefinition> definitions = Map.of();
    private static Map<DungeonTier, List<QuestNpcDefinition>> byTier = Map.of();

    private QuestNpcDataManager() {
    }

    public static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, QuestNpcDefinition> loaded = new HashMap<>();
        Map<DungeonTier, List<QuestNpcDefinition>> tierPools = new EnumMap<>(DungeonTier.class);
        for (DungeonTier tier : DungeonTier.values()) tierPools.put(tier, new ArrayList<>());
        if (resourceManager == null) {
            definitions = Map.of();
            byTier = freezePools(tierPools);
            return;
        }
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));
        List<Map.Entry<ResourceLocation, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        for (Map.Entry<ResourceLocation, Resource> entry : sorted) {
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                QuestNpcDefinition definition = parseDefinition(json);
                if (definition == null) {
                    DebugLog.log("[CobblemonNML] Rejected invalid Quest NPC definition: " + entry.getKey());
                    continue;
                }
                if (loaded.containsKey(definition.id())) {
                    DebugLog.log("[CobblemonNML] Rejected duplicate Quest NPC id: " + definition.id());
                    continue;
                }
                loaded.put(definition.id(), definition);
                tierPools.get(definition.tier()).add(definition);
            } catch (Exception exception) {
                DebugLog.log("[CobblemonNML] Failed to load Quest NPC definition " + entry.getKey());
                exception.printStackTrace();
            }
        }
        for (List<QuestNpcDefinition> pool : tierPools.values()) pool.sort(Comparator.comparing(definition -> definition.id().toString()));
        definitions = Map.copyOf(loaded);
        byTier = freezePools(tierPools);
        DebugLog.log("[CobblemonNML] Loaded " + definitions.size() + " Quest NPC definition(s).");
    }

    public static QuestNpcDefinition get(ResourceLocation id) {
        return id == null ? null : definitions.get(id);
    }

    public static List<QuestNpcDefinition> findForTier(DungeonTier tier) {
        if (tier == null) return List.of();
        return byTier.getOrDefault(tier, List.of());
    }

    static QuestNpcDefinition parseDefinition(JsonObject json) {
        if (json == null || !json.has("id") || !json.has("npc_preset") || !json.has("quest")) return null;
        ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
        ResourceLocation preset = ResourceLocation.tryParse(json.get("npc_preset").getAsString());
        ResourceLocation quest = ResourceLocation.tryParse(json.get("quest").getAsString());
        DungeonTier tier = inferTier(preset);
        if (id == null || preset == null || quest == null || tier == null) return null;
        return new QuestNpcDefinition(id, preset, quest, tier);
    }

    public static DungeonTier inferTier(ResourceLocation preset) {
        if (preset == null || !preset.getPath().endsWith(PRESET_SUFFIX)) return null;
        String path = preset.getPath();
        if (path.startsWith(PRESET_PREFIX + "tier_1/")) return DungeonTier.TIER_1;
        if (path.startsWith(PRESET_PREFIX + "tier_2/")) return DungeonTier.TIER_2;
        return null;
    }

    private static Map<DungeonTier, List<QuestNpcDefinition>> freezePools(Map<DungeonTier, List<QuestNpcDefinition>> pools) {
        Map<DungeonTier, List<QuestNpcDefinition>> frozen = new EnumMap<>(DungeonTier.class);
        for (Map.Entry<DungeonTier, List<QuestNpcDefinition>> entry : pools.entrySet()) frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        return Map.copyOf(frozen);
    }
}
