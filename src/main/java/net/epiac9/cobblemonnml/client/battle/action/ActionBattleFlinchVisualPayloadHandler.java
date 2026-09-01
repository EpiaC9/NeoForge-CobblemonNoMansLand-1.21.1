package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleFlinchVisualPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ActionBattleFlinchVisualPayloadHandler {
    private ActionBattleFlinchVisualPayloadHandler() {}

    public static void handle(ActionBattleFlinchVisualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ActionBattleFlinchVisualClientState.trigger(payload));
    }
}
