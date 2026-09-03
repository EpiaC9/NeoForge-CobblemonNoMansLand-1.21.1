package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class DungeonTimerClientEvents {
    private DungeonTimerClientEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_BAR, original -> (graphics, deltaTracker) -> {
            if (DungeonTimerHud.shouldReplaceExperienceHud()) DungeonTimerHud.renderExperienceTimer(graphics);
            else original.render(graphics, deltaTracker);
        });
        event.wrapLayer(VanillaGuiLayers.EXPERIENCE_LEVEL, original -> (graphics, deltaTracker) -> {
            if (!DungeonTimerHud.shouldReplaceExperienceHud()) original.render(graphics, deltaTracker);
        });
    }
}
