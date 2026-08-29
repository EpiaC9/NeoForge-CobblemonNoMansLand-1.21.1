package net.epiac9.cobblemonnml.dimension.network;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.client.DungeonCleanupToastHandler;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.client.battle.action.ActionBattleHudPayloadHandler;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMoveHerePayload;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMoveHerePayloadHandler;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMovePayload;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMovePayloadHandler;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleSwapPayload;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleSwapPayloadHandler;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ModNetworking {
    // REGISTER PAYLOADS
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar( "1" );
        registrar.playToServer(
                ActionBattleMoveHerePayload.TYPE,
                ActionBattleMoveHerePayload.STREAM_CODEC,
                ActionBattleMoveHerePayloadHandler::handle
        );
        registrar.playToServer(
                ActionBattleMovePayload.TYPE,
                ActionBattleMovePayload.STREAM_CODEC,
                ActionBattleMovePayloadHandler::handle
        );
        registrar.playToServer(
                ActionBattleSwapPayload.TYPE,
                ActionBattleSwapPayload.STREAM_CODEC,
                ActionBattleSwapPayloadHandler::handle
        );
        registrar.playToClient(
                ActionBattleHudPayload.TYPE,
                ActionBattleHudPayload.STREAM_CODEC,
                ActionBattleHudPayloadHandler::handle
        );
        // DUNGEON CLEANUP TOAST
        registrar.playToClient(
                DungeonCleanupToastPayload.TYPE,
                DungeonCleanupToastPayload.STREAM_CODEC,
                DungeonCleanupToastHandler::handle
        );
        // CUSTOM DUNGEON TIMER
        registrar.playToClient(
                DungeonTimerPayload.TYPE,
                DungeonTimerPayload.STREAM_CODEC,
                DungeonTimerPayloadHandler::handle
        );
    }
}
