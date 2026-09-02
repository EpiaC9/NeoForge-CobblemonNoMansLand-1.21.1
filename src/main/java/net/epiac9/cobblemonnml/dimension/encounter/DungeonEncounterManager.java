package net.epiac9.cobblemonnml.dimension.encounter;

import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.labels.CobblemonPokemonLabels;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Species;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.level.DungeonPokemonLevelManager;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.preset.PresetType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;

import net.epiac9.cobblemonnml.events.quest.npc.QuestNpcSpawnManager;
import net.epiac9.cobblemonnml.events.raid.DungeonRaidManager;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerDuplicateGuard;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerPresets;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerTracker;

import net.epiac9.cobblemonnml.events.trial.DungeonTrialSpawnerManager;
import net.epiac9.cobblemonnml.events.vault.DungeonVaultManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonEncounterManager {
    // TRAINER DUPLICATE SETTINGS
    /*
     * If a tracked trainer already exists this close to the requested marker spawn position, assume the marker was
     * processed twice and do not create another trainer.
     */
    private static final double EXISTING_TRAINER_RADIUS = 1.75D;
    // ENCOUNTER RETRY SETTINGS
    private static final int MAX_ENCOUNTER_RETRIES = 2;
    private static final long RETRY_DELAY_TICKS = 20L;
    private static final List<PendingEncounterRetry> PENDING_RETRIES = new ArrayList<>();
    private static final List<ResourceLocation> TRAINER_HELD_ITEMS =
            List.of(
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "ability_shield" ),
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "air_balloon" ),
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "assault_vest" ),
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "black_belt" ),
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "black_glasses" ),
                    ResourceLocation.fromNamespaceAndPath( "cobblemon", "black_sludge" )
            );

    private record PendingEncounterRetry(
            ServerLevel level,
            BlockPos markerPos,
            String marker,
            DungeonEncounterContext context,
            int attempt,
            long executeAtGameTime
    ) {
    }
    // HANDLE MARKER
    public static void handleMarker( ServerLevel level, BlockPos markerPos, String marker ) {
        handleMarker( level, markerPos, marker, DungeonEncounterContext.normalRoom() );
    }

    public static void handleMarker(
            ServerLevel level,
            BlockPos markerPos,
            String marker,
            DungeonEncounterContext context
    ) {
        handleMarkerInternal( level, markerPos, marker, true, normalizeContext( context ) );
    }

    /**
     * Attempts one encounter marker immediately without scheduling the
     * legacy same-type retry. DungeonGenerationQueue uses this entry point
     * so a failed normal-room encounter can fall back to another room type
     * without the original failed type spawning later as a duplicate.
     *
     * @return true only when the requested encounter/block was created.
     */
    public static boolean tryHandleMarker( ServerLevel level, BlockPos markerPos, String marker ) {
        return tryHandleMarker( level, markerPos, marker, DungeonEncounterContext.normalRoom() );
    }

    public static boolean tryHandleMarker(
            ServerLevel level,
            BlockPos markerPos,
            String marker,
            DungeonEncounterContext context
    ) {
        return handleMarkerInternal( level, markerPos, marker, false, normalizeContext( context ) );
    }

    private static DungeonEncounterContext normalizeContext( DungeonEncounterContext context ) {
        return context != null ? context : DungeonEncounterContext.normalRoom();
    }

    private static boolean handleMarkerInternal(
            ServerLevel level,
            BlockPos markerPos,
            String marker,
            boolean scheduleRetryOnFailure,
            DungeonEncounterContext context
    ) {
        if (level == null || markerPos == null || marker == null || marker.isBlank()) {
            return false;
        }

        String normalizedMarker =
                marker.trim()
                        .toLowerCase();

        DebugLog.log( "Dungeon marker detected: " + normalizedMarker + " at " + markerPos );

        switch (normalizedMarker) {
            case "trainer" -> {
                boolean specialRoom = context != null && context.fromSpecialRoom();
                boolean spawned;

                if (specialRoom) {
                    DungeonTier activeTier = DungeonSession.getTier();
                    DebugLog.log(
                            "[CobblemonNML] Routing special-room trainer marker to Quest NPC system at "
                                    + markerPos
                    );
                    spawned = QuestNpcSpawnManager.spawn( level, markerPos, activeTier );
                } else {
                    spawned = spawnRandomTrainer( level, markerPos );
                }

                if (!spawned) {
                    DebugLog.log(
                            "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                                    + (specialRoom ? "Quest NPC" : "Dungeon trainer")
                                    + " failed to spawn at "
                                    + markerPos
                    );
                    if (scheduleRetryOnFailure) {
                        scheduleRetry( level, markerPos, normalizedMarker, context, 1 );
                    }
                }
                return spawned;
            }

            case "alpha" -> {
                handleAlpha( level, markerPos );
                return true;
            }

            case "raid" -> {
                boolean spawned = handleRaid( level, markerPos );
                if (!spawned) {
                    DebugLog.log(
                            "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                                    + "Dungeon raid failed to spawn at "
                                    + markerPos
                    );
                    if (scheduleRetryOnFailure) {
                        scheduleRetry( level, markerPos, normalizedMarker, context, 1 );
                    }
                }
                return spawned;
            }

            case "trial_spawner" -> {
                return handleTrialSpawner( level, markerPos );
            }

            case "elite_spawner" -> {
                return DungeonTrialSpawnerManager.spawn(
                        level,
                        markerPos,
                        DungeonTrialSpawnerManager.SpawnerType.ELITE
                );
            }

            case "boss_spawner" -> {
                return DungeonTrialSpawnerManager.spawn(
                        level,
                        markerPos,
                        DungeonTrialSpawnerManager.SpawnerType.BOSS
                );
            }

            case "vault" -> {
                boolean spawned = DungeonVaultManager.spawn(
                        level,
                        markerPos,
                        DungeonVaultManager.VaultType.NORMAL
                );
                if (!spawned) {
                    DebugLog.log( "Failed to create normal dungeon vault at " + markerPos );
                }
                return spawned;
            }

            case "ominous_vault" -> {
                boolean spawned = DungeonVaultManager.spawn(
                        level,
                        markerPos,
                        DungeonVaultManager.VaultType.OMINOUS
                );
                if (!spawned) {
                    DebugLog.log( "Failed to create ominous dungeon vault at " + markerPos );
                }
                return spawned;
            }
        }

        DebugLog.log( "Unknown dungeon marker: " + normalizedMarker + " at " + markerPos );
        return false;
    }
    // RANDOM TRAINER
    private static boolean spawnRandomTrainer( ServerLevel level, BlockPos markerPos ) {
        DungeonTier activeTier = DungeonSession.getTier();
        if (activeTier == null) {
            DebugLog.log( "Cannot spawn trainer: " + "no active dungeon tier." );
            return false;
        }
        // NORMAL TRAINERS ONLY
        List<ResourceLocation> trainers = DungeonTrainerPresets.findNormalTrainerPresets( level, activeTier );

        if (trainers.isEmpty()) {
            DebugLog.log(
                    "Cannot spawn trainer: no normal presets found for "
                            + activeTier.getDisplayName()
            );
            return false;
        }
        // CHOOSE ONE NORMAL TRAINER
        int index = level.getRandom().nextInt( trainers.size() );
        ResourceLocation selectedPreset = trainers.get( index );
        DebugLog.log(
                "[CobblemonNML] Selected normal trainer "
                        + (index + 1)
                        + "/"
                        + trainers.size()
                        + " for "
                        + activeTier.getDisplayName()
                        + ": "
                        + selectedPreset
        );
        // SPAWN IT
        return spawnTrainer( level, markerPos, selectedPreset );
    }
    // SPAWN TRAINER
    private static boolean spawnTrainer( ServerLevel level, BlockPos markerPos, ResourceLocation preset ) {
        // SPAWN POSITION
        BlockPos spawnPos = markerPos.above();
        Vec3 position = new Vec3( spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D );
        // PRE-SPAWN DUPLICATE FAILSAFE
        /*
         * If marker processing somehow fires twice, don't create another trainer on top of an already tracked one.
         */
        UUID existingTrainer = findTrackedTrainerNear( level, position );
        if (existingTrainer != null) {
            DebugLog.log( "[CobblemonNML] Skipping duplicate trainer spawn." );
            DebugLog.log( "[CobblemonNML] Marker: " + markerPos );
            DebugLog.log( "[CobblemonNML] Existing trainer UUID: " + existingTrainer );
            return true;
        }
        // UNIQUE NPC UUID
        UUID npcUUID = UUID.randomUUID();
        DebugLog.log( "Attempting to spawn trainer " + preset + " at " + position );
        // SPAWN BUNDLED EASY NPC PRESET
        Optional<EasyNPC<?>> spawned =
                EasyNPCEntityHandler
                        .spawnFromPreset( PresetType.DATA, preset, level, position, npcUUID, null );
        // RESULT
        if (spawned.isEmpty()) {
            DebugLog.log( "FAILED to spawn trainer from preset " + preset );
            return false;
        }
        EasyNPC<?> npc = spawned.get();
        // ATTACH RANDOM TBCS TRAINER TEAM
        String runtimeTrainerId = attachTrainerTeam( level, npc, preset );
        if (runtimeTrainerId == null) {
            DebugLog.log(
                    "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                            + "Trainer NPC spawned, but TBCS team attachment failed."
            );
            DebugLog.log( "[CobblemonNML] Removing incomplete trainer NPC: " + npc.getEntityUUID() );
            npc.getEntity().discard();
            return false;
        }
        // TRACK THIS DUNGEON TRAINER
        DungeonTrainerTracker.track( npc.getEntityUUID(), runtimeTrainerId, preset );
        // SCHEDULE POST-SPAWN DUPLICATE GUARD
        /*
         * EasyNPC has previously produced an additional NPC with another UUID shortly after the intended entity spawned.
         * Keep npc.getEntityUUID() as authoritative and remove any nearby EasyNPC clones appearing shortly afterward.
         */
        DungeonTrainerDuplicateGuard.scheduleCheck( level, npc.getEntityUUID(), npc.getEntity().position() );
        // DEBUG
        DebugLog.log( "Trainer spawned successfully." );
        DebugLog.log( "Preset: " + preset );
        DebugLog.log( "Position: " + npc.getEntity().position());
        DebugLog.log( "NPC UUID: " + npc.getEntityUUID());
        DebugLog.log( "RCT/TBCS trainer ID: " + runtimeTrainerId );
        DebugLog.log( "Tracked dungeon trainers: " + DungeonTrainerTracker.size());
        return true;
    }
    // FIND EXISTING TRACKED TRAINER NEAR POSITION
    private static UUID findTrackedTrainerNear( ServerLevel level, Vec3 position ) {
        if (level == null || position == null) {
            return null;
        }
        double maximumDistanceSquared =
                DungeonEncounterManager.EXISTING_TRAINER_RADIUS * DungeonEncounterManager.EXISTING_TRAINER_RADIUS;
        for (UUID trainerUUID : DungeonTrainerTracker .getTrackedTrainers()) {
            Entity entity = level.getEntity( trainerUUID );
            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                continue;
            }
            double distanceSquared =
                    entity.position()
                            .distanceToSqr( position );
            if (distanceSquared <= maximumDistanceSquared) {
                return trainerUUID;
            }
        }
        return null;
    }
    // ATTACH RANDOM TRAINER TEAM
    private static String attachTrainerTeam( ServerLevel level, EasyNPC<?> npc, ResourceLocation preset ) {
        if (level == null || npc == null || preset == null) {
            return null;
        }
        // DERIVE EASY NPC BASE NAME
        String npcBaseName = getTrainerBaseName( preset );
        if (npcBaseName == null || npcBaseName.isBlank()) {
            DebugLog.log( "Cannot attach TBCS trainer: " + "could not determine NPC base name from " + preset );
            return null;
        }
        // GET TBCS TRAINER REGISTRY
        TrainerRegistry registry;
        try {
            registry =
                    TBCS.getInstance()
                            .getTrainerRegistry();
        } catch (Exception exception) {
            DebugLog.log( "Cannot attach TBCS trainer to " + npcBaseName + ": failed to access TBCS registry." );
            exception.printStackTrace();
            return null;
        }
        if (registry == null) {
            DebugLog.log( "Cannot attach TBCS trainer to " + npcBaseName + ": TBCS registry was null." );
            return null;
        }
        // SINGLE TEAM TEMPLATE
        DungeonTier activeTier = DungeonSession.getTier();
        if (activeTier == null) {
            DebugLog.log( "Cannot attach TBCS trainer to " + npcBaseName + ": no active dungeon tier." );
            return null;
        }

        String trainerThemeId = DungeonTrainerPresets.getTrainerThemeId( preset );
        if (trainerThemeId == null || trainerThemeId.isBlank()) {
            DebugLog.log(
                    "Cannot attach TBCS trainer to "
                            + npcBaseName
                            + ": could not determine trainer theme from "
                            + preset
            );
            return null;
        }

        List<String> trainerTypes =
                DungeonTrainerPresets.getTrainerTypes(
                        level,
                        preset,
                        activeTier
                );

        if (trainerTypes.isEmpty()) {
            DebugLog.log(
                    "Cannot attach TBCS trainer to "
                            + npcBaseName
                            + ": no trainer type folders were found for "
                            + preset
            );
            return null;
        }

        String selectedTrainerId = getSelectedTrainerId( activeTier, trainerThemeId, npcBaseName );

        DebugLog.log(
                "Using TBCS trainer template: "
                        + selectedTrainerId
                        + " with Pokemon type pool "
                        + trainerTypes
        );
        // GET TRAINER TEMPLATE
        TrainerNPC template;
        try {
            template = registry.getById( selectedTrainerId, TrainerNPC.class );
        } catch (Exception exception) {
            DebugLog.log( "Failed to obtain TBCS trainer template " + selectedTrainerId );
            exception.printStackTrace();
            return null;
        }
        if (template == null) {
            DebugLog.log( "TBCS trainer template was not registered: " + selectedTrainerId );
            return null;
        }
        // GET EASY NPC LIVING ENTITY
        if (!(npc.getEntity() instanceof LivingEntity livingEntity)) {
            DebugLog.log(
                    "Cannot attach TBCS trainer "
                            + selectedTrainerId
                            + ": EasyNPC entity is not a LivingEntity."
            );
            return null;
        }
        // COPY TRAINER TEMPLATE
        TrainerNPC runtimeTrainer = new TrainerNPC( template );
        // RANDOMIZE TRAINER POKEMON LEVELS
        randomizeTrainerPokemon( level, runtimeTrainer, trainerTypes );
        // ATTACH EASY NPC ENTITY
        runtimeTrainer.setEntity( livingEntity );
        // UNIQUE RUNTIME TRAINER ID
        String runtimeTrainerId =
                selectedTrainerId
                        + "__dungeon_"
                        + npc
                        .getEntityUUID()
                        .toString()
                        .replace( "-", "" );
        // REGISTER RUNTIME TRAINER
        try {
            registry.registerNPC( runtimeTrainerId, runtimeTrainer );
        } catch (Exception exception) {
            DebugLog.log( "Failed to register runtime dungeon trainer " + runtimeTrainerId );
            exception.printStackTrace();
            return null;
        }
        // VERIFY ATTACHMENT
        String attachedTrainerId = registry.getId( livingEntity );
        if (attachedTrainerId == null) {
            DebugLog.log( "WARNING: TBCS trainer registered, " + "but registry could not resolve the NPC entity." );
        } else {
            DebugLog.log( "TBCS trainer attached successfully." );
            DebugLog.log( "EasyNPC: " + npcBaseName );
            DebugLog.log( "Template trainer: " + selectedTrainerId );
            DebugLog.log( "Runtime trainer: " + runtimeTrainerId );
            DebugLog.log( "Registry entity lookup: " + attachedTrainerId );
        }
        return runtimeTrainerId;
    }

    private static @NotNull String getSelectedTrainerId(
            DungeonTier activeTier,
            String trainerThemeId,
            String npcBaseName
    ) {
        String tierId =
                switch (activeTier) {
                    case TIER_1 -> "tier_1";
                    case TIER_2 -> "tier_2";
                    case TIER_3 -> "tier_3";
                    case TIER_4 -> "tier_4";
                };

        return "tbcs:"
                + trainerThemeId
                + "_"
                + tierId
                + "_"
                + npcBaseName
                + "__team_1";
    }
    // RANDOMIZE TRAINER POKEMON LEVELS
    private static void randomizeTrainerPokemon(
            ServerLevel level,
            TrainerNPC trainer,
            List<String> trainerTypes
    ) {
        if (level == null || trainer == null || trainerTypes == null || trainerTypes.isEmpty()) {
            return;
        }

        DungeonTier tier = DungeonSession.getTier();
        if (tier == null) {
            return;
        }

        List<Species> speciesPool = getEligibleTrainerSpecies( trainerTypes );
        if (speciesPool.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] No eligible trainer Pokemon found for type pool "
                            + trainerTypes
            );
            return;
        }

        for (var pokemon : trainer.getTeam()) {
            Species selectedSpecies = speciesPool.get( level.getRandom().nextInt( speciesPool.size() ) );
            pokemon.setSpecies( selectedSpecies );
            DungeonPokemonLevelManager.applyLevel( pokemon, tier, level.getRandom() );
            pokemon.rollAbility();
            pokemon.setNature( Natures.getRandomNature() );

            float maleRatio = pokemon.getForm().getMaleRatio();
            Gender randomGender;
            if (maleRatio < 0.0F) {
                randomGender = Gender.GENDERLESS;
            } else if (maleRatio == 0.0F) {
                randomGender = Gender.FEMALE;
            } else if (maleRatio == 1.0F) {
                randomGender = Gender.MALE;
            } else {
                randomGender = level.getRandom().nextFloat() < maleRatio ? Gender.MALE : Gender.FEMALE;
            }
            pokemon.setGender( randomGender );
            pokemon.setShiny( level.getRandom().nextInt( 100 ) == 0 );

            for (Stat stat : Stats.Companion.getPERMANENT()) {
                pokemon.setIV( stat, level.getRandom().nextInt( 32 ) );
            }

            applyRandomTrainerEVs( level, pokemon );
            pokemon.initializeMoveset( true );
            applyRandomTrainerHeldItem( level, pokemon );

            DebugLog.log(
                    "[CobblemonNML] Randomized trainer Pokemon: "
                            + selectedSpecies.getName()
                            + " Lv."
                            + pokemon.getLevel()
                            + " from type pool "
                            + trainerTypes
            );
        }
    }

    private static List<Species> getEligibleTrainerSpecies( List<String> trainerTypes ) {
        if (trainerTypes == null || trainerTypes.isEmpty()) {
            return List.of();
        }

        Set<String> requiredTypes = new LinkedHashSet<>();
        for (String trainerType : trainerTypes) {
            if (trainerType == null || trainerType.isBlank()) {
                continue;
            }
            requiredTypes.add( trainerType.trim().toLowerCase( Locale.ROOT ) );
        }

        if (requiredTypes.isEmpty()) {
            return List.of();
        }

        List<Species> eligible = new ArrayList<>();
        for (Species species : PokemonSpecies.getImplemented()) {
            if (species == null) {
                continue;
            }

            Set<String> labels = species.getLabels();
            if (containsLabel( labels, CobblemonPokemonLabels.LEGENDARY )) {
                continue;
            }
            if (containsLabel( labels, CobblemonPokemonLabels.MYTHICAL )) {
                continue;
            }
            if (containsLabel( labels, CobblemonPokemonLabels.ULTRA_BEAST )) {
                continue;
            }
            if (containsLabel( labels, CobblemonPokemonLabels.PARADOX )) {
                continue;
            }

            String primaryType = species.getPrimaryType().getName().toLowerCase( Locale.ROOT );
            String secondaryType =
                    species.getSecondaryType() != null
                            ? species.getSecondaryType().getName().toLowerCase( Locale.ROOT )
                            : null;

            if (!requiredTypes.contains( primaryType )
                    && (secondaryType == null || !requiredTypes.contains( secondaryType ))) {
                continue;
            }

            eligible.add( species );
        }

        DebugLog.log(
                "[CobblemonNML] Eligible trainer species for type pool "
                        + requiredTypes
                        + ": "
                        + eligible.size()
        );
        return List.copyOf( eligible );
    }
    private static void applyRandomTrainerEVs( ServerLevel level, com.cobblemon.mod.common.pokemon.Pokemon pokemon ) {
        if (level == null || pokemon == null) {
            return;
        }
        List<Stat> stats = new ArrayList<>( Stats.Companion.getPERMANENT() );
        Collections.shuffle( stats, new Random( level .getRandom() .nextLong() ) );
        // RESET EVS
        for (Stat stat : stats) {
            pokemon.setEV( stat, 0 );
        }
        // COMPETITIVE-STYLE RANDOM DISTRIBUTION
        //
        // 252 / 252 / 6 = 510 TOTAL
        pokemon.setEV(stats.get(0), 252);
        pokemon.setEV(stats.get(1), 252);
        pokemon.setEV(stats.get(2), 6);
    }

    private static void applyRandomTrainerHeldItem(
            ServerLevel level,
            com.cobblemon.mod.common.pokemon.Pokemon pokemon
    ) {
        if (level == null || pokemon == null || TRAINER_HELD_ITEMS.isEmpty()) {
            return;
        }

        /*
         * 25% chance to have no held item.
         */
        if (level.getRandom() .nextInt(4) == 0) {
            pokemon.swapHeldItem( ItemStack.EMPTY, false, false );
            return;
        }
        ResourceLocation itemId = TRAINER_HELD_ITEMS.get(level .getRandom() .nextInt(TRAINER_HELD_ITEMS.size()));
        Item item = BuiltInRegistries.ITEM.get( itemId );
        if (item == net.minecraft.world.item.Items.AIR) {
            return;
        }
        pokemon.swapHeldItem( new ItemStack( item ), false, false );
    }
    private static boolean containsLabel( Set<String> labels, String wantedLabel ) {
        if (labels == null || wantedLabel == null) {
            return false;
        }
        for (String label : labels) {
            if (wantedLabel.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }
    // EASY NPC PRESET -> BASE TRAINER NAME
    private static String getTrainerBaseName( ResourceLocation preset ) {
        if (preset == null) {
            return null;
        }
        String path = preset.getPath();
        if (path.isBlank()) {
            return null;
        }
        // REMOVE FOLDER PATH
        int lastSlash = path.lastIndexOf('/');
        String fileName = getFileName(lastSlash, path);
        return fileName.trim()
                .toLowerCase();
    }
    private static @NotNull String getFileName(int lastSlash, String path) {
        String fileName =
                lastSlash >= 0
                        ? path.substring( lastSlash + 1 )
                        : path;
        // REMOVE EASY NPC EXTENSION
        String suffix = ".npc.nbt";
        if (fileName.endsWith(suffix)) {
            fileName = fileName.substring( 0, fileName.length() - suffix.length() );
        }
        return fileName;
    }
    // ALPHA
    /*
     * Alpha spawning will be added later.
     */
    private static void handleAlpha( ServerLevel level, BlockPos markerPos ) {
        BlockPos spawnPos = markerPos.above();
        DebugLog.log( "ALPHA encounter requested at " + spawnPos );
    }
    // RAID
    private static boolean handleRaid( ServerLevel level, BlockPos markerPos ) {
        return DungeonRaidManager.spawn( level, markerPos );
    }
    // NORMAL TRIAL SPAWNER
    private static boolean handleTrialSpawner( ServerLevel level, BlockPos markerPos ) {
        boolean spawned =
                DungeonTrialSpawnerManager.spawn( level, markerPos, DungeonTrialSpawnerManager.SpawnerType.NORMAL );
        if (!spawned) {
            DebugLog.log( "Failed to create dungeon trial spawner at " + markerPos );
        }
        return spawned;
    }
    // SCHEDULE ENCOUNTER RETRY
    private static void scheduleRetry(
            ServerLevel level,
            BlockPos markerPos,
            String marker,
            DungeonEncounterContext context,
            int attempt
    ) {
        if (level == null || markerPos == null || marker == null || attempt > MAX_ENCOUNTER_RETRIES) {
            return;
        }
        PENDING_RETRIES.add(
                new PendingEncounterRetry(
                        level,
                        markerPos.immutable(),
                        marker,
                        context,
                        attempt,
                        level.getGameTime()
                                + RETRY_DELAY_TICKS
                )
        );
        DebugLog.log(
                "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                        + "Scheduled retry "
                        + attempt
                        + "/"
                        + MAX_ENCOUNTER_RETRIES
                        + " for "
                        + marker
                        + " at "
                        + markerPos
        );
    }
    // ENCOUNTER RETRY TICK
    @SubscribeEvent
    public static void onServerTick( ServerTickEvent.Post event ) {
        if (PENDING_RETRIES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<PendingEncounterRetry> readyRetries = new ArrayList<>();
        Iterator<PendingEncounterRetry> iterator = PENDING_RETRIES.iterator();
        while (iterator.hasNext()) {
            PendingEncounterRetry pending = iterator.next();
            ServerLevel level = pending.level();
            if (level == null || level.getServer() != server) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.executeAtGameTime()) {
                continue;
            }
            iterator.remove();
            readyRetries.add( pending );
        }

        /*
         * Process retries only AFTER iteration has finished.
         * retryEncounter() may schedule another retry, which adds a new entry to PENDING_RETRIES. Doing that outside the
         * iterator prevents ConcurrentModificationException.
         */
        for (PendingEncounterRetry pending : readyRetries) {
            retryEncounter( pending );
        }
    }
    // RETRY ENCOUNTER
    private static void retryEncounter( PendingEncounterRetry pending ) {
        if (pending == null) {
            return;
        }
        boolean success =
                handleMarkerInternal(
                        pending.level(),
                        pending.markerPos(),
                        pending.marker(),
                        false,
                        pending.context()
                );

        if (success) {
            DebugLog.log(
                    "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                            + pending.marker()
                            + " retry succeeded at "
                            + pending.markerPos()
            );
            return;
        }
        int nextAttempt = pending.attempt() + 1;
        if (nextAttempt > MAX_ENCOUNTER_RETRIES) {
            DebugLog.log(
                    "[CobblemonNML] ENCOUNTER FAIL-SAFE: "
                            + pending.marker()
                            + " failed after "
                            + MAX_ENCOUNTER_RETRIES
                            + " retries at "
                            + pending.markerPos()
            );
            return;
        }
        scheduleRetry( pending.level(), pending.markerPos(), pending.marker(), pending.context(), nextAttempt );
    }
    // CLEAR PENDING RETRIES
    public static void clearPendingRetries() {
        PENDING_RETRIES.clear();
    }
}
