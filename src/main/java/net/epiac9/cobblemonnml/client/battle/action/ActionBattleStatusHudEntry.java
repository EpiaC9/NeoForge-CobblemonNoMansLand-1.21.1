package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;

public record ActionBattleStatusHudEntry(ActionBattleHudPayload.StatusState state, ActionBattleStatusVisualRegistry.StatusVisual visual) {
    public float progress() {
        if (state == null || state.totalTicks() <= 0L) return 0.0F;
        return Math.clamp((float) state.remainingTicks() / (float) state.totalTicks(), 0.0F, 1.0F);
    }
}
