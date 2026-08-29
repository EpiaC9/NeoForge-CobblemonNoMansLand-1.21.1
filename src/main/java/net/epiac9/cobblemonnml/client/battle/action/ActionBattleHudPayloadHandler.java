package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ActionBattleHudPayloadHandler {
    private ActionBattleHudPayloadHandler() {}
    public static void handle(ActionBattleHudPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ActionBattleHudClientState.apply(payload));
    }
}
