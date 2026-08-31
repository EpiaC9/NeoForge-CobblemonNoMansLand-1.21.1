package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;

import java.util.UUID;

public final class ActionBattleFlinchController {
    public static final long COOLDOWN_PENALTY_TICKS = ActionBattleTiming.seconds(2L);

    private ActionBattleFlinchController() {}

    public static boolean apply(ActionBattleSession session, UUID targetPokemonUUID, long currentTick, boolean contact) {
        if (session == null || targetPokemonUUID == null || currentTick < 0L) return false;
        if (!ActionBattleCommandController.cancelPendingOrders(
                session, targetPokemonUUID, ActionBattleCommandController.InterruptReason.CONTROL_EFFECT)) return false;
        if (!ActionBattleCommandController.addCooldownPenalty(session, targetPokemonUUID, currentTick, COOLDOWN_PENALTY_TICKS)) return false;
        ActionBattleProtectController.global().breakForControl(session.battleId(), targetPokemonUUID, currentTick, contact);
        return true;
    }
}
