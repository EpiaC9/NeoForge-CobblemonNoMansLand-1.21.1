package net.epiac9.cobblemonnml.events.raid;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import com.necro.raid.dens.common.data.raid.RaidBoss;
import com.necro.raid.dens.common.events.RaidEvents;
import com.necro.raid.dens.common.data.raid.RaidTier;
import com.necro.raid.dens.common.raids.RaidInstance;
import com.necro.raid.dens.common.raids.helpers.RaidHelper;
import com.necro.raid.dens.common.registry.RaidRegistry;
import com.necro.raid.dens.common.util.IRaidAccessor;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import kotlin.Unit;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonRaidManager {
    // SETTINGS
    /*
     * Prevent the same marker/location from creating another tracked dungeon raid on top of an existing one.
     */
    private static final double EXISTING_RAID_RADIUS = 2.0D;

    /*
     * Ignore microscopic floating-point differences.
     * Anything exceeding this tolerance is considered real movement and gets corrected immediately.
     */
    private static final double ANCHOR_TOLERANCE_SQUARED = 0.000001D;

    /*
     * The normal cleanup path intentionally does not perform a second full-world verification scan. The generic slot-wide
     * safety sweeps already provide later cleanup coverage, and avoiding a duplicate getAllEntities() pass keeps dungeon exit
     * lighter. Set this to true temporarily when diagnosing cleanup.
     */
    private static final boolean VERIFY_RAID_CLEANUP = false;
    // DUNGEON RAID TRACKING
    /*
     * Raid UUID -> authoritative boss entity UUID.
     */
    private static final Map<UUID, UUID> DUNGEON_RAID_BOSSES = new HashMap<>();

    /*
     * Boss entity UUID -> exact dungeon marker position.
     * Dungeon raid bosses are hard-locked to this position every server tick.
     */
    private static final Map<UUID, Vec3> DUNGEON_RAID_ANCHORS = new HashMap<>();

    /*
     * Boss UUIDs which have already produced a drift warning.
     * We only need one detailed warning per boss. Otherwise, a flying Pokemon attempting to move every tick could flood
     * the server console.
     */
    private static final Set<UUID> DRIFT_WARNED = new HashSet<>();

    /*
     * Theme + dungeon tier -> eligible Raid Dens boss indices.
     * Building this list requires walking the Raid Dens registry and constructing preview Pokemon to inspect their native types. A
     * dungeon can contain several raid rooms, so cache that expensive result for the lifetime of the dungeon instead of rebuilding it
     * for every raid marker.
     */
    private static final Map<DungeonTheme, Map<DungeonTier, BitSet>>
            RAID_BOSS_MATCH_CACHE = new EnumMap<>(DungeonTheme.class);

    /*
     * Raid Dens can notify each participant independently when a raid ends.
     * Keep a tiny same-tick guard so a duplicated callback cannot advance a
     * player's raid objective twice.
     */
    private static final Map<UUID, Long> LAST_RAID_QUEST_PROGRESS_TICK = new HashMap<>();

    static {
        registerRaidQuestEvents();
    }
    // RAID QUEST OBJECTIVE EVENT
    private static void registerRaidQuestEvents() {
        try {
            RaidEvents.RAID_END.subscribe(
                    Priority.NORMAL,
                    event -> {
                        handleRaidEndForQuests(event);
                        return Unit.INSTANCE;
                    }
            );
            DebugLog.log("[CobblemonNML] Registered Raid Dens quest completion listener.");
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to register Raid Dens quest completion listener.");
            exception.printStackTrace();
        }
    }

    private static void handleRaidEndForQuests(Object event) {
        if (event == null || !DungeonSession.isActive()) {
            return;
        }

        Boolean won = readRaidEventBoolean(event, "isWin", "getWin", "win");
        if (!Boolean.TRUE.equals(won)) {
            return;
        }

        Object playerValue = readRaidEventValue(event, "player", "getPlayer");
        if (!(playerValue instanceof ServerPlayer player)) {
            return;
        }

        /*
         * Dungeon raid objectives are only progressed while the player is
         * actually still inside the CobblemonNML dungeon. This matches the
         * dungeon quest lifetime rule and avoids crediting unrelated raids.
         */
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            return;
        }

        long gameTime = player.serverLevel().getGameTime();
        Long previousTick = LAST_RAID_QUEST_PROGRESS_TICK.put(player.getUUID(), gameTime);
        if (previousTick != null && previousTick == gameTime) {
            return;
        }

        int progressed = QuestRuntimeManager.progressByType(player, "raid_battle", 1);
        if (progressed > 0) {
            DebugLog.log(
                    "[CobblemonNML] Dungeon raid victory progressed "
                            + progressed
                            + " raid quest objective(s) for "
                            + player.getGameProfile().getName()
            );
        }
    }

    private static Boolean readRaidEventBoolean(Object event, String... methodNames) {
        Object value = readRaidEventValue(event, methodNames);
        return value instanceof Boolean bool ? bool : null;
    }

    private static Object readRaidEventValue(Object event, String... methodNames) {
        if (event == null || methodNames == null) {
            return null;
        }
        for (String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) {
                continue;
            }
            try {
                return event.getClass().getMethod(methodName).invoke(event);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
    // SPAWN RAID
    public static boolean spawn( ServerLevel level, BlockPos markerPos ) {
        if (level == null || markerPos == null) {
            return false;
        }
        Vec3 intendedPosition = new Vec3( markerPos.getX() + 0.5D, markerPos.getY(), markerPos.getZ() + 0.5D );
        // PRE-SPAWN DUPLICATE FAILSAFE
        UUID existingBoss = findTrackedBossNear( level, intendedPosition );
        if (existingBoss != null) {
            DebugLog.log( "[CobblemonNML] Skipping duplicate raid spawn." );
            DebugLog.log( "[CobblemonNML] Marker: " + markerPos );
            DebugLog.log( "[CobblemonNML] Existing boss UUID: " + existingBoss );
            return false;
        }
        // CURRENT DUNGEON THEME + TIER
        DungeonTheme dungeonTheme = DungeonSession.getTheme();
        DungeonTier dungeonTier = DungeonSession.getTier();
        if (dungeonTheme == null) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "no active dungeon theme." );
            return false;
        }
        if (dungeonTier == null) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "no active dungeon tier." );
            return false;
        }
        // ALLOWED RAID DENS TIERS
        List<RaidTier> allowedRaidTiers = getAllowedRaidTiers( dungeonTier );
        if (allowedRaidTiers.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Cannot create dungeon raid: "
                            + "no Raid Dens tiers mapped for "
                            + dungeonTier
            );
            return false;
        }
        // FIND REAL NATIVE-TYPE RAID BOSS
        /*
         * IMPORTANT:
         * Raid Dens RaidType is NOT used to decide whether a Pokemon belongs in the dungeon theme.
         * Instead, every eligible RaidBoss is inspected using the actual native Cobblemon types of its Pokemon.
         */
        ResourceLocation bossId = getRandomNativeTypeRaidBoss( level, dungeonTheme, dungeonTier, allowedRaidTiers );
        if (bossId == null) {
            DebugLog.log( "[CobblemonNML] Cannot create themed dungeon raid." );
            DebugLog.log(
                    "[CobblemonNML] No native "
                            + dungeonTheme.getDisplayName()
                            + "-type Raid Dens boss exists in "
                            + allowedRaidTiers
            );
            return false;
        }
        // RAID BOSS DATA
        RaidBoss raidBoss = RaidRegistry.getRaidBoss( bossId );
        if (raidBoss == null) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "RaidBoss was null for " + bossId );
            return false;
        }
        RaidTier selectedRaidTier = raidBoss.getTier();
        // BUILD RAID BOSS ENTITY
        PokemonEntity bossEntity;
        try {
            bossEntity = raidBoss.getBossEntity( level, null );
        } catch (Exception exception) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "exception while building boss " + bossId );
            exception.printStackTrace();
            return false;
        }
        if (bossEntity == null) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "failed to build boss entity for " + bossId );
            return false;
        }
        // VERIFY ACTUAL CREATED BOSS TYPE
        if (!pokemonMatchesTheme( bossEntity.getPokemon(), dungeonTheme )) {
            DebugLog.log( "=================================================");
            DebugLog.log( "[CobblemonNML] RAID SAFETY CHECK FAILED" );
            DebugLog.log( "[CobblemonNML] Dungeon theme: " + dungeonTheme.getDisplayName());
            DebugLog.log( "[CobblemonNML] Selected Raid boss: " + bossId );
            DebugLog.log( "[CobblemonNML] Actual native types: " + getPokemonTypeNames(bossEntity.getPokemon()));
            DebugLog.log( "[CobblemonNML] Boss rejected." );
            DebugLog.log( "=================================================" );
            bossEntity.discard();
            return false;
        }
        // CREATE UNIQUE RAID ID
        UUID raidId = UUID.randomUUID();
        IRaidAccessor raidAccessor = (IRaidAccessor) bossEntity;
        raidAccessor.crd_setRaidId( raidId );
        // CONFIGURE BOSS
        bossEntity.setInvulnerable( true );
        bossEntity.setPersistenceRequired();
        // INITIAL POSITION
        bossEntity.moveTo( intendedPosition.x, intendedPosition.y, intendedPosition.z, 0.0F, 0.0F );
        // INITIAL MOVEMENT LOCK
        lockBossMovement( bossEntity, intendedPosition );
        // CREATE RAID INSTANCE
        RaidInstance raidInstance = new RaidInstance( bossEntity, null, false );
        if (raidInstance.failedToStart()) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "RaidInstance failed to start." );
            bossEntity.discard();
            return false;
        }
        // REGISTER RAID
        RaidHelper.ACTIVE_RAIDS.put( raidId, raidInstance );
        // ADD BOSS TO WORLD
        boolean added = level.addFreshEntity( bossEntity );
        if (!added) {
            DebugLog.log( "[CobblemonNML] Cannot create dungeon raid: " + "failed to add raid boss entity." );
            RaidHelper.ACTIVE_RAIDS.remove( raidId );
            bossEntity.discard();
            return false;
        }
        // TRACK AUTHORITATIVE BOSS
        UUID bossUUID = bossEntity.getUUID();
        DUNGEON_RAID_BOSSES.put( raidId, bossUUID );
        // TRACK EXACT ANCHOR
        DUNGEON_RAID_ANCHORS.put( bossUUID, intendedPosition );
        // ENFORCE LOCK AFTER ADDING TO WORLD
        lockBossMovement( bossEntity, intendedPosition );
        // POST-SPAWN DUPLICATE GUARD
        DungeonRaidDuplicateGuard.scheduleCheck( level, raidId, bossUUID, bossEntity.position() );
        // DEBUG
        DebugLog.log( "[CobblemonNML] Themed dungeon raid created successfully." );
        DebugLog.log( "[CobblemonNML] Dungeon theme: " + dungeonTheme.getDisplayName());
        DebugLog.log( "[CobblemonNML] Dungeon tier: " + dungeonTier.getDisplayName());
        DebugLog.log( "[CobblemonNML] Allowed Raid tiers: " + allowedRaidTiers);
        DebugLog.log( "[CobblemonNML] Selected Raid tier: " + selectedRaidTier );
        DebugLog.log("[CobblemonNML] Raid boss: " + bossId );
        DebugLog.log( "[CobblemonNML] Native Pokemon types: " + getPokemonTypeNames(bossEntity.getPokemon()));
        DebugLog.log( "[CobblemonNML] Raid Dens raid type: " + raidBoss.getType());
        DebugLog.log( "[CobblemonNML] Raid boss movement: HARD LOCKED" );
        DebugLog.log( "[CobblemonNML] Boss anchor: " + intendedPosition );
        DebugLog.log( "[CobblemonNML] Raid UUID: " + raidId );
        DebugLog.log( "[CobblemonNML] Boss UUID: " + bossUUID );
        DebugLog.log( "[CobblemonNML] Boss position: " + bossEntity.position());
        DebugLog.log( "[CobblemonNML] Tracked dungeon raids: " + DUNGEON_RAID_BOSSES.size());
        DebugLog.log( "[CobblemonNML] Active Raid Dens raids: " + RaidHelper.ACTIVE_RAIDS.size());
        return true;
    }
    // RANDOM NATIVE-TYPE RAID BOSS
    private static ResourceLocation getRandomNativeTypeRaidBoss(
            ServerLevel level,
            DungeonTheme theme,
            DungeonTier dungeonTier,
            List<RaidTier> allowedRaidTiers
    ) {
        if (level == null
                || theme == null
                || dungeonTier == null
                || allowedRaidTiers == null
                || allowedRaidTiers.isEmpty()) {
            return null;
        }
        BitSet matchingBosses = getOrBuildMatchingRaidBosses( theme, dungeonTier, allowedRaidTiers );
        if (matchingBosses.isEmpty()) {
            return null;
        }

        /*
         * Pass a clone so Raid Dens is free to manipulate the BitSet
         * internally without corrupting our cached eligibility set.
         */
        BitSet selectionPool = (BitSet) matchingBosses.clone();
        return RaidRegistry.getRandomRaidBoss( level.getRandom(), level, selectionPool, null );
    }
    // CACHED NATIVE-TYPE RAID BOSS SEARCH
    private static BitSet getOrBuildMatchingRaidBosses(
            DungeonTheme theme,
            DungeonTier dungeonTier,
            List<RaidTier> allowedRaidTiers
    ) {
        Map<DungeonTier, BitSet> tierCache =
                RAID_BOSS_MATCH_CACHE.computeIfAbsent( theme, ignored -> new EnumMap<>(DungeonTier.class) );
        BitSet cached = tierCache.get( dungeonTier );
        if (cached != null) {
            return cached;
        }
        BitSet matchingBosses = new BitSet();
        int inspected = 0;
        int matching = 0;
        for (ResourceLocation raidBossId : RaidRegistry.getAll()) {
            if (raidBossId == null) {
                continue;
            }
            RaidBoss raidBoss = RaidRegistry.getRaidBoss( raidBossId );
            if (raidBoss == null) {
                continue;
            }
            inspected++;
            if (!allowedRaidTiers.contains( raidBoss.getTier() )) {
                continue;
            }
            if (!raidBossMatchesTheme( raidBoss, theme )) {
                continue;
            }
            Integer registryIndex =
                    RaidRegistry
                            .RAID_INDEX
                            .get( raidBossId );
            if (registryIndex == null) {
                continue;
            }
            matchingBosses.set( registryIndex );
            matching++;
        }
        tierCache.put( dungeonTier, matchingBosses );
        DebugLog.log( "[CobblemonNML] Native-type raid cache built." );
        DebugLog.log( "[CobblemonNML] Dungeon theme: " + theme.getDisplayName());
        DebugLog.log( "[CobblemonNML] Dungeon tier: " + dungeonTier.getDisplayName());
        DebugLog.log( "[CobblemonNML] Allowed Raid tiers: " + allowedRaidTiers );
        DebugLog.log( "[CobblemonNML] Raid bosses inspected: " + inspected );
        DebugLog.log( "[CobblemonNML] Matching raid bosses cached: " + matching );
        return matchingBosses;
    }
    // INVALIDATE RAID ELIGIBILITY CACHE
    public static void invalidateRaidBossCache() {
        RAID_BOSS_MATCH_CACHE.clear();
    }
    // RAID BOSS -> NATIVE TYPE
    private static boolean raidBossMatchesTheme( RaidBoss raidBoss, DungeonTheme theme ) {
        if (raidBoss == null || theme == null) {
            return false;
        }
        try {
            Pokemon previewPokemon =
                    raidBoss
                            .getBossProperties()
                            .create();
            return pokemonMatchesTheme( previewPokemon, theme );
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] Could not inspect native Pokemon "
                            + "type for RaidBoss "
                            + raidBoss.getId()
            );
            return false;
        }
    }
    // POKEMON MATCHES DUNGEON THEME
    private static boolean pokemonMatchesTheme( Pokemon pokemon, DungeonTheme theme ) {
        if (pokemon == null || theme == null) {
            return false;
        }
        String requiredType =
                theme
                        .getId()
                        .trim()
                        .toLowerCase();
        for (ElementalType type : pokemon .getForm() .getTypes()) {
            if (type == null) {
                continue;
            }
            if (type .showdownId() .equalsIgnoreCase( requiredType )) {
                return true;
            }
        }
        return false;
    }
    // GET POKEMON NATIVE TYPE NAMES
    private static List<String> getPokemonTypeNames(Pokemon pokemon) {
        List<String> result = new ArrayList<>();
        if (pokemon == null) {
            return result;
        }
        for (ElementalType type : pokemon .getForm() .getTypes()) {
            if (type == null) {
                continue;
            }
            result.add( type.showdownId() );
        }
        return result;
    }
    // HARD LOCK BOSS MOVEMENT
    private static void lockBossMovement( PokemonEntity bossEntity, Vec3 anchor ) {
        if (bossEntity == null || anchor == null || bossEntity.isRemoved()) {
            return;
        }
        bossEntity.setNoAi( true );
        bossEntity.setNoGravity( true );
        try {
            bossEntity
                    .getNavigation()
                    .stop();
        } catch (Exception ignored) {
        }
        bossEntity.setDeltaMovement( Vec3.ZERO );
        Vec3 currentPosition = bossEntity.position();
        double driftDistanceSquared = currentPosition.distanceToSqr( anchor );
        if (driftDistanceSquared > ANCHOR_TOLERANCE_SQUARED) {
            UUID bossUUID = bossEntity.getUUID();
            if (DRIFT_WARNED.add( bossUUID )) {
                DebugLog.log( "=================================================" );
                DebugLog.log( "[CobblemonNML] RAID BOSS DRIFT CORRECTED" );
                DebugLog.log( "[CobblemonNML] Pokemon: " + bossEntity .getName() .getString() );
                DebugLog.log( "[CobblemonNML] Native Pokemon types: " + getPokemonTypeNames( bossEntity.getPokemon()) );
                DebugLog.log( "[CobblemonNML] Boss UUID: " + bossUUID );
                try {
                    IRaidAccessor raidAccessor = (IRaidAccessor) bossEntity;
                    DebugLog.log( "[CobblemonNML] Raid UUID: " + raidAccessor .crd_getRaidId() );
                } catch (Exception ignored) {
                }
                DebugLog.log( "[CobblemonNML] Anchor: " + anchor );
                DebugLog.log( "[CobblemonNML] Drifted position: " + currentPosition );
                DebugLog.log( "[CobblemonNML] Distance moved: " + Math.sqrt(driftDistanceSquared));
                DebugLog.log( "[CobblemonNML] NoAI: " + bossEntity.isNoAi());
                DebugLog.log( "[CobblemonNML] NoGravity: " + bossEntity.isNoGravity());
                DebugLog.log( "[CobblemonNML] Returning boss to anchor." );
                DebugLog.log( "=================================================" );
            }
            bossEntity.moveTo( anchor.x, anchor.y, anchor.z, bossEntity.getYRot(), bossEntity.getXRot() );
        }
        bossEntity.setDeltaMovement( Vec3.ZERO );
    }
    // SERVER-TICK POSITION LOCK
    @SubscribeEvent
    public static void onServerTick( ServerTickEvent.Post event ) {
        if (DUNGEON_RAID_ANCHORS.isEmpty()) {
            return;
        }
        ServerLevel dungeonLevel =
                event
                        .getServer()
                        .getLevel( DungeonDimension .DUNGEON_DIMENSION );
        if (dungeonLevel == null) {
            return;
        }
        for (Map.Entry<UUID, Vec3> entry : DUNGEON_RAID_ANCHORS.entrySet()) {
            UUID bossUUID = entry.getKey();
            Vec3 anchor = entry.getValue();
            if (bossUUID == null || anchor == null) {
                continue;
            }
            Entity entity = dungeonLevel.getEntity( bossUUID );
            if (!(entity instanceof PokemonEntity bossEntity)) {
                continue;
            }
            if (bossEntity.isRemoved() || !bossEntity.isAlive()) {
                continue;
            }
            lockBossMovement( bossEntity, anchor );
        }
    }
    // FIND TRACKED BOSS NEAR POSITION
    private static UUID findTrackedBossNear( ServerLevel level, Vec3 position ) {
        if (level == null || position == null) {
            return null;
        }
        double maxDistanceSquared = DungeonRaidManager.EXISTING_RAID_RADIUS * DungeonRaidManager.EXISTING_RAID_RADIUS;
        for (UUID bossUUID : DUNGEON_RAID_BOSSES.values()) {
            if (bossUUID == null) {
                continue;
            }
            Entity boss = level.getEntity( bossUUID );
            if (boss == null || boss.isRemoved() || !boss.isAlive()) {
                continue;
            }
            double distanceSquared =
                    boss
                            .position()
                            .distanceToSqr( position );
            if (distanceSquared <= maxDistanceSquared) {
                return bossUUID;
            }
        }
        return null;
    }
    // CLEANUP ALL DUNGEON RAIDS
    public static void cleanupDungeonRaids( ServerLevel level ) {
        if (level == null) {
            return;
        }
        DungeonRaidDuplicateGuard.clear();

        /*
         * Eligibility is cached only for the lifetime of a dungeon.
         * Clearing it here keeps datapack/resource changes safe and prevents old theme/tier results from accumulating forever.
         */
        invalidateRaidBossCache();
        if (DUNGEON_RAID_BOSSES.isEmpty()) {
            DUNGEON_RAID_ANCHORS.clear();
            DRIFT_WARNED.clear();
            DebugLog.log( "[CobblemonNML] No tracked dungeon raids to clean up." );
            return;
        }
        Set<UUID> raidIds = new HashSet<>( DUNGEON_RAID_BOSSES.keySet() );
        Set<UUID> authoritativeBossIds = new HashSet<>( DUNGEON_RAID_BOSSES.values() );
        authoritativeBossIds.remove( null );
        DebugLog.log( "[CobblemonNML] Cleaning up " + raidIds.size() + " dungeon raid(s)." );
        DebugLog.log( "[CobblemonNML] Authoritative boss UUIDs tracked: " + authoritativeBossIds.size() );
        // REMOVE RAID DENS INSTANCES FIRST
        for (UUID raidId : raidIds) {
            RaidHelper.ACTIVE_RAIDS.remove( raidId );
        }
        // REMOVE AUTHORITATIVE BOSSES THAT ARE ALREADY LOADED
        /*
         * Do NOT synchronously force-load anchor chunks here.
         * Previous testing showed level.getChunkAt(anchorPos) can return before persisted entities are registered in the
         * ServerLevel, while still creating a server-thread hitch on dungeon exit. The generic final/post-reset/pre-encounter
         * slot sweeps already remove those entities safely when their chunks become loaded.
         */
        int authoritativeRemoved = 0;
        int authoritativeUnavailable = 0;
        for (UUID bossUUID : authoritativeBossIds) {
            Entity boss = level.getEntity( bossUUID );
            if (boss == null || boss.isRemoved()) {
                authoritativeUnavailable++;
                continue;
            }
            DebugLog.log(
                    "[CobblemonNML] Removing loaded authoritative dungeon raid boss "
                            + bossUUID
                            + " at "
                            + boss.blockPosition()
            );
            boss.discard();
            authoritativeRemoved++;
        }
        // OPTIONAL DIAGNOSTIC VERIFICATION
        /*
         * DungeonGenerationEvents runs the generic slot-wide entity sweep immediately after this method. That sweep removes all
         * loaded non-player entities in the slot, so doing another unconditional getAllEntities() pass here would duplicate the
         * same work.
         * Keep the old raid-specific scan available only as a temporary diagnostic.
         */
        int diagnosticRemoved = 0;
        int survivors = 0;
        if (VERIFY_RAID_CLEANUP) {
            for (Entity entity : level.getAllEntities()) {
                if (entity == null || entity.isRemoved()) {
                    continue;
                }
                boolean authoritativeMatch = authoritativeBossIds.contains( entity.getUUID() );
                UUID entityRaidId = getRaidId( entity );
                boolean raidMatch =
                        entityRaidId != null
                                && raidIds.contains( entityRaidId );
                if (!authoritativeMatch && !raidMatch) {
                    continue;
                }
                survivors++;
                DebugLog.log(
                        "[CobblemonNML] RAID CLEANUP DIAGNOSTIC removing loaded matching entity "
                                + entity.getUUID()
                                + " at "
                                + entity.blockPosition()
                                + ( entityRaidId != null ? " raid=" + entityRaidId : "" )
                );
                entity.discard();
                diagnosticRemoved++;
            }
        }
        DUNGEON_RAID_BOSSES.clear();
        DUNGEON_RAID_ANCHORS.clear();
        DRIFT_WARNED.clear();
        LAST_RAID_QUEST_PROGRESS_TICK.clear();
        DebugLog.log( "[CobblemonNML] Dungeon raid cleanup complete." );
        DebugLog.log(
                "[CobblemonNML] Authoritative bosses removed="
                        + authoritativeRemoved
                        + ", currently unavailable="
                        + authoritativeUnavailable
        );
        if (authoritativeUnavailable > 0) {
            DebugLog.log(
                    "[CobblemonNML] Unloaded raid bosses will be handled by "
                            + "the generic post-reset/pre-encounter slot sweeps."
            );
        }
        if (VERIFY_RAID_CLEANUP) {
            DebugLog.log( "[CobblemonNML] Raid cleanup diagnostic matches removed=" + diagnosticRemoved );
            DebugLog.log( "[CobblemonNML] Raid cleanup diagnostic matches found=" + survivors );
        }
        DebugLog.log( "[CobblemonNML] Active Raid Dens raids remaining: " + RaidHelper.ACTIVE_RAIDS.size() );
    }
    // READ RAID ID FROM LOADED ENTITY
    private static UUID getRaidId( Entity entity ) {
        if (!(entity instanceof PokemonEntity) || !(entity instanceof IRaidAccessor raidAccessor)) {
            return null;
        }
        try {
            return raidAccessor.crd_getRaidId();
        } catch (Exception ignored) {
            return null;
        }
    }
    // DUNGEON TIER -> ALLOWED RAID DENS TIERS
    private static List<RaidTier> getAllowedRaidTiers( DungeonTier tier ) {
        if (tier == null) {
            return List.of();
        }
        return switch (tier) {
            case TIER_1 ->
                    List.of( RaidTier.TIER_ONE, RaidTier.TIER_TWO );
            case TIER_2 ->
                    List.of( RaidTier.TIER_THREE, RaidTier.TIER_FOUR );
            case TIER_3 ->
                    List.of( RaidTier.TIER_FIVE, RaidTier.TIER_SIX );
            case TIER_4 ->
                    List.of( RaidTier.TIER_SEVEN );
        };
    }
}
