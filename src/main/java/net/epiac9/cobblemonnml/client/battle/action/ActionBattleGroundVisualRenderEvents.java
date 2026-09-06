package net.epiac9.cobblemonnml.client.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundVisualRules;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleGroundVisualRenderEvents {
    private ActionBattleGroundVisualRenderEvents() {}

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemon) || pokemon.getPokemon() == null) return;
        int depth = ActionBattleGroundVisualClientState.depthPercent(pokemon.getPokemon().getUuid());
        double offset = ActionBattleGroundVisualRules.offsetY(pokemon.getBbHeight(), depth);
        if (offset != 0.0D) event.getPoseStack().translate(0.0D, offset, 0.0D);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ActionBattleGroundVisualClientState.clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ActionBattleGroundVisualClientState.clearAll();
    }
}
