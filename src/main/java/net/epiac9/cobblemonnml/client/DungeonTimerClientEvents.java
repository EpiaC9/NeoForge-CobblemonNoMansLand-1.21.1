package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber( modid = CobblemonNML.MOD_ID, value = Dist.CLIENT )
public final class DungeonTimerClientEvents {
    // HUD LAYER ID
    private static final ResourceLocation DUNGEON_TIMER_LAYER =
            ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "dungeon_timer" );
    // REGISTER HUD
    @SubscribeEvent
    public static void registerGuiLayers( RegisterGuiLayersEvent event ) {
        event.registerAboveAll(
                DUNGEON_TIMER_LAYER,
                ( graphics, deltaTracker ) ->
                        DungeonTimerHud
                                .render( graphics )
        );
    }
}
