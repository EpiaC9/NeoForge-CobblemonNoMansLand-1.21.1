package net.epiac9.cobblemonnml.events.trainer;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.entity.LivingEntityManager;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonTrainerDuplicateGuard {
    // SETTINGS
    /*
     * A duplicate EasyNPC has previously appeared one block below the originally spawned NPC.
     * 1.75 blocks gives enough room to catch that case while still keeping the search local to the trainer marker.
     */
    private static final double DUPLICATE_RADIUS = 1.75D;

    /*
     * EasyNPC duplicates may not appear on exactly the same tick as spawn.
     * Check several times shortly after creation.
     */
    private static final int[] CHECK_DELAYS = {
            2,
            5,
            10
    };
    // PENDING CHECKS
    private static final List<PendingCheck> PENDING_CHECKS = new ArrayList<>();
    // SCHEDULE CHECKS
    public static void scheduleCheck( ServerLevel level, UUID intendedTrainerUUID, Vec3 spawnPosition ) {
        if (level == null || intendedTrainerUUID == null || spawnPosition == null) {
            return;
        }
        long currentGameTime = level.getGameTime();
        for (int delay : CHECK_DELAYS) {
            PENDING_CHECKS.add(
                    new PendingCheck( level, intendedTrainerUUID, spawnPosition, currentGameTime + delay )
            );
        }
        DebugLog.log(
                "[CobblemonNML] Scheduled duplicate guard for trainer "
                        + intendedTrainerUUID
                        + " at "
                        + spawnPosition
        );
    }
    // SERVER TICK
    @SubscribeEvent
    public static void onServerTick( ServerTickEvent.Post event ) {
        if (PENDING_CHECKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<PendingCheck> iterator = PENDING_CHECKS.iterator();
        while (iterator.hasNext()) {
            PendingCheck pending = iterator.next();
            ServerLevel level = pending.level();

            /*
             * Remove stale checks from an old/stopped server.
             */
            if (level == null || level.getServer() != server) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.executeAtGameTime()) {
                continue;
            }
            iterator.remove();
            runDuplicateCheck( pending );
        }
    }
    // DUPLICATE CHECK
    private static void runDuplicateCheck(PendingCheck pending) {
        ServerLevel level = pending.level();
        UUID intendedUUID = pending.intendedTrainerUUID();
        // VERIFY INTENDED TRAINER STILL EXISTS
        Entity intendedEntity = level.getEntity( intendedUUID );
        if (intendedEntity == null || intendedEntity.isRemoved()) {

            /*
             * Do not delete nearby NPCs if the entity we were supposed to preserve no longer exists.
             */
            return;
        }
        AABB searchBox = getSearchBox(pending);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass( LivingEntity.class, searchBox );
        if (nearbyEntities.isEmpty()) {
            return;
        }
        // REMOVE UNEXPECTED EASY NPC ENTITIES
        for (LivingEntity candidate : nearbyEntities) {
            if (candidate == null || candidate.isRemoved()) {
                continue;
            }

            // Keep the authoritative trainer.
            if (candidate.getUUID().equals( intendedUUID )) {
                continue;
            }
            // CONFIRM THIS IS AN EASY NPC
            boolean isEasyNpc = false;
            try {
                isEasyNpc =
                        LivingEntityManager
                                .getServerEasyNPCEntityByUUID( candidate.getUUID(), level ) != null;
            } catch (Exception ignored) {
            }

            /*
             * Fallback in case the duplicate exists as an entity
             * but EasyNPC's internal registry has already become
             * inconsistent.
             */
            if (!isEasyNpc) {
                String className =
                        candidate.getClass()
                                .getName();
                isEasyNpc = className.startsWith( "de.markusbordihn.easynpc." );
            }
            if (!isEasyNpc) {
                continue;
            }
            // DUPLICATE FOUND
            DebugLog.log( "[CobblemonNML] DUPLICATE TRAINER DETECTED." );
            DebugLog.log( "[CobblemonNML] Intended UUID: " + intendedUUID );
            DebugLog.log( "[CobblemonNML] Duplicate UUID: " + candidate.getUUID());
            DebugLog.log( "[CobblemonNML] Duplicate position: " + candidate.position());
            removeDuplicate( level, candidate );
        }
    }
    private static @NotNull AABB getSearchBox(PendingCheck pending) {
        Vec3 center = pending.spawnPosition();
        // LOCAL SEARCH BOX
        return new AABB(
                center.x - DUPLICATE_RADIUS,
                center.y - DUPLICATE_RADIUS,
                center.z - DUPLICATE_RADIUS,
                center.x + DUPLICATE_RADIUS,
                center.y + DUPLICATE_RADIUS,
                center.z + DUPLICATE_RADIUS
        );
    }
    // REMOVE DUPLICATE
    private static void removeDuplicate( ServerLevel level, LivingEntity duplicate ) {
        UUID duplicateUUID = duplicate.getUUID();
        boolean removedByEasyNpc = false;
        // TRY NORMAL EASY NPC CLEANUP FIRST
        try {
            removedByEasyNpc =
                    EasyNPCEntityHandler
                            .despawn( duplicateUUID, level, NPCRemovalReason.DESPAWNED );
        } catch (Exception exception) {
            DebugLog.log( "[CobblemonNML] EasyNPC duplicate despawn threw for " + duplicateUUID );
            exception.printStackTrace();
        }
        // HARD FALLBACK
        Entity remaining = level.getEntity( duplicateUUID );
        if (remaining != null && !remaining.isRemoved()) {
            DebugLog.log( "[CobblemonNML] Hard-discarding duplicate trainer " + duplicateUUID );
            remaining.discard();
        }
        DebugLog.log(
                "[CobblemonNML] Duplicate trainer cleanup complete. "
                        + "EasyNPC result="
                        + removedByEasyNpc
                        + ", UUID="
                        + duplicateUUID
        );
    }
    // CLEAR
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
            UUID intendedTrainerUUID,
            Vec3 spawnPosition,
            long executeAtGameTime
    ) {
    }
}
