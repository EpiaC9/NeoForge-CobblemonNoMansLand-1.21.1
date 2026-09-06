package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleGroundVisualPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ActionBattleGroundVisualPayloadHandler {
    private ActionBattleGroundVisualPayloadHandler() {}

    public static void handle(ActionBattleGroundVisualPayload payload, IPayloadContext context) {
        if (payload == null || payload.operation() == null) return;
        context.enqueueWork(() -> ActionBattleGroundVisualClientState.apply(
                payload.operation().name(), payload.sessionId(), payload.pokemonId(), payload.depthPercent()));
    }
}
