package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;

public final class ActionBattleNativePartyObscurity {
    private ActionBattleNativePartyObscurity() {}

    public static int stage() {
        if (!ActionBattleHudClientState.isVisible()) return 0;
        ActionBattleHudPayload payload = ActionBattleHudClientState.get();
        return payload != null ? payload.playerObscurity().stage() : 0;
    }
}
