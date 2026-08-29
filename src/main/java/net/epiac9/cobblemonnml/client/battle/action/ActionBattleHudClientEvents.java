package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleHudClientEvents {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_hud");
    private ActionBattleHudClientEvents() {}
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, (graphics, deltaTracker) -> ActionBattleHud.render(graphics));
    }
}
