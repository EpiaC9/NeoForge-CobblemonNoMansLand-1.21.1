package net.epiac9.cobblemonnml.events.raid;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import com.necro.raid.dens.common.util.IRaidAccessor;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonRaidDuplicateGuard {
    // SETTINGS
    /*
     * Raid duplicates should normally appear at almost exactly the same position as the authoritative boss.
     */
    private static final double DUPLICATE_RADIUS = 2.0D;

    /*
     * Check shortly after the initial spawn.
     * This catches entities created a few ticks later by another mod system.
     */
    private static final int[] CHECK_DELAYS = {
            2,
            5,
            10
    };
    // PENDING CHECKS
    private static final List<PendingCheck> PENDING_CHECKS = new ArrayList<>();
    // SCHEDULE CHECK
    public static void scheduleCheck( ServerLevel level, UUID raidId, UUID authoritativeBossUUID, Vec3 spawnPosition ) {
        if (level == null || raidId == null || authoritativeBossUUID == null || spawnPosition == null) {
            return;
        }
        long currentTime = level.getGameTime();
        for (int delay : CHECK_DELAYS) {
            PENDING_CHECKS.add(
                    new PendingCheck( level, raidId, authoritativeBossUUID, spawnPosition, currentTime + delay )
            );
        }
        DebugLog.log( "[CobblemonNML] Scheduled raid duplicate guard." );
        DebugLog.log( "[CobblemonNML] Raid UUID: " + raidId );
        DebugLog.log( "[CobblemonNML] Authoritative boss UUID: " + authoritativeBossUUID );
    }
    // SERVER TICK
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_CHECKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<PendingCheck> iterator = PENDING_CHECKS.iterator();
        while (iterator.hasNext()) {
            PendingCheck pending = iterator.next();
            ServerLevel level = pending.level();
            // REMOVE STALE CHECK
            if (level == null || level.getServer() != server) {
                iterator.remove();
                continue;
            }
            // WAIT UNTIL CHECK TIME
            if (level.getGameTime() < pending.executeAtGameTime()) {
                continue;
            }
            iterator.remove();
            runDuplicateCheck( pending );
        }
    }
    // RUN DUPLICATE CHECK
    private static void runDuplicateCheck(PendingCheck pending) {
        ServerLevel level = pending.level();
        UUID raidId = pending.raidId();
        UUID authoritativeBossUUID = pending.authoritativeBossUUID();
        // AUTHORITATIVE BOSS MUST STILL EXIST
        Entity authoritativeBoss = level.getEntity( authoritativeBossUUID );

        /*
         * If the authoritative entity disappeared, do NOT start deleting other bosses.
         * Raid Dens may legitimately replace an entity in some circumstance, so this guard only removes duplicates
         * while the original boss is definitely present.
         */
        if (authoritativeBoss == null || authoritativeBoss.isRemoved()) {
            return;
        }
        // LOCAL SEARCH BOX
        Vec3 center = pending.spawnPosition();
        AABB searchBox =
                new AABB(
                        center.x - DUPLICATE_RADIUS,
                        center.y - DUPLICATE_RADIUS,
                        center.z - DUPLICATE_RADIUS,
                        center.x + DUPLICATE_RADIUS,
                        center.y + DUPLICATE_RADIUS,
                        center.z + DUPLICATE_RADIUS
                );
        List<PokemonEntity> nearbyPokemon = level.getEntitiesOfClass( PokemonEntity.class, searchBox );
        // CHECK FOR SAME-RAID DUPLICATES
        for (PokemonEntity candidate : nearbyPokemon) {
            if (candidate == null || candidate.isRemoved()) {
                continue;
            }
            // Keep the authoritative entity.
            if (candidate.getUUID().equals(authoritativeBossUUID)) {
                continue;
            }
            // GET CANDIDATE RAID ID
            UUID candidateRaidId;
            try {
                IRaidAccessor raidAccessor = (IRaidAccessor) candidate;
                candidateRaidId = raidAccessor.crd_getRaidId();
            } catch (Exception exception) {
                continue;
            }
            // MUST BELONG TO SAME RAID
            if (!raidId.equals(candidateRaidId)) {
                continue;
            }
            // DUPLICATE FOUND
            DebugLog.log( "[CobblemonNML] DUPLICATE RAID BOSS DETECTED." );
            DebugLog.log( "[CobblemonNML] Raid UUID: " + raidId);
            DebugLog.log( "[CobblemonNML] Authoritative boss: " + authoritativeBossUUID );
            DebugLog.log( "[CobblemonNML] Duplicate boss: " + candidate.getUUID());
            DebugLog.log( "[CobblemonNML] Duplicate position: " + candidate.position());
            // REMOVE DUPLICATE
            candidate.discard();
            DebugLog.log( "[CobblemonNML] Duplicate raid boss removed = " + candidate.isRemoved());
        }
    }
    // CLEAR ALL
    public static void clear() {
        PENDING_CHECKS.clear();
    }
    // SERVER STOPPING
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }
    // PENDING CHECK
    private record PendingCheck(
            ServerLevel level,
            UUID raidId,
            UUID authoritativeBossUUID,
            Vec3 spawnPosition,
            long executeAtGameTime
    ) {
    }
}
