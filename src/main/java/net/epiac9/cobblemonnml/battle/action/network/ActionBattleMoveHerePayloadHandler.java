package net.epiac9.cobblemonnml.battle.action.network;

import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ActionBattleMoveHerePayloadHandler {
    private ActionBattleMoveHerePayloadHandler() {}

    public static void handle(ActionBattleMoveHerePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ActionBattleManager.requestPlayerMoveHere(player, payload.x(), payload.y(), payload.z());
    }
}
