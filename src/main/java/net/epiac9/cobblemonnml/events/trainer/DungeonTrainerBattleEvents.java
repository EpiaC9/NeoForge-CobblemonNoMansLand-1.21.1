package net.epiac9.cobblemonnml.events.trainer;

import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.battle.BattleState;
import com.gitlab.srcmc.rctapi.api.events.EventListener;
import com.gitlab.srcmc.rctapi.api.events.Events;
import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;

import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;

import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonTrainerBattleEvents {
    // TBCS RCT INSTANCE
    private static final String TBCS_RCT_INSTANCE_ID = "tbcs";
    // PROCESSED BATTLES
    /*
     * RCT normally fires BATTLE_ENDED once.
     * Keep a battle UUID guard anyway so duplicate callbacks can never pay a second reward.
     */
    private static final Set<UUID> PROCESSED_BATTLES = new HashSet<>();
    // LISTENER REGISTRATION STATE
    private static boolean listenerRegistered = false;

    @SuppressWarnings("rawtypes")
    private static final EventListener BATTLE_ENDED_LISTENER =
            event -> {
                if (event == null) {
                    return;
                }

                Object value = event.getValue();
                if (!(value instanceof BattleState battleState)) {
                    return;
                }

                onBattleEnded(battleState);
            };

    private DungeonTrainerBattleEvents() {
    }
    // SERVER STARTED
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onServerStarted(ServerStartedEvent event) {
        clear();

        if (listenerRegistered) {
            unregisterListener();
        }

        try {
            RCTApi
                    .getInstance(TBCS_RCT_INSTANCE_ID)
                    .getEventContext()
                    .register(Events.BATTLE_ENDED, BATTLE_ENDED_LISTENER);

            listenerRegistered = true;
            DebugLog.log(
                    "[CobblemonNML] Registered dungeon trainer reward listener "
                            + "for RCT BATTLE_ENDED."
            );
        } catch (Exception exception) {
            listenerRegistered = false;
            DebugLog.log(
                    "[CobblemonNML] Failed to register dungeon trainer reward listener."
            );
            exception.printStackTrace();
        }
    }
    // SERVER STOPPED
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        unregisterListener();
        clear();
    }
    // UNREGISTER LISTENER
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void unregisterListener() {
        if (!listenerRegistered) {
            return;
        }

        try {
            RCTApi
                    .getInstance(TBCS_RCT_INSTANCE_ID)
                    .getEventContext()
                    .unregister(Events.BATTLE_ENDED, BATTLE_ENDED_LISTENER);
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Failed to unregister "
                            + "dungeon trainer reward listener."
            );
            exception.printStackTrace();
        } finally {
            listenerRegistered = false;
        }
    }
    // BATTLE ENDED
    private static void onBattleEnded(BattleState battleState) {
        if (battleState == null || battleState.getBattle() == null) {
            return;
        }

        UUID battleId = battleState.getBattle().getBattleId();
        if (battleId == null || PROCESSED_BATTLES.contains(battleId)) {
            return;
        }

        TrackedDungeonTrainer dungeonTrainer = findTrackedDungeonTrainer(battleState);
        if (dungeonTrainer == null || !isNormalTrainerPreset(dungeonTrainer.preset())) {
            return;
        }

        // FORCED END / SURRENDER
        /*
         * TBCS/RCT marks a surrendered battle as forced-ended.
         * Normal trainer rewards are still never granted on this path.
         */
        if (battleState.isEndForced()) {
            List<ServerPlayer> players = findPlayerParticipants(battleState);
            if (players.isEmpty()) {
                return;
            }

            if (!PROCESSED_BATTLES.add(battleId)) {
                return;
            }

            DebugLog.log(
                    "[CobblemonNML] Normal dungeon trainer surrender confirmed. "
                            + "Battle="
                            + battleId
                            + ", trainerUUID="
                            + dungeonTrainer.trainerUUID()
                            + ", preset="
                            + dungeonTrainer.preset()
                            + ", players="
                            + players.size()
            );

            for (ServerPlayer player : players) {
                openTrainerDialog(
                        player,
                        dungeonTrainer.trainerUUID(),
                        "battle_surrender"
                );
            }
            return;
        }

        // NORMAL VICTORY
        List<ServerPlayer> winningPlayers = findWinningPlayers(battleState);
        if (winningPlayers.isEmpty()) {
            return;
        }

        /*
         * Consume BEFORE rewards/dialog.
         * If anything downstream causes the same event to be observed again,
         * this battle can no longer process twice.
         */
        if (!PROCESSED_BATTLES.add(battleId)) {
            return;
        }

        DebugLog.log(
                "[CobblemonNML] Normal dungeon trainer victory confirmed. "
                        + "Battle="
                        + battleId
                        + ", trainerUUID="
                        + dungeonTrainer.trainerUUID()
                        + ", preset="
                        + dungeonTrainer.preset()
                        + ", winners="
                        + winningPlayers.size()
        );

        DungeonTier tier = DungeonSession.getTier();

        for (ServerPlayer player : winningPlayers) {
            openTrainerDialog(
                    player,
                    dungeonTrainer.trainerUUID(),
                    "battle_won"
            );

            if (tier != null) {
                DungeonTrainerRewardManager.grantNormalTrainerRewards(player, tier);
                QuestRuntimeManager.progressByType(player, "trainer_battle", 1);
            } else {
                DebugLog.log(
                        "[CobblemonNML] Trainer battle won, but no active dungeon tier "
                                + "was available for rewards."
                );
            }
        }
    }

    // FIND TRACKED DUNGEON TRAINER
    private static TrackedDungeonTrainer findTrackedDungeonTrainer(
            BattleState battleState
    ) {
        if (battleState == null) {
            return null;
        }

        List<Trainer> participants = new ArrayList<>();
        participants.addAll(battleState.getParticipants1());
        participants.addAll(battleState.getParticipants2());

        for (Trainer participant : participants) {
            if (!(participant instanceof TrainerNPC trainerNpc)) {
                continue;
            }

            LivingEntity trainerEntity;
            try {
                trainerEntity =
                        trainerNpc.getEntity(
                                battleState.getBattle()
                        );
            } catch (Exception exception) {
                trainerEntity = trainerNpc.getEntity();
            }

            if (trainerEntity == null) {
                continue;
            }

            UUID trainerUUID = trainerEntity.getUUID();
            String runtimeTrainerId =
                    DungeonTrainerTracker.getRCTTrainerId(trainerUUID);

            if (runtimeTrainerId == null || runtimeTrainerId.isBlank()) {
                continue;
            }

            ResourceLocation preset =
                    DungeonTrainerTracker.getPreset(trainerUUID);

            if (preset == null) {
                continue;
            }

            return new TrackedDungeonTrainer(
                    trainerUUID,
                    runtimeTrainerId,
                    preset
            );
        }

        return null;
    }
    // NORMAL TRAINER CHECK
    private static boolean isNormalTrainerPreset(ResourceLocation preset) {
        if (preset == null) {
            return false;
        }

        String path = preset.getPath();

        return path.contains("easy_npc/preset/humanoid/trainers/")
                && !path.contains("/quests_givers/");
    }
    // WINNING PLAYERS
    private static List<ServerPlayer> findWinningPlayers(
            BattleState battleState
    ) {
        if (battleState == null) {
            return List.of();
        }

        List<ServerPlayer> winners = new ArrayList<>();

        for (Trainer winner : battleState.getWinners()) {
            if (!(winner instanceof TrainerPlayer trainerPlayer)) {
                continue;
            }

            ServerPlayer player = trainerPlayer.getPlayer();
            if (player == null) {
                continue;
            }

            winners.add(player);
        }

        return List.copyOf(winners);
    }
    // ALL PLAYER PARTICIPANTS
    private static List<ServerPlayer> findPlayerParticipants(
            BattleState battleState
    ) {
        if (battleState == null) {
            return List.of();
        }

        List<Trainer> participants = new ArrayList<>();
        participants.addAll(battleState.getParticipants1());
        participants.addAll(battleState.getParticipants2());

        List<ServerPlayer> players = new ArrayList<>();
        Set<UUID> seenPlayers = new HashSet<>();

        for (Trainer participant : participants) {
            if (!(participant instanceof TrainerPlayer trainerPlayer)) {
                continue;
            }

            ServerPlayer player = trainerPlayer.getPlayer();
            if (player == null || !seenPlayers.add(player.getUUID())) {
                continue;
            }

            players.add(player);
        }

        return List.copyOf(players);
    }

    // OPEN EASY NPC DIALOG BY LABEL
    private static void openTrainerDialog(
            ServerPlayer player,
            UUID trainerUUID,
            String label
    ) {
        if (player == null
                || trainerUUID == null
                || label == null
                || label.isBlank()) {
            return;
        }

        LivingEntity trainerEntity =
                player
                        .serverLevel()
                        .getEntity(trainerUUID) instanceof LivingEntity livingEntity
                        ? livingEntity
                        : null;

        if (trainerEntity == null) {
            DebugLog.log(
                    "[CobblemonNML] Could not open trainer dialog '"
                            + label
                            + "': trainer entity "
                            + trainerUUID
                            + " was not found."
            );
            return;
        }

        if (!(trainerEntity instanceof DialogDataCapable<?> dialogNpc)) {
            DebugLog.log(
                    "[CobblemonNML] Could not open trainer dialog '"
                            + label
                            + "': trainer does not support EasyNPC dialogs."
            );
            return;
        }

        if (!dialogNpc.hasDialog(label)) {
            DebugLog.log(
                    "[CobblemonNML] Trainer "
                            + trainerUUID
                            + " has no EasyNPC dialog label '"
                            + label
                            + "'."
            );
            return;
        }

        UUID dialogId = dialogNpc.getDialogId(label);
        if (dialogId == null) {
            DebugLog.log(
                    "[CobblemonNML] Trainer dialog label '"
                            + label
                            + "' resolved to no dialog ID."
            );
            return;
        }

        dialogNpc.openDialog(player, dialogId);

        DebugLog.log(
                "[CobblemonNML] Opened trainer dialog '"
                        + label
                        + "' for "
                        + player.getGameProfile().getName()
        );
    }

    // CLEAR SESSION STATE
    public static void clear() {
        PROCESSED_BATTLES.clear();
    }
    // TRACKED TRAINER
    private record TrackedDungeonTrainer(
            UUID trainerUUID,
            String runtimeTrainerId,
            ResourceLocation preset
    ) {
    }
}
