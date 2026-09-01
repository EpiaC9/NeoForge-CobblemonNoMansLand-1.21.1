package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleFlinchVisualClientEvents {
    private ActionBattleFlinchVisualClientEvents() {}

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        ActionBattleFlinchVisualClientState.tick();
    }
}
