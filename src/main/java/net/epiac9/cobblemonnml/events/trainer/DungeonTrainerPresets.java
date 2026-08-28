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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DungeonTrainerPresets {
    private static final String NAMESPACE = "cobblemonnml";
    private static final String BASE_DIRECTORY = "easy_npc/preset/humanoid/trainers";
    private static final String BASE_PREFIX = BASE_DIRECTORY + "/";
    private static final String NPC_FILE_SUFFIX = ".npc.nbt";

    private record TrainerCacheKey(DungeonTheme theme, DungeonTier tier) {}

    private static final Map<TrainerCacheKey, List<ResourceLocation>> NORMAL_PRESET_CACHE = new HashMap<>();
    private static ResourceManager cachedResourceManager = null;

    private DungeonTrainerPresets() {}

    public static void invalidateCache() {
        NORMAL_PRESET_CACHE.clear();
        cachedResourceManager = null;
    }

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
            cached = List.copyOf(findNpcPresets(resourceManager, theme.getId(), getTierFolder(tier)));
            NORMAL_PRESET_CACHE.put(cacheKey, cached);

            DebugLog.log(
                    "[CobblemonNML] Cached "
                            + cached.size()
                            + " normal trainer preset(s) for "
                            + theme.getDisplayName()
                            + " "
                            + tier.getDisplayName()
            );
        }

        return new ArrayList<>(cached);
    }

    public static List<String> getTrainerTypes(
            ServerLevel level,
            ResourceLocation preset,
            DungeonTier tier
    ) {
        if (level == null || preset == null || tier == null) {
            return List.of();
        }

        String trainerName = getTrainerBaseName(preset);
        if (trainerName == null || trainerName.isBlank()) {
            return List.of();
        }

        String tierFolder = getTierFolder(tier);
        ResourceManager resourceManager = getResourceManager(level);

        Map<ResourceLocation, Resource> resources =
                resourceManager.listResources(
                        BASE_DIRECTORY,
                        resourceLocation ->
                                resourceLocation.getNamespace().equals(NAMESPACE)
                                        && resourceLocation.getPath().endsWith(NPC_FILE_SUFFIX)
                );

        Set<String> types = new LinkedHashSet<>();

        for (ResourceLocation resourceLocation : resources.keySet()) {
            String path = resourceLocation.getPath();
            if (!path.startsWith(BASE_PREFIX)) {
                continue;
            }

            String relativePath = path.substring(BASE_PREFIX.length());
            String[] parts = relativePath.split("/");
            if (parts.length != 3) {
                continue;
            }

            String themeId = parts[0].trim().toLowerCase(Locale.ROOT);
            String resourceTier = parts[1].trim().toLowerCase(Locale.ROOT);
            String resourceTrainerName = stripNpcSuffix(parts[2]).trim().toLowerCase(Locale.ROOT);

            if (!resourceTier.equals(tierFolder) || !resourceTrainerName.equals(trainerName)) {
                continue;
            }

            if (isPokemonType(themeId)) {
                types.add(themeId);
            }
        }

        List<String> result = new ArrayList<>(types);
        result.sort(String::compareTo);

        DebugLog.log(
                "[CobblemonNML] Trainer type pool for "
                        + trainerName
                        + " "
                        + tier.getDisplayName()
                        + ": "
                        + result
        );

        return List.copyOf(result);
    }

    public static String getTrainerThemeId(ResourceLocation preset) {
        if (preset == null) {
            return null;
        }

        String path = preset.getPath();
        if (!path.startsWith(BASE_PREFIX)) {
            return null;
        }

        String relativePath = path.substring(BASE_PREFIX.length());
        String[] parts = relativePath.split("/");
        if (parts.length != 3) {
            return null;
        }

        String themeId = parts[0].trim().toLowerCase(Locale.ROOT);
        return isPokemonType(themeId) ? themeId : null;
    }

    private static ResourceManager getResourceManager(ServerLevel level) {
        ResourceManager resourceManager = level.getServer().getResourceManager();
        if (cachedResourceManager != resourceManager) {
            NORMAL_PRESET_CACHE.clear();
            cachedResourceManager = resourceManager;
        }
        return resourceManager;
    }

    private static List<ResourceLocation> findNpcPresets(
            ResourceManager resourceManager,
            String activeTheme,
            String tierFolder
    ) {
        if (resourceManager == null
                || activeTheme == null
                || activeTheme.isBlank()
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

            String relativePath = path.substring(BASE_PREFIX.length());
            String[] parts = relativePath.split("/");
            if (parts.length != 3) {
                continue;
            }

            String resourceTheme = parts[0];
            String resourceTier = parts[1];

            if (!resourceTheme.equalsIgnoreCase(activeTheme)
                    || !resourceTier.equals(tierFolder)) {
                continue;
            }

            trainers.add(resourceLocation);
        }

        trainers.sort(Comparator.naturalOrder());
        return trainers;
    }

    private static String getTrainerBaseName(ResourceLocation preset) {
        if (preset == null) {
            return null;
        }

        String path = preset.getPath();
        if (!path.startsWith(BASE_PREFIX)) {
            return null;
        }

        String relativePath = path.substring(BASE_PREFIX.length());
        String[] parts = relativePath.split("/");
        if (parts.length != 3) {
            return null;
        }

        return stripNpcSuffix(parts[2]).trim().toLowerCase(Locale.ROOT);
    }

    private static String stripNpcSuffix(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.endsWith(NPC_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - NPC_FILE_SUFFIX.length())
                : fileName;
    }

    private static boolean isPokemonType(String type) {
        return switch (type) {
            case "normal", "fighting", "flying", "poison", "ground", "rock",
                 "bug", "ghost", "steel", "fire", "water", "grass",
                 "electric", "psychic", "ice", "dragon", "dark", "fairy" -> true;
            default -> false;
        };
    }

    private static String getTierFolder(DungeonTier tier) {
        return switch (tier) {
            case TIER_1 -> "tier_1";
            case TIER_2 -> "tier_2";
            case TIER_3 -> "tier_3";
            case TIER_4 -> "tier_4";
        };
    }
}
