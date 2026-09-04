package net.epiac9.cobblemonnml.dimension.generation;

import com.gitlab.srcmc.tbcs.api.TBCS;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;

import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectRuntime;
import net.epiac9.cobblemonnml.dimension.*;
import net.epiac9.cobblemonnml.dimension.encounter.DungeonEncounterManager;
import net.epiac9.cobblemonnml.dimension.reset.DungeonResetQueue;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.dimension.timer.DungeonTimer;
import net.epiac9.cobblemonnml.portal.DungeonPortalManager;
import net.epiac9.cobblemonnml.portal.DungeonPortalSelectionManager;
import net.epiac9.cobblemonnml.events.raid.DungeonRaidManager;
import net.epiac9.cobblemonnml.events.quest.npc.QuestNpcSpawnManager;
import net.epiac9.cobblemonnml.events.quest.npc.QuestNpcTracker;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;
import net.epiac9.cobblemonnml.events.quest.item.DungeonQuestItemMarkerManager;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerBattleEvents;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerTracker;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.events.vault.DungeonVaultManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonGenerationEvents {
    // SERVER TICK
    @SubscribeEvent
    public static void onServerTick( ServerTickEvent.Post event ) {
        MinecraftServer server = event.getServer();
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension.DUNGEON_DIMENSION );
        // PORTAL ARMING
        DungeonPortalSelectionManager.tick( server );
        // TIMER
        DungeonTimer.tick( server );
        // DUNGEON-SCOPED ACTION TYPE EFFECTS
        ActionBattleTypeEffectRuntime.tick(server);
        // DUNGEON GENERATION
        DungeonGenerationQueue.tick();
        // OMINOUS VAULT PROXIMITY
        if (dungeonLevel != null) {
            DungeonVaultManager.tick( dungeonLevel );
        }
        // RESUME SAVED RESET
        DungeonResetQueue.resumeSavedReset( server );
        // BACKGROUND CLEANUP
        int cleanupBudget =
                DungeonGenerationQueue.isGenerating()
                        ? 1
                        : 4;
        DungeonResetQueue.tick( cleanupBudget );
        // SESSION RESET REQUESTED?
        if (!DungeonDimensionEvents .isResetPending()) {
            return;
        }
        if (dungeonLevel == null) {
            return;
        }
        // WAIT UNTIL EVERY ONLINE PLAYER IS GONE
        if (!dungeonLevel .players() .isEmpty()) {
            // RESET REQUEST FAIL-SAFE
            if (!DungeonDimensionEvents .hasResetRequestTimedOut( dungeonLevel )) {
                return;
            }
            DebugLog.log(
                    "[CobblemonNML] FAIL-SAFE: "
                            + "Dungeon reset request exceeded timeout "
                            + "while players were still inside."
            );

            /*
             * A reset was already requested, so these players should no longer be allowed to keep the old dungeon alive.
             * Kill a copied player list because player.kill() may alter the dimension/player collection while this loop runs.
             */
            for (ServerPlayer player : new ArrayList<>( dungeonLevel.players() )) {
                DebugLog.log(
                        "[CobblemonNML] FAIL-SAFE: Removing player "
                                + player
                                .getGameProfile()
                                .getName()
                                + " from stuck dungeon session."
                );
                QuestRuntimeManager.failAllDungeonQuests(player);
                player.kill();
            }

            /*
             * Do not perform cleanup on this same tick.
             * Let the death/dimension state settle first. On the next server tick, if the dungeon is empty, the normal cleanup
             * path below will run exactly as usual.
             */
            return;
        }
        // DUNGEON EMPTY
        DungeonSlotManager.Slot oldSlot =
                DungeonSlotManager
                        .getCurrentSlot();
        DungeonTier oldTier =
                DungeonSession
                        .getTier();

        /*
         * Capture the slot origin BEFORE switching to the next
         * slot.
         * The broader entity safety sweep uses this origin rather than relying only on this run's exact jigsaw bounds.
         */
        BlockPos oldSlotOrigin =
                DungeonSlotManager
                        .getCurrentOrigin()
                        .immutable();
        DebugLog.log(
                "Dungeon empty. Ending slot "
                        + oldSlot
                        + ( oldTier != null ? " - " + oldTier.getDisplayName() : "" )
        );
        // STOP TIMER
        DungeonTimer.stop();
        // CLEAR OMINOUS VAULT TRACKING
        DungeonVaultManager.clearTrackedVaults();
        // CLEAN TRACKED TRAINERS
        cleanupDungeonTrainers( dungeonLevel );
        // CLEAN QUEST NPCS / QUEST-ITEM MARKERS
        QuestNpcSpawnManager.cleanupAll( dungeonLevel );
        DungeonQuestItemMarkerManager.clear( dungeonLevel );
        // CLEAR TRAINER REWARD BATTLE STATE
        DungeonTrainerBattleEvents.clear();
        // CLEAN TRACKED RAIDS
        DungeonRaidManager.cleanupDungeonRaids( dungeonLevel );
        // CLEAR PENDING ENCOUNTER RETRIES
        DungeonEncounterManager.clearPendingRetries();
        // END ACTIVE SESSION
        UUID oldSessionId = DungeonSession.getSessionId();
        DungeonSession.end();
        ActionBattleTypeEffectRuntime.clearSession(dungeonLevel, oldSessionId);
        // CLOSE OVERWORLD PORTAL
        DungeonPortalManager.deactivateTrackedPortals( server );
        // STOP UNFINISHED GENERATION
        DungeonGenerationQueue.cancelGeneration();
        // GET EXACT BLOCK RESET BOUNDS
        /*
         * Block restoration remains exact and efficient.
         * We do NOT fill the whole slot with bedrock because that would dirty thousands of unnecessary chunks.
         */
        var resetBounds =
                DungeonGenerationQueue
                        .getBoundsForSlot( oldSlot );
        // SLOT-WIDE LOADED ENTITY SAFETY SWEEP
        /*
         * The old cleanup only removed entities inside the exact bounding boxes generated during THIS run.
         * That means an entity living in a forgotten room or corridor from an older generation implementation could
         * survive forever once those old bounds were forgotten.
         * The new sweep covers the whole logical slot safety region, but only inspects entities that are already
         * loaded. Players are always excluded.
         * Specialized trainer / raid cleanup above still runs first so EasyNPC, TBCS and Raid Dens bookkeeping stays
         * correct.
         */
        DungeonGenerationQueue
                .sweepLoadedNonPlayerEntitiesInSlot( dungeonLevel, oldSlotOrigin, "final slot cleanup" );
        // START EXACT BACKGROUND BLOCK RESET
        DungeonResetQueue.start( dungeonLevel, oldSlot, resetBounds );
        // SWITCH TO NEXT SLOT
        DungeonSlotManager
                .switchToOtherSlot();
        DebugLog.log( "Next dungeon will use slot " + DungeonSlotManager .getCurrentSlot() );
        // ALLOW NEXT DUNGEON TO GENERATE
        DungeonDimension.resetGeneratedState();
        // RESET REQUEST HANDLED
        DungeonDimensionEvents.clearResetPending();
        DebugLog.log( "Dungeon session cleanup started successfully." );
    }
    // SERVER STOPPING
    @SubscribeEvent
    public static void onServerStopping( ServerStoppingEvent event ) {
        MinecraftServer server = event.getServer();

        /*
         * Return any portal-selection items that are still being held by the arming manager before the integrated server
         * disappears.
         */
        DungeonPortalSelectionManager
                .returnAllPendingItems( server );

        /*
         * Reset progress is already persisted after each X slice.
         * Pause in-memory jobs so they can resume cleanly next time the world starts.
         */
        DungeonEncounterManager.clearPendingRetries();
        ActionBattleManager.clearAll();
        ActionBattleTypeEffectRuntime.clearAll();
        DungeonTrainerBattleEvents.clear();
        QuestNpcTracker.clear();
        DungeonQuestItemMarkerManager.clearAll();
        DungeonResetQueue.pauseAll();
        DungeonVaultManager.clearTrackedVaults();

        /*
         * Do not carry overworld portal centers into another world created inside the same client JVM.
         */
        DungeonPortalManager.clearTrackedPortalMemory();
        DebugLog.log( "Dungeon cleanup paused for server shutdown." );
    }
    // CLEANUP TRAINERS
    private static void cleanupDungeonTrainers( ServerLevel dungeonLevel ) {
        ActionBattleManager.clearAll();
        int trackedCount =
                DungeonTrainerTracker
                        .size();
        if (trackedCount == 0) {
            DebugLog.log( "[CobblemonNML] No dungeon trainers to clean up." );
            return;
        }
        DebugLog.log( "[CobblemonNML] Cleaning up " + trackedCount + " dungeon trainer(s)." );
        // COPY TRACKED UUIDS
        Set<UUID> trainerUUIDs = new HashSet<>( DungeonTrainerTracker .getTrackedTrainers() );
        // CLEAN EACH TRAINER
        for (UUID trainerUUID : trainerUUIDs) {
            // RUNTIME TBCS TRAINER ID
            String rctTrainerId =
                    DungeonTrainerTracker
                            .getRCTTrainerId( trainerUUID );
            // UNREGISTER RUNTIME TBCS TRAINER
            if (rctTrainerId != null && !rctTrainerId.isBlank()) {
                try {
                    var removedTrainer =
                            TBCS.getInstance()
                                    .getTrainerRegistry()
                                    .unregisterById( rctTrainerId );
                    if (removedTrainer != null) {
                        DebugLog.log( "[CobblemonNML] Unregistered runtime TBCS trainer " + rctTrainerId );
                    }
                } catch (Exception exception) {
                    DebugLog.log( "[CobblemonNML] Failed to unregister runtime TBCS trainer " + rctTrainerId );
                    exception.printStackTrace();
                }
            }
            // ASK EASY NPC TO DESPAWN
            try {
                EasyNPCEntityHandler.despawn( trainerUUID, dungeonLevel, NPCRemovalReason.DESPAWNED );
            } catch (Exception exception) {
                DebugLog.log( "[CobblemonNML] EasyNPC despawn failed for " + trainerUUID );
                exception.printStackTrace();
            }
            // HARD ENTITY REMOVAL FALLBACK
            Entity remainingEntity = dungeonLevel.getEntity( trainerUUID );
            if (remainingEntity != null) {
                remainingEntity.discard();
            }
            // REMOVE TRACKER ENTRY
            DungeonTrainerTracker.untrack( trainerUUID );
        }
        // SAFETY CLEAR
        DungeonTrainerTracker.clear();
        DebugLog.log( "[CobblemonNML] Dungeon trainer cleanup complete." );
    }
}
