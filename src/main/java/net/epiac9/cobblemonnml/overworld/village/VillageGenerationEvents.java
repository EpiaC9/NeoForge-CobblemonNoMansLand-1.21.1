package net.epiac9.cobblemonnml.overworld.village;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class VillageGenerationEvents {
    private VillageGenerationEvents() {
    }
    // SERVER TICK
    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event
    ) {
        MinecraftServer server =
                event.getServer();

        ServerLevel overworld =
                server.overworld();

        VillageGenerationQueue.tick(
                overworld
        );
    }
    // SERVER STOPPING
    @SubscribeEvent
    public static void onServerStopping(
            ServerStoppingEvent event
    ) {
        VillageGenerationQueue.clear(
                event.getServer()
        );
    }
}
