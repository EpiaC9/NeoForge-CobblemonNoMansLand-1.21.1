package net.epiac9.cobblemonnml.events.trainer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DungeonTrainerBattleFormats {
    private static final String SINGLES = "GEN_9_SINGLES";
    private static final String DOUBLES = "GEN_9_DOUBLES";

    private static final ResourceLocation FORMAT_FILE =
            ResourceLocation.fromNamespaceAndPath(
                    CobblemonNML.MOD_ID,
                    "trainers/battle_formats.json"
            );

    private static final Set<String> DOUBLE_TRAINERS = new HashSet<>();
    private static final Map<String, String> PAIRED_TRAINERS = new HashMap<>();
    private static Object cachedResourceManager = null;

    private DungeonTrainerBattleFormats() {}

    public static String getBattleFormat(
            ServerLevel level,
            ResourceLocation preset
    ) {
        if (level == null || preset == null) {
            return SINGLES;
        }

        ensureLoaded(level);

        String trainerName = getTrainerBaseName(preset);
        if (trainerName == null || trainerName.isBlank()) {
            return SINGLES;
        }

        return DOUBLE_TRAINERS.contains(trainerName)
                || PAIRED_TRAINERS.containsKey(trainerName)
                ? DOUBLES
                : SINGLES;
    }

    public static String getPartnerTrainerName(
            ServerLevel level,
            ResourceLocation preset
    ) {
        if (level == null || preset == null) {
            return null;
        }

        ensureLoaded(level);

        String trainerName = getTrainerBaseName(preset);
        if (trainerName == null || trainerName.isBlank()) {
            return null;
        }

        return PAIRED_TRAINERS.get(trainerName);
    }

    public static void invalidateCache() {
        DOUBLE_TRAINERS.clear();
        PAIRED_TRAINERS.clear();
        cachedResourceManager = null;
    }

    private static void ensureLoaded(ServerLevel level) {
        Object resourceManager = level.getServer().getResourceManager();

        if (cachedResourceManager == resourceManager) {
            return;
        }

        DOUBLE_TRAINERS.clear();
        PAIRED_TRAINERS.clear();
        cachedResourceManager = resourceManager;

        Optional<Resource> resource =
                level.getServer()
                        .getResourceManager()
                        .getResource(FORMAT_FILE);

        if (resource.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] No trainer battle format file found at "
                            + FORMAT_FILE
                            + ". All normal trainers will use singles."
            );
            return;
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonElement rootElement = JsonParser.parseReader(reader);

            if (!rootElement.isJsonObject()) {
                DebugLog.log(
                        "[CobblemonNML] Trainer battle format file is not a JSON object: "
                                + FORMAT_FILE
                );
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();

            JsonArray doubles =
                    root.has("doubles") && root.get("doubles").isJsonArray()
                            ? root.getAsJsonArray("doubles")
                            : new JsonArray();

            for (JsonElement entry : doubles) {
                if (!entry.isJsonPrimitive()
                        || !entry.getAsJsonPrimitive().isString()) {
                    continue;
                }

                String trainerName = normalizeName(entry.getAsString());
                if (!trainerName.isBlank()) {
                    DOUBLE_TRAINERS.add(trainerName);
                }
            }

            if (root.has("pairs") && root.get("pairs").isJsonObject()) {
                JsonObject pairs = root.getAsJsonObject("pairs");

                for (Map.Entry<String, JsonElement> entry : pairs.entrySet()) {
                    if (entry.getValue() == null
                            || !entry.getValue().isJsonPrimitive()
                            || !entry.getValue().getAsJsonPrimitive().isString()) {
                        continue;
                    }

                    String first = normalizeName(entry.getKey());
                    String second = normalizeName(entry.getValue().getAsString());

                    if (first.isBlank() || second.isBlank()) {
                        continue;
                    }

                    /*
                     * Store both directions so selecting either physical preset
                     * creates the same two-member trainer pair.
                     */
                    PAIRED_TRAINERS.put(first, second);
                    PAIRED_TRAINERS.put(second, first);

                    DOUBLE_TRAINERS.add(first);
                    DOUBLE_TRAINERS.add(second);
                }
            }

            DebugLog.log(
                    "[CobblemonNML] Loaded "
                            + DOUBLE_TRAINERS.size()
                            + " double-battle trainer name(s) and "
                            + (PAIRED_TRAINERS.size() / 2)
                            + " paired trainer definition(s)."
            );
        } catch (Exception exception) {
            DOUBLE_TRAINERS.clear();
            PAIRED_TRAINERS.clear();

            DebugLog.log(
                    "[CobblemonNML] Failed to load trainer battle format file: "
                            + FORMAT_FILE
            );
            exception.printStackTrace();
        }
    }

    private static String getTrainerBaseName(ResourceLocation preset) {
        String path = preset.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }

        int lastSlash = path.lastIndexOf('/');
        String fileName =
                lastSlash >= 0
                        ? path.substring(lastSlash + 1)
                        : path;

        String suffix = ".npc.nbt";
        if (fileName.endsWith(suffix)) {
            fileName =
                    fileName.substring(
                            0,
                            fileName.length() - suffix.length()
                    );
        }

        return normalizeName(fileName);
    }

    private static String normalizeName(String name) {
        return name == null
                ? ""
                : name.trim().toLowerCase(Locale.ROOT);
    }
}