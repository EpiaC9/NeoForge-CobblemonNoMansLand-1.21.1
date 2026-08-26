package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber( modid = CobblemonNML.MOD_ID, value = Dist.CLIENT )
public final class DungeonPortalClientEvents {
    // BLOCK COLOURS
    @SubscribeEvent
    public static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register(
                ( state, level, pos, tintIndex ) -> {

                    /*
                     * Both the portal base and the activation overlay use tintindex=0 now, so the giant
                     * animated overlay receives the exact same Theme color as the portal underneath it.
                     */
                    if (tintIndex != 0) {
                        return 0xFFFFFFFF;
                    }
                    int themeIndex = state.getValue( DungeonPortalVisualState.THEME );
                    DungeonTheme theme =
                            DungeonPortalVisualState
                                    .themeFromIndex( themeIndex );
                    if (theme == null) {
                        return 0xFFFFFFFF;
                    }
                    return theme.getPortalColor();
                },
                ModBlocks
                        .DUNGEON_PORTAL_CORE
                        .get(),
                ModBlocks
                        .DUNGEON_PORTAL
                        .get()
        );
    }
    // BLOCK ITEM COLOURS
    @SubscribeEvent
    public static void registerItemColorHandlers( RegisterColorHandlersEvent.Item event ) {
        event.register(
                ( stack, tintIndex ) ->
                        0xFFFFFFFF,
                ModBlocks
                        .DUNGEON_PORTAL_CORE_ITEM
                        .get(),
                ModBlocks
                        .DUNGEON_PORTAL_ITEM
                        .get()
        );
    }
}
