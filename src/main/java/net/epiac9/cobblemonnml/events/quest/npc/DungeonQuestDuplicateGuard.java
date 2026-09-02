package net.epiac9.cobblemonnml.events.quest.npc;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.entity.LivingEntityManager;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonQuestDuplicateGuard {
    // SETTINGS
    private static final double DUPLICATE_RADIUS = 1.75D;

    /*
     * EasyNPC may replace the initially returned entity shortly after a DATA preset spawn.
     * Check on the same short schedule used by normal dungeon trainers so we can either
     * remove true duplicates or adopt the replacement entity if the original UUID vanishes.
     */
    private static final int[] CHECK_DELAYS = {
            2,
            5,
            10,
            20,
            40,
            100
    };
    // PENDING CHECKS
    private static final List<PendingCheck> PENDING_CHECKS = new ArrayList<>();

    private DungeonQuestDuplicateGuard() {
    }
    // SCHEDULE CHECKS
    public static void scheduleCheck(ServerLevel level, UUID intendedQuestNpcUUID, Vec3 spawnPosition) {
        if (level == null || intendedQuestNpcUUID == null || spawnPosition == null) {
            return;
        }

        ResourceLocation definitionId = QuestNpcTracker.getDefinitionId(intendedQuestNpcUUID);
        if (definitionId == null) {
            return;
        }
        String recruitmentId = null;
        Entity intended = level.getEntity(intendedQuestNpcUUID);
        if (intended != null) {
            String tagged = intended.getPersistentData().getString(QuestNpcSpawnManager.RECRUITMENT_ID_TAG);
            if (tagged != null && !tagged.isBlank()) {
                recruitmentId = tagged;
            }
        }

        long currentGameTime = level.getGameTime();
        for (int delay : CHECK_DELAYS) {
            PENDING_CHECKS.add(
                    new PendingCheck(
                            level,
                            intendedQuestNpcUUID,
                            definitionId,
                            recruitmentId,
                            spawnPosition,
                            currentGameTime + delay
                    )
            );
        }

        DebugLog.log(
                "[CobblemonNML] Scheduled Quest NPC duplicate guard for "
                        + intendedQuestNpcUUID
                        + " at "
                        + spawnPosition
        );
    }
    // SERVER TICK
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_CHECKS.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        Iterator<PendingCheck> iterator = PENDING_CHECKS.iterator();
        Set<StabilizedKey> stabilizedKeys = new HashSet<>();

        while (iterator.hasNext()) {
            PendingCheck pending = iterator.next();
            ServerLevel level = pending.level();

            if (level == null || level.getServer() != server) {
                iterator.remove();
                continue;
            }

            if (level.getGameTime() < pending.executeAtGameTime()) {
                continue;
            }

            iterator.remove();
            if (runDuplicateCheck(pending)) {
                stabilizedKeys.add(new StabilizedKey(level, pending.intendedQuestNpcUUID()));
            }
        }

        if (!stabilizedKeys.isEmpty()) {
            PENDING_CHECKS.removeIf(pending -> stabilizedKeys.contains(
                    new StabilizedKey(pending.level(), pending.intendedQuestNpcUUID())
            ));
        }
    }
    // DUPLICATE CHECK
    private static boolean runDuplicateCheck(PendingCheck pending) {
        ServerLevel level = pending.level();
        UUID intendedUUID = pending.intendedQuestNpcUUID();
        ResourceLocation definitionId = pending.definitionId();
        String recruitmentId = pending.recruitmentId();

        List<LivingEntity> nearbyEasyNpcs = getNearbyEasyNpcs(level, pending);
        Entity originalEntity = level.getEntity(intendedUUID);
        boolean originalPresent = originalEntity instanceof LivingEntity livingEntity
                && !livingEntity.isRemoved()
                && livingEntity.isAlive()
                && isEasyNpc(level, livingEntity);

        DebugLog.log(
                "[CobblemonNML] Quest NPC guard check at gameTime="
                        + level.getGameTime()
                        + " for "
                        + intendedUUID
        );
        DebugLog.log("[CobblemonNML] Original entity present: " + originalPresent);
        DebugLog.log("[CobblemonNML] Nearby EasyNPCs: " + nearbyEasyNpcs.size());

        /*
         * Prefer whichever nearby entity is already tracked for this Quest NPC preset.
         * Once a replacement UUID is adopted, later checks will continue following it.
         */
        LivingEntity survivor = findTrackedSurvivor(nearbyEasyNpcs, definitionId);

        if (survivor == null && originalPresent) {
            survivor = (LivingEntity) originalEntity;
        }
        // EASY NPC REPLACED THE ORIGINAL ENTITY
        if (survivor == null) {
            survivor = findSurvivingQuestNpc(nearbyEasyNpcs, pending.spawnPosition());
        }
        if (survivor == null) {
            DebugLog.log("[CobblemonNML] No Quest NPC survivor found yet; waiting for a later guard check.");
            return false;
        }

        UUID survivorUUID = survivor.getUUID();
        ResourceLocation survivorDefinition = QuestNpcTracker.getDefinitionId(survivorUUID);

        if (!definitionId.equals(survivorDefinition)) {
            QuestNpcTracker.untrack(intendedUUID);
            QuestNpcTracker.track(survivorUUID, definitionId);
            if (recruitmentId != null && !recruitmentId.isBlank()) {
                survivor.getPersistentData().putString(
                        QuestNpcSpawnManager.RECRUITMENT_ID_TAG,
                        recruitmentId
                );
            }

            DebugLog.log("[CobblemonNML] Quest NPC replacement detected.");
            DebugLog.log("[CobblemonNML] Previous UUID: " + intendedUUID);
            DebugLog.log("[CobblemonNML] Surviving UUID: " + survivorUUID);
            DebugLog.log("[CobblemonNML] Quest NPC tracker rebound to EasyNPC replacement entity.");
        }

        /*
         * EasyNPC may briefly keep both the original entity and the replacement around while
         * it finishes its DATA-preset lifecycle. Do not destroy either during that overlap;
         * a later check can safely decide which UUID survived.
         */
        if (originalPresent && nearbyEasyNpcs.size() > 1) {
            DebugLog.log("[CobblemonNML] Deferring duplicate cleanup while original Quest NPC is still present.");
            return false;
        }

        for (LivingEntity candidate : nearbyEasyNpcs) {
            if (candidate.getUUID().equals(survivorUUID)) {
                continue;
            }

            DebugLog.log("[CobblemonNML] DUPLICATE QUEST NPC DETECTED AFTER REBIND.");
            DebugLog.log("[CobblemonNML] Surviving UUID: " + survivorUUID);
            DebugLog.log("[CobblemonNML] Duplicate UUID: " + candidate.getUUID());
            DebugLog.log("[CobblemonNML] Duplicate position: " + candidate.position());
            removeDuplicate(level, candidate);
        }

        boolean stableSingleNpc = originalPresent && nearbyEasyNpcs.size() == 1;
        if (!stableSingleNpc && nearbyEasyNpcs.size() == 1) {
            stableSingleNpc = definitionId.equals(QuestNpcTracker.getDefinitionId(survivorUUID));
        }

        if (stableSingleNpc) {
            DebugLog.log(
                    "[CobblemonNML] Quest NPC guard stabilized for "
                            + survivorUUID
                            + "; cancelling remaining scheduled checks."
            );
            return true;
        }

        return false;
    }
    // FIND TRACKED SURVIVOR
    private static LivingEntity findTrackedSurvivor(
            List<LivingEntity> nearbyEasyNpcs,
            ResourceLocation definitionId
    ) {
        for (LivingEntity candidate : nearbyEasyNpcs) {
            ResourceLocation trackedDefinition = QuestNpcTracker.getDefinitionId(candidate.getUUID());
            if (definitionId.equals(trackedDefinition)) {
                return candidate;
            }
        }
        return null;
    }
    // FIND SURVIVING QUEST NPC
    private static LivingEntity findSurvivingQuestNpc(List<LivingEntity> nearbyEasyNpcs, Vec3 spawnPosition) {
        return nearbyEasyNpcs.stream()
                .filter(entity -> entity != null && !entity.isRemoved() && entity.isAlive())
                .min(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(spawnPosition)))
                .orElse(null);
    }
    // NEARBY EASY NPCS
    private static List<LivingEntity> getNearbyEasyNpcs(ServerLevel level, PendingCheck pending) {
        List<LivingEntity> result = new ArrayList<>();

        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, getSearchBox(pending))) {
            if (candidate == null || candidate.isRemoved() || !candidate.isAlive()) {
                continue;
            }

            if (isEasyNpc(level, candidate)) {
                result.add(candidate);
            }
        }

        return result;
    }
    // EASY NPC CHECK
    private static boolean isEasyNpc(ServerLevel level, LivingEntity candidate) {
        try {
            if (LivingEntityManager.getServerEasyNPCEntityByUUID(candidate.getUUID(), level) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }

        String className = candidate.getClass().getName();
        return className.startsWith("de.markusbordihn.easynpc.");
    }
    // SEARCH BOX
    private static @NotNull AABB getSearchBox(PendingCheck pending) {
        Vec3 center = pending.spawnPosition();

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
    private static void removeDuplicate(ServerLevel level, LivingEntity duplicate) {
        UUID duplicateUUID = duplicate.getUUID();
        boolean removedByEasyNpc = false;

        try {
            removedByEasyNpc = EasyNPCEntityHandler.despawn(
                    duplicateUUID,
                    level,
                    NPCRemovalReason.DESPAWNED
            );
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] EasyNPC Quest NPC duplicate despawn threw for " + duplicateUUID);
            exception.printStackTrace();
        }

        Entity remaining = level.getEntity(duplicateUUID);
        if (remaining != null && !remaining.isRemoved()) {
            DebugLog.log("[CobblemonNML] Hard-discarding duplicate Quest NPC " + duplicateUUID);
            remaining.discard();
        }

        QuestNpcTracker.untrack(duplicateUUID);

        DebugLog.log(
                "[CobblemonNML] Duplicate Quest NPC cleanup complete. "
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
            UUID intendedQuestNpcUUID,
            ResourceLocation definitionId,
            String recruitmentId,
            Vec3 spawnPosition,
            long executeAtGameTime
    ) {
    }

    private record StabilizedKey(ServerLevel level, UUID intendedQuestNpcUUID) {
    }
}
