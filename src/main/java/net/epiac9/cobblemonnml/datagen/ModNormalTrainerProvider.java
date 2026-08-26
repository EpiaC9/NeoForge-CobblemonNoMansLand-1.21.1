package net.epiac9.cobblemonnml.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import net.neoforged.fml.loading.FMLPaths;

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
    // EASY NPC PRESET ROOT
    private static final Path NPC_ROOT =
            FMLPaths.GAMEDIR.get()
                    .getParent()
                    .resolve(
                            "src"
                    ).resolve(
                            "main"
                    ).resolve(
                            "resources"
                    ).resolve(
                            "data"
                    ).resolve(
                            CobblemonNML.MOD_ID
                    ).resolve(
                            "easy_npc"
                    ).resolve(
                            "preset"
                    ).resolve(
                            "humanoid"
                    ).resolve(
                            "trainers"
                    ).normalize();
    // MANUAL TRAINER DATA ROOT
    /*
     * If a trainer file already exists under src/main/resources,
     * datagen will leave it completely untouched.
     */
    private static final Path MANUAL_TRAINER_ROOT =
            FMLPaths.GAMEDIR.get()
                    .getParent()
                    .resolve(
                            "src"
                    ).resolve(
                            "main"
                    ).resolve(
                            "resources"
                    ).resolve(
                            "data"
                    ).resolve(
                            CobblemonNML.MOD_ID
                    ).resolve(
                            "trainers"
                    ).normalize();
    // OUTPUT
    private final PackOutput output;
    public ModNormalTrainerProvider(PackOutput output) {
        this.output = output;
    }
    // RUN
    @Override
    public @NotNull CompletableFuture<?> run( @NotNull CachedOutput cachedOutput ) {
        DebugLog.log( "[CobblemonNML] Normal trainer datagen scanning: " + NPC_ROOT.toAbsolutePath() );
        if (!Files.isDirectory(NPC_ROOT)) {
            DebugLog.log(
                    "[CobblemonNML] Normal trainer datagen skipped because "
                            + "the Easy NPC preset directory does not exist."
            );
            return CompletableFuture.completedFuture( null );
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        int[] counts =
                new int[]{
                        0, // discovered
                        0, // generated
                        0, // existing
                        0  // skipped / invalid
                };
        try (Stream<Path> paths = Files.walk( NPC_ROOT )) {

            paths.filter( Files::isRegularFile )
                    .filter( path -> path .getFileName() .toString() .endsWith( ".npc.nbt" ) )
                    .forEach( npcPath -> processNpc( cachedOutput, npcPath, futures, counts ) );
        } catch (IOException exception) {
            DebugLog.log(
                    "[CobblemonNML] Failed to scan normal Easy NPC trainer "
                            + "presets during trainer datagen."
            );
            exception.printStackTrace();
            return CompletableFuture.failedFuture( exception );
        }
        DebugLog.log(
                "[CobblemonNML] Normal trainer datagen complete. "
                        + "Discovered="
                        + counts[0]
                        + ", generated="
                        + counts[1]
                        + ", existing="
                        + counts[2]
                        + ", skipped="
                        + counts[3]
        );
        return CompletableFuture.allOf( futures.toArray( CompletableFuture[]::new ) );
    }
    // PROCESS NPC
    private void processNpc(
            CachedOutput cachedOutput,
            Path npcPath,
            List<CompletableFuture<?>> futures,
            int[] counts
    ) {
        Path relative = NPC_ROOT.relativize( npcPath );

        /*
         * Only this layout is supported beneath the trainers root:
         * <theme>/<tier>/<name>.npc.nbt
         * Example: bug/tier_1/bug_catcher_amy.npc.nbt
         */
        if (relative.getNameCount() != 3) {
            counts[3]++;
            return;
        }

        String theme =
                relative.getName( 0 ).toString()
                        .trim()
                        .toLowerCase( Locale.ROOT );

        String tier =
                relative.getName( 1 ).toString()
                        .trim()
                        .toLowerCase( Locale.ROOT );

        String fileName =
                relative.getName( 2 ).toString()
                        .trim();
        if (!fileName.endsWith(".npc.nbt")) {
            counts[3]++;
            return;
        }
        int teamSize = getTeamSize( tier );
        if (teamSize <= 0) {
            DebugLog.log( "[CobblemonNML] Normal trainer datagen skipped unsupported tier: " + relative );
            counts[3]++;
            return;
        }
        String npcName =
                fileName.substring( 0, fileName.length() - ".npc.nbt".length() ).trim()
                        .toLowerCase( Locale.ROOT );
        if (theme.isBlank() || npcName.isBlank()) {
            counts[3]++;
            return;
        }
        counts[0]++;
        // OUTPUT PATHS
        Path relativeTrainerPath = Path.of( theme, tier, npcName, "team_1.json" );
        Path manualTrainerPath = MANUAL_TRAINER_ROOT.resolve( relativeTrainerPath ).normalize();
        Path generatedTrainerPath =
                output.getOutputFolder()
                        .resolve(
                                "data"
                        ).resolve(
                                CobblemonNML.MOD_ID
                        ).resolve(
                                "trainers"
                        ).resolve(
                                relativeTrainerPath
                        ).normalize();
        // NEVER OVERWRITE EXISTING TRAINER DATA
        /*
         * A hand-authored file under src/main/resources wins.
         * We also leave an already-generated team_1.json alone so adding another NPC does not rewrite older trainer files.
         */
        if (Files.isRegularFile(manualTrainerPath) || Files.isRegularFile(generatedTrainerPath)) {
            counts[2]++;
            DebugLog.log( "[CobblemonNML] Normal trainer datagen kept existing file: " + relativeTrainerPath );
            return;
        }
        // CREATE TRAINER JSON
        JsonObject root = createTrainerJson( npcName, tier, teamSize );
        futures.add( DataProvider.saveStable( cachedOutput, root, generatedTrainerPath ) );
        counts[1]++;
        DebugLog.log(
                "[CobblemonNML] Normal trainer datagen created: "
                        + relativeTrainerPath
                        + " with "
                        + teamSize
                        + " Pokemon."
        );
    }
    // TEAM SIZE
    private static int getTeamSize(String tier) {
        return switch (tier) {
            case "tier_1" -> 3;
            case "tier_2" -> 4;
            case "tier_3" -> 5;
            case "tier_4" -> 6;
            default -> -1;
        };
    }
    // CREATE TRAINER JSON
    private static JsonObject createTrainerJson( String npcName, String tier, int teamSize ) {
        JsonObject root = new JsonObject();
        root.addProperty( "name", makeTrainerDisplayName( npcName ) );
        // AI
        JsonObject ai = new JsonObject();
        ai.addProperty( "type", "rct" );
        ai.add( "data", new JsonObject() );
        root.add( "ai", ai );
        // BAG
        root.add( "bag", new JsonArray() );
        // TEAM
        JsonArray team = new JsonArray();
        for (int slot = 0; slot < teamSize; slot++) {
            team.add( createPlaceholderPokemon( tier, slot ) );
        }
        root.add( "team", team );
        // BATTLE THEME
        root.addProperty( "battleTheme", "" );
        return root;
    }
    // PLACEHOLDER POKEMON
    /*
     * These values only need to form a valid TBCS trainer template.
     *
     * CobblemonNML replaces the template Pokemon with the randomized
     * dungeon team at runtime.
     */
    private static JsonObject createPlaceholderPokemon( String tier, int slot ) {
        JsonObject pokemon = new JsonObject();
        pokemon.addProperty( "species", getPlaceholderSpecies( tier, slot ) );
        pokemon.addProperty( "nickname", "" );
        pokemon.addProperty( "gender", "MALE" );
        pokemon.addProperty( "level", 30 );
        pokemon.addProperty( "nature", "cobblemon:jolly" );
        pokemon.addProperty( "ability", "static" );
        // MOVESET
        JsonArray moveset = new JsonArray();
        moveset.add( "thunderbolt" );
        moveset.add( "quickattack" );
        moveset.add( "irontail" );
        moveset.add( "voltswitch" );
        pokemon.add( "moveset", moveset );
        // IVS
        JsonObject ivs = new JsonObject();
        ivs.addProperty( "hp", 31 );
        ivs.addProperty( "atk", 31 );
        ivs.addProperty( "def", 31 );
        ivs.addProperty( "spa", 31 );
        ivs.addProperty( "spd", 31 );
        ivs.addProperty( "spe", 31 );
        pokemon.add( "ivs", ivs );
        // EVS
        JsonObject evs = new JsonObject();
        evs.addProperty( "hp", 0 );
        evs.addProperty( "atk", 252 );
        evs.addProperty( "def", 0 );
        evs.addProperty( "spa", 0 );
        evs.addProperty( "spd", 4 );
        evs.addProperty( "spe", 252 );
        pokemon.add( "evs", evs );
        pokemon.addProperty( "shiny", false );
        pokemon.addProperty( "heldItem", "" );
        pokemon.add( "aspects", new JsonArray() );
        // GIMMICKS
        JsonObject gimmicks = new JsonObject();
        gimmicks.add( "tera", null );
        gimmicks.addProperty( "dynamax", false );
        gimmicks.addProperty( "gmax", false );
        pokemon.add( "gimmicks", gimmicks );
        return pokemon;
    }
    // PLACEHOLDER SPECIES
    private static String getPlaceholderSpecies( String tier, int slot ) {
        /*
         * Mirrors the existing working templates:
         * Tier 1 = Pichu
         * Tier 2 = Bulbasaur
         * Tier 3 = Squirtle
         * Tier 4 = Charmander
         * The runtime randomizer replaces these anyway.
         */
        return switch (tier) {
            case "tier_1" -> "cobblemon:pichu";
            case "tier_2" -> "cobblemon:bulbasaur";
            case "tier_3" -> "cobblemon:squirtle";
            case "tier_4" -> "cobblemon:charmander";
            default -> "cobblemon:pichu";
        };
    }
    // DISPLAY NAME
    private static String makeTrainerDisplayName(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return "Dungeon Trainer";
        }
        String[] parts = npcName.split( "_" );
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append( " " );
            }
            result.append( Character.toUpperCase( part.charAt( 0 ) ) );
            if (part.length() > 1) {
                result.append( part.substring( 1 ) );
            }
        }
        if (result.isEmpty()) {
            return "Dungeon Trainer";
        }
        return result.toString();
    }
    // NAME
    @Override
    public @NotNull String getName() {
        return "CobblemonNML Normal Trainer Templates";
    }
}
