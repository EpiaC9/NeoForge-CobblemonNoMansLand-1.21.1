package net.epiac9.cobblemonnml.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class ModNormalTrainerProvider implements DataProvider {
    private static final String NPC_SUFFIX = ".npc.nbt";

    private final PackOutput output;
    private final Path npcRoot;
    private final Path manualTrainerRoot;

    public ModNormalTrainerProvider(PackOutput output) {
        this.output = output;

        Path generatedResourcesRoot =
                output.getOutputFolder()
                        .toAbsolutePath()
                        .normalize();

        Path srcRoot =
                generatedResourcesRoot
                        .getParent()
                        .getParent();

        Path mainResourcesRoot =
                srcRoot
                        .resolve("main")
                        .resolve("resources")
                        .normalize();

        this.npcRoot =
                mainResourcesRoot
                        .resolve("data")
                        .resolve(CobblemonNML.MOD_ID)
                        .resolve("easy_npc")
                        .resolve("preset")
                        .resolve("humanoid")
                        .resolve("trainers")
                        .normalize();

        this.manualTrainerRoot =
                mainResourcesRoot
                        .resolve("data")
                        .resolve(CobblemonNML.MOD_ID)
                        .resolve("trainers")
                        .normalize();
    }

    @Override
    public @NotNull CompletableFuture<?> run(
            @NotNull CachedOutput cachedOutput
    ) {
        System.out.println(
                "[CobblemonNML] Normal trainer datagen scanning: "
                        + npcRoot
        );

        DebugLog.log(
                "[CobblemonNML] Normal trainer datagen scanning: "
                        + npcRoot
        );

        if (!Files.isDirectory(npcRoot)) {
            System.out.println(
                    "[CobblemonNML] Normal trainer datagen skipped because "
                            + "the trainer preset directory does not exist: "
                            + npcRoot
            );

            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen skipped because "
                            + "the trainer preset directory does not exist: "
                            + npcRoot
            );

            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();

        int[] counts = new int[]{
                0,
                0,
                0,
                0
        };

        try (Stream<Path> paths = Files.walk(npcRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .endsWith(NPC_SUFFIX)
                    )
                    .forEach(path ->
                            processNpc(
                                    cachedOutput,
                                    path,
                                    futures,
                                    counts
                            )
                    );

        } catch (IOException exception) {
            DebugLog.log(
                    "[CobblemonNML] Failed to scan normal trainer "
                            + "presets during datagen."
            );

            exception.printStackTrace();

            return CompletableFuture.failedFuture(exception);
        }

        String summary =
                "[CobblemonNML] Normal trainer datagen complete. "
                        + "Discovered="
                        + counts[0]
                        + ", generated="
                        + counts[1]
                        + ", existing="
                        + counts[2]
                        + ", skipped="
                        + counts[3];

        System.out.println(summary);
        DebugLog.log(summary);

        return CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        );
    }

    private void processNpc(
            CachedOutput cachedOutput,
            Path npcPath,
            List<CompletableFuture<?>> futures,
            int[] counts
    ) {
        Path relative = npcRoot.relativize(npcPath);

        /*
         * Required layout:
         *
         * trainers/<theme>/<tier>/<name>.npc.nbt
         *
         * Because npcRoot already points at "trainers",
         * the relative path must be:
         *
         * <theme>/<tier>/<name>.npc.nbt
         *
         * Examples:
         * water/tier_1/fisherman.npc.nbt
         * normal/tier_1/athlete.npc.nbt
         * fighting/tier_1/athlete.npc.nbt
         *
         * A trainer may intentionally exist in multiple theme folders.
         * Runtime code combines those folders into that trainer's Pokemon type pool.
         */
        if (relative.getNameCount() != 3) {
            counts[3]++;

            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen skipped "
                            + "unexpected path: "
                            + relative
            );

            return;
        }

        String theme =
                relative.getName(0)
                        .toString()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String tier =
                relative.getName(1)
                        .toString()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String fileName =
                relative.getName(2)
                        .toString()
                        .trim();

        if (!fileName.endsWith(NPC_SUFFIX)) {
            counts[3]++;
            return;
        }

        int teamSize = getTeamSize(tier);

        if (teamSize <= 0) {
            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen skipped "
                            + "unsupported tier: "
                            + relative
            );

            counts[3]++;
            return;
        }

        String npcName =
                fileName.substring(
                                0,
                                fileName.length() - NPC_SUFFIX.length()
                        )
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (!isPokemonType(theme) || npcName.isBlank()) {
            counts[3]++;

            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen skipped invalid theme: "
                            + relative
            );

            return;
        }

        counts[0]++;

        Path relativeTrainerPath =
                Path.of(
                        theme,
                        tier,
                        npcName,
                        "team_1.json"
                );

        Path manualTrainerPath =
                manualTrainerRoot
                        .resolve(relativeTrainerPath)
                        .normalize();

        Path generatedTrainerPath =
                output.getOutputFolder()
                        .resolve("data")
                        .resolve(CobblemonNML.MOD_ID)
                        .resolve("trainers")
                        .resolve(relativeTrainerPath)
                        .normalize();

        if (Files.isRegularFile(manualTrainerPath)) {
            counts[2]++;

            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen kept manual file: "
                            + relativeTrainerPath
            );

            return;
        }

        JsonObject root =
                createTrainerJson(
                        npcName,
                        tier,
                        teamSize
                );

        futures.add(
                DataProvider.saveStable(
                        cachedOutput,
                        root,
                        generatedTrainerPath
                )
        );

        counts[1]++;

        DebugLog.log(
                "[CobblemonNML] Normal trainer datagen created: "
                        + relativeTrainerPath
                        + " with "
                        + teamSize
                        + " Pokemon."
        );
    }

    private static boolean isPokemonType(String type) {
        return switch (type) {
            case "normal", "fighting", "flying", "poison", "ground", "rock",
                 "bug", "ghost", "steel", "fire", "water", "grass",
                 "electric", "psychic", "ice", "dragon", "dark", "fairy" -> true;
            default -> false;
        };
    }

    private static int getTeamSize(String tier) {
        return switch (tier) {
            case "tier_1" -> 3;
            case "tier_2" -> 4;
            case "tier_3" -> 5;
            case "tier_4" -> 6;
            default -> -1;
        };
    }

    private static JsonObject createTrainerJson(
            String npcName,
            String tier,
            int teamSize
    ) {
        JsonObject root = new JsonObject();

        root.addProperty(
                "name",
                makeTrainerDisplayName(npcName)
        );

        JsonObject ai = new JsonObject();
        ai.addProperty("type", "rct");
        ai.add("data", new JsonObject());

        root.add("ai", ai);
        root.add("bag", new JsonArray());

        JsonArray team = new JsonArray();

        for (int slot = 0; slot < teamSize; slot++) {
            team.add(
                    createPlaceholderPokemon(
                            tier,
                            slot
                    )
            );
        }

        root.add("team", team);
        root.addProperty("battleTheme", "");

        return root;
    }

    private static JsonObject createPlaceholderPokemon(
            String tier,
            int slot
    ) {
        JsonObject pokemon = new JsonObject();

        pokemon.addProperty(
                "species",
                getPlaceholderSpecies(
                        tier,
                        slot
                )
        );

        pokemon.addProperty("gender", "MALE");
        pokemon.addProperty("level", 30);
        pokemon.addProperty("nature", "cobblemon:jolly");
        pokemon.addProperty("ability", "static");

        JsonArray moveset = new JsonArray();
        moveset.add("thunderbolt");
        moveset.add("quickattack");
        moveset.add("irontail");
        moveset.add("voltswitch");

        pokemon.add("moveset", moveset);

        JsonObject ivs = new JsonObject();
        ivs.addProperty("hp", 31);
        ivs.addProperty("atk", 31);
        ivs.addProperty("def", 31);
        ivs.addProperty("spa", 31);
        ivs.addProperty("spd", 31);
        ivs.addProperty("spe", 31);

        pokemon.add("ivs", ivs);

        JsonObject evs = new JsonObject();
        evs.addProperty("hp", 0);
        evs.addProperty("atk", 252);
        evs.addProperty("def", 0);
        evs.addProperty("spa", 0);
        evs.addProperty("spd", 4);
        evs.addProperty("spe", 252);

        pokemon.add("evs", evs);

        pokemon.addProperty("shiny", false);
        pokemon.addProperty("heldItem", "");
        pokemon.add("aspects", new JsonArray());

        JsonObject gimmicks = new JsonObject();
        gimmicks.add("tera", null);
        gimmicks.addProperty("dynamax", false);
        gimmicks.addProperty("gmax", false);

        pokemon.add("gimmicks", gimmicks);

        return pokemon;
    }

    private static String getPlaceholderSpecies(
            String tier,
            int slot
    ) {
        return switch (tier) {
            case "tier_1" -> "cobblemon:pichu";
            case "tier_2" -> "cobblemon:bulbasaur";
            case "tier_3" -> "cobblemon:squirtle";
            case "tier_4" ->
                    slot == 0
                            ? "cobblemon:charmander"
                            : "cobblemon:cyndaquil";
            default -> "cobblemon:pichu";
        };
    }

    private static String makeTrainerDisplayName(
            String npcName
    ) {
        if (npcName == null || npcName.isBlank()) {
            return "Dungeon Trainer";
        }

        String[] parts = npcName.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                result.append(
                        part.substring(1)
                );
            }
        }

        return result.isEmpty()
                ? "Dungeon Trainer"
                : result.toString();
    }

    @Override
    public @NotNull String getName() {
        return "CobblemonNML Normal Trainer Templates";
    }
}