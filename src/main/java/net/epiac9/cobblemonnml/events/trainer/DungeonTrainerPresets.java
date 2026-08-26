package net.epiac9.cobblemonnml.events.trainer;

import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DungeonTrainerPresets {
    // RESOURCE PATHS
    private static final String NAMESPACE = "cobblemonnml";
    private static final String BASE_DIRECTORY = "easy_npc/preset/humanoid/trainers/";
    private static final String NPC_FILE_SUFFIX = ".npc.nbt";
    // THEME + TIER CACHE KEY
    private record TrainerCacheKey(DungeonTheme theme, DungeonTier tier) {
    }
    // RESOURCE DISCOVERY CACHE
    private static final Map<TrainerCacheKey, List<ResourceLocation>> NORMAL_PRESET_CACHE = new HashMap<>();
    private static ResourceManager cachedResourceManager = null;

    private DungeonTrainerPresets() {
    }
    // CACHE INVALIDATION
    public static void invalidateCache() {
        NORMAL_PRESET_CACHE.clear();
        cachedResourceManager = null;
    }
    // FIND NORMAL TRAINERS
    public static List<ResourceLocation> findNormalTrainerPresets(ServerLevel level, DungeonTier tier) {
        if (level == null || tier == null) {
            return new ArrayList<>();
        }

        DungeonTheme theme = DungeonSession.getTheme();
        if (theme == null) {
            DebugLog.log("[CobblemonNML] Cannot resolve normal trainer presets: no active dungeon theme.");
            return new ArrayList<>();
        }

        ResourceManager resourceManager = getResourceManager(level);
        TrainerCacheKey cacheKey = new TrainerCacheKey(theme, tier);
        List<ResourceLocation> cached = NORMAL_PRESET_CACHE.get(cacheKey);

        if (cached == null) {
            String directory = BASE_DIRECTORY + theme.getId() + "/" + getTierFolder(tier);
            cached = List.copyOf(findNpcPresets(resourceManager, directory));
            NORMAL_PRESET_CACHE.put(cacheKey, cached);

            DebugLog.log(
                    "[CobblemonNML] Cached "
                            + cached.size()
                            + " normal trainer preset(s) for "
                            + theme.getDisplayName()
                            + " "
                            + tier.getDisplayName()
                            + " from "
                            + directory
            );
        }

        return new ArrayList<>(cached);
    }
    // RESOURCE MANAGER / CACHE LIFETIME
    private static ResourceManager getResourceManager(ServerLevel level) {
        ResourceManager resourceManager = level.getServer().getResourceManager();
        if (cachedResourceManager != resourceManager) {
            NORMAL_PRESET_CACHE.clear();
            cachedResourceManager = resourceManager;
        }
        return resourceManager;
    }
    // GENERIC NPC RESOURCE SEARCH
    private static List<ResourceLocation> findNpcPresets(ResourceManager resourceManager, String directory) {
        if (resourceManager == null || directory == null || directory.isBlank()) {
            return new ArrayList<>();
        }

        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources(
                        directory,
                        resourceLocation ->
                                resourceLocation.getNamespace().equals(NAMESPACE)
                                        && resourceLocation.getPath().endsWith(NPC_FILE_SUFFIX)
                );

        List<ResourceLocation> trainers = new ArrayList<>(resources.keySet());
        trainers.sort(Comparator.naturalOrder());
        return trainers;
    }
    // GET TIER FOLDER
    private static String getTierFolder(DungeonTier tier) {
        return switch (tier) {
            case TIER_1 -> "tier_1";
            case TIER_2 -> "tier_2";
            case TIER_3 -> "tier_3";
            case TIER_4 -> "tier_4";
        };
    }
}
