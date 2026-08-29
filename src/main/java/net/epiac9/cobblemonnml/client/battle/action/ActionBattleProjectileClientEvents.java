package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModEntities;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleProjectileClientEvents {
    private ActionBattleProjectileClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ACTION_BATTLE_PROJECTILE.get(), NoopRenderer::new);
    }
}
