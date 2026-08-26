package net.epiac9.cobblemonnml.dimension.network;

import net.epiac9.cobblemonnml.client.DungeonTimerClientState;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonTimerPayloadHandler {
    // HANDLE TIMER PAYLOAD
    public static void handle( DungeonTimerPayload payload, IPayloadContext context ) {
        context.enqueueWork( () -> DungeonTimerClientState.apply( payload ) );
    }
}
