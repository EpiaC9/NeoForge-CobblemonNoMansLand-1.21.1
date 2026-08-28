package net.epiac9.cobblemonnml.events.trainer;

import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public final class DungeonTrainerBattleResultHandler {
    private DungeonTrainerBattleResultHandler() {}

    public static void handleVictory(ServerPlayer player, UUID trainerUUID) {
        if (player == null || trainerUUID == null) {
            return;
        }
        openTrainerDialog(player, trainerUUID, "battle_won");
        DungeonTier tier = DungeonSession.getTier();
        if (tier == null) {
            DebugLog.log("[CobblemonNML] Trainer battle won, but no active dungeon tier was available for rewards.");
            return;
        }
        DungeonTrainerRewardManager.grantNormalTrainerRewards(player, tier);
        QuestRuntimeManager.progressByType(player, "trainer_battle", 1);
    }

    public static void handleSurrender(ServerPlayer player, UUID trainerUUID) {
        if (player == null || trainerUUID == null) {
            return;
        }
        openTrainerDialog(player, trainerUUID, "battle_surrender");
    }

    public static void handleLoss(ServerPlayer player, UUID trainerUUID) {
        // Normal dungeon trainer losses currently have no reward/dialog side effect here.
        // The action battle system still routes through this method so future loss handling stays centralized.
    }

    private static void openTrainerDialog(ServerPlayer player, UUID trainerUUID, String label) {
        if (player == null || trainerUUID == null || label == null || label.isBlank()) {
            return;
        }
        LivingEntity trainerEntity = player.serverLevel().getEntity(trainerUUID) instanceof LivingEntity livingEntity ? livingEntity : null;
        if (trainerEntity == null) {
            DebugLog.log("[CobblemonNML] Could not open trainer dialog '" + label + "': trainer entity " + trainerUUID + " was not found.");
            return;
        }
        if (!(trainerEntity instanceof DialogDataCapable<?> dialogNpc)) {
            DebugLog.log("[CobblemonNML] Could not open trainer dialog '" + label + "': trainer does not support EasyNPC dialogs.");
            return;
        }
        if (!dialogNpc.hasDialog(label)) {
            DebugLog.log("[CobblemonNML] Trainer " + trainerUUID + " has no EasyNPC dialog label '" + label + "'.");
            return;
        }
        UUID dialogId = dialogNpc.getDialogId(label);
        if (dialogId == null) {
            DebugLog.log("[CobblemonNML] Trainer dialog label '" + label + "' resolved to no dialog ID.");
            return;
        }
        dialogNpc.openDialog(player, dialogId);
        DebugLog.log("[CobblemonNML] Opened trainer dialog '" + label + "' for " + player.getGameProfile().getName());
    }
}
