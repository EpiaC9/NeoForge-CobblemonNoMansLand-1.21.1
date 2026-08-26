package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.network.DungeonCleanupToastPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class DungeonCleanupToastHandler {
    public static void handle( DungeonCleanupToastPayload payload, IPayloadContext context ) {
        context.enqueueWork(
                () -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    switch (payload.status()) {
                        // CLEANUP STARTED
                        case "STARTED" ->
                                SystemToast.add(
                                        minecraft.getToasts(),
                                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                        Component.literal( "Dungeon Cleanup Started" ),
                                        Component.literal( "Slot " + payload.slotName() + " is being reset." )
                                );
                        // CLEANUP FINISHED
                        case "FINISHED" ->
                                SystemToast.add(
                                        minecraft.getToasts(),
                                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                        Component.literal( "Dungeon Cleanup Complete" ),
                                        Component.literal( "Slot " + payload.slotName() + " is ready." )
                                );
                        // ALL SLOTS BUSY
                        case "ALL_BUSY" ->
                                SystemToast.add(
                                        minecraft.getToasts(),
                                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                        Component.literal( "All Dungeons Are Being Cleaned" ),
                                        Component.literal( "Please wait for a dungeon slot." )
                                );
                        default -> {}
                    }
                }
        );
    }
}
