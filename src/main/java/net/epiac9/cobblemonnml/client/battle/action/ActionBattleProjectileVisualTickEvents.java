package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleProjectileVisualTickEvents {
    private ActionBattleProjectileVisualTickEvents() {}

    @SubscribeEvent
    public static void afterClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (entity instanceof ActionBattleProjectileEntity projectile) {
                ActionBattleProjectileNativeVisuals.tryAttach(projectile);
            }
        }
    }
}
