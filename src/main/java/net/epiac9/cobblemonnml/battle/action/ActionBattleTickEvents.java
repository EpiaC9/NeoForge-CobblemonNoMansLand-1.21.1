package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ActionBattleTickEvents {
    private ActionBattleTickEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ActionBattleManager.tickPlayerMovement(player);
        }
    }
}
