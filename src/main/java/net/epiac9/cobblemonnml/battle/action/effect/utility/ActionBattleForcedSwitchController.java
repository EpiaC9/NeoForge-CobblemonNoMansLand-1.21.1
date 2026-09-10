package net.epiac9.cobblemonnml.battle.action.effect.utility;

import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;

public final class ActionBattleForcedSwitchController {
    private ActionBattleForcedSwitchController() {}

    public static boolean applyToPlayer(ServerPlayer player) {
        return ActionBattleManager.forcePlayerSwitch(player);
    }

    public static boolean applyToTrainer(ActionBattleSession session, ServerLevel level) {
        return ActionBattleManager.forceTrainerSwitch(session, level);
    }
}
