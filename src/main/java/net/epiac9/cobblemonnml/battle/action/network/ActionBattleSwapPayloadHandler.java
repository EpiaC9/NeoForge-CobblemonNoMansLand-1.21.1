package net.epiac9.cobblemonnml.battle.action.network;

import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ActionBattleSwapPayloadHandler {
    private ActionBattleSwapPayloadHandler() {}

    public static void handle(ActionBattleSwapPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        ActionBattleManager.requestPlayerSwap(player);
    }
}
