package net.epiac9.cobblemonnml.events.trainer;

import com.gitlab.srcmc.rctapi.api.RCTApi;
import com.gitlab.srcmc.rctapi.api.battle.BattleState;
import com.gitlab.srcmc.rctapi.api.events.EventListener;
import com.gitlab.srcmc.rctapi.api.events.Events;
import com.gitlab.srcmc.rctapi.api.trainer.Trainer;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerPlayer;
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
    private static final String TBCS_RCT_INSTANCE_ID = "tbcs";
    private static final Set<UUID> PROCESSED_BATTLES = new HashSet<>();
    private static boolean listenerRegistered = false;

    @SuppressWarnings("rawtypes")
    private static final EventListener BATTLE_ENDED_LISTENER = event -> {
        if (event == null) {
            return;
        }
        Object value = event.getValue();
        if (value instanceof BattleState battleState) {
            onBattleEnded(battleState);
        }
    };

    private DungeonTrainerBattleEvents() {}

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onServerStarted(ServerStartedEvent event) {
        clear();
        if (listenerRegistered) {
            unregisterListener();
        }
        try {
            RCTApi.getInstance(TBCS_RCT_INSTANCE_ID).getEventContext().register(Events.BATTLE_ENDED, BATTLE_ENDED_LISTENER);
            listenerRegistered = true;
            DebugLog.log("[CobblemonNML] Registered dungeon trainer reward listener for RCT BATTLE_ENDED.");
        } catch (Exception exception) {
            listenerRegistered = false;
            DebugLog.log("[CobblemonNML] Failed to register dungeon trainer reward listener.");
            exception.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        unregisterListener();
        clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void unregisterListener() {
        if (!listenerRegistered) {
            return;
        }
        try {
            RCTApi.getInstance(TBCS_RCT_INSTANCE_ID).getEventContext().unregister(Events.BATTLE_ENDED, BATTLE_ENDED_LISTENER);
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] WARNING: Failed to unregister dungeon trainer reward listener.");
            exception.printStackTrace();
        } finally {
            listenerRegistered = false;
        }
    }

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
        if (battleState.isEndForced()) {
            List<ServerPlayer> players = findPlayerParticipants(battleState);
            if (players.isEmpty() || !PROCESSED_BATTLES.add(battleId)) {
                return;
            }
            DebugLog.log("[CobblemonNML] Normal dungeon trainer surrender confirmed. Battle=" + battleId + ", trainerUUID=" + dungeonTrainer.trainerUUID() + ", preset=" + dungeonTrainer.preset() + ", players=" + players.size());
            for (ServerPlayer player : players) {
                DungeonTrainerBattleResultHandler.handleSurrender(player, dungeonTrainer.trainerUUID());
            }
            return;
        }
        List<ServerPlayer> winningPlayers = findWinningPlayers(battleState);
        if (winningPlayers.isEmpty() || !PROCESSED_BATTLES.add(battleId)) {
            return;
        }
        DebugLog.log("[CobblemonNML] Normal dungeon trainer victory confirmed. Battle=" + battleId + ", trainerUUID=" + dungeonTrainer.trainerUUID() + ", preset=" + dungeonTrainer.preset() + ", winners=" + winningPlayers.size());
        for (ServerPlayer player : winningPlayers) {
            DungeonTrainerBattleResultHandler.handleVictory(player, dungeonTrainer.trainerUUID());
        }
    }

    private static TrackedDungeonTrainer findTrackedDungeonTrainer(BattleState battleState) {
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
                trainerEntity = trainerNpc.getEntity(battleState.getBattle());
            } catch (Exception exception) {
                trainerEntity = trainerNpc.getEntity();
            }
            if (trainerEntity == null) {
                continue;
            }
            UUID trainerUUID = trainerEntity.getUUID();
            String runtimeTrainerId = DungeonTrainerTracker.getRCTTrainerId(trainerUUID);
            if (runtimeTrainerId == null || runtimeTrainerId.isBlank()) {
                continue;
            }
            ResourceLocation preset = DungeonTrainerTracker.getPreset(trainerUUID);
            if (preset != null) {
                return new TrackedDungeonTrainer(trainerUUID, runtimeTrainerId, preset);
            }
        }
        return null;
    }

    private static boolean isNormalTrainerPreset(ResourceLocation preset) {
        if (preset == null) {
            return false;
        }
        String path = preset.getPath();
        return path.contains("easy_npc/preset/humanoid/trainers/") && !path.contains("/quests_givers/");
    }

    private static List<ServerPlayer> findWinningPlayers(BattleState battleState) {
        if (battleState == null) {
            return List.of();
        }
        List<ServerPlayer> winners = new ArrayList<>();
        for (Trainer winner : battleState.getWinners()) {
            if (winner instanceof TrainerPlayer trainerPlayer) {
                ServerPlayer player = trainerPlayer.getPlayer();
                if (player != null) {
                    winners.add(player);
                }
            }
        }
        return List.copyOf(winners);
    }

    private static List<ServerPlayer> findPlayerParticipants(BattleState battleState) {
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
            if (player != null && seenPlayers.add(player.getUUID())) {
                players.add(player);
            }
        }
        return List.copyOf(players);
    }

    public static void clear() {
        PROCESSED_BATTLES.clear();
    }

    private record TrackedDungeonTrainer(UUID trainerUUID, String runtimeTrainerId, ResourceLocation preset) {}
}
