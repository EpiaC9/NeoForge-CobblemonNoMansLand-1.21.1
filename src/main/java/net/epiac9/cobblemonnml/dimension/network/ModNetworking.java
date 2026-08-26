package net.epiac9.cobblemonnml.dimension.network;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.client.DungeonCleanupToastHandler;

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
