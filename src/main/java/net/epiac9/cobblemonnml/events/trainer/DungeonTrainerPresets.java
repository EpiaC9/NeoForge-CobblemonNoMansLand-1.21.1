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
    private static final String BASE_DIRECTORY = "easy_npc/preset/humanoid/trainers";
    private static final String BASE_PREFIX = BASE_DIRECTORY + "/";
    private static final String NPC_FILE_SUFFIX = ".npc.nbt";
    // ACTIVE DUNGEON TYPE + TIER CACHE KEY
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
            cached = List.copyOf(
                    findNpcPresets(
                            resourceManager,
                            theme.getId(),
                            getTierFolder(tier)
                    )
            );

            NORMAL_PRESET_CACHE.put(cacheKey, cached);

            DebugLog.log(
                    "[CobblemonNML] Cached "
                            + cached.size()
                            + " normal trainer preset(s) compatible with "
                            + theme.getDisplayName()
                            + " "
                            + tier.getDisplayName()
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
    private static List<ResourceLocation> findNpcPresets(
            ResourceManager resourceManager,
            String activeType,
            String tierFolder
    ) {
        if (resourceManager == null
                || activeType == null
                || activeType.isBlank()
                || tierFolder == null
                || tierFolder.isBlank()) {
            return new ArrayList<>();
        }

        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources(
                        BASE_DIRECTORY,
                        resourceLocation ->
                                resourceLocation.getNamespace().equals(NAMESPACE)
                                        && resourceLocation.getPath().endsWith(NPC_FILE_SUFFIX)
                );

        List<ResourceLocation> trainers = new ArrayList<>();

        for (ResourceLocation resourceLocation : resources.keySet()) {
            String path = resourceLocation.getPath();

            if (!path.startsWith(BASE_PREFIX)) {
                continue;
            }

            String relativePath =
                    path.substring(BASE_PREFIX.length());

            String[] parts = relativePath.split("/");

            if (parts.length != 3) {
                continue;
            }

            String typePool = parts[0];
            String resourceTier = parts[1];

            if (!resourceTier.equals(tierFolder)) {
                continue;
            }

            if (!typePoolContains(typePool, activeType)) {
                continue;
            }

            trainers.add(resourceLocation);
        }

        trainers.sort(Comparator.naturalOrder());
        return trainers;
    }

    private static boolean typePoolContains(
            String typePool,
            String wantedType
    ) {
        if (typePool == null
                || typePool.isBlank()
                || wantedType == null
                || wantedType.isBlank()) {
            return false;
        }

        String[] types = typePool.split("_");

        if (types.length < 1 || types.length > 2) {
            return false;
        }

        for (String type : types) {
            if (type.equalsIgnoreCase(wantedType)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getTrainerTypes(
            ResourceLocation preset
    ) {
        if (preset == null) {
            return List.of();
        }

        String path = preset.getPath();

        if (!path.startsWith(BASE_PREFIX)) {
            return List.of();
        }

        String relativePath =
                path.substring(BASE_PREFIX.length());

        String[] parts = relativePath.split("/");

        if (parts.length != 3) {
            return List.of();
        }

        String[] types = parts[0].split("_");

        if (types.length < 1 || types.length > 2) {
            return List.of();
        }

        List<String> result = new ArrayList<>();

        for (String type : types) {
            if (type != null && !type.isBlank()) {
                result.add(type.toLowerCase());
            }
        }

        return List.copyOf(result);
    }

    public static String getTrainerTypePoolId(
            ResourceLocation preset
    ) {
        if (preset == null) {
            return null;
        }

        String path = preset.getPath();

        if (!path.startsWith(BASE_PREFIX)) {
            return null;
        }

        String relativePath =
                path.substring(BASE_PREFIX.length());

        String[] parts = relativePath.split("/");

        if (parts.length != 3) {
            return null;
        }

        String typePool = parts[0];

        return typePool.isBlank()
                ? null
                : typePool.toLowerCase();
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
