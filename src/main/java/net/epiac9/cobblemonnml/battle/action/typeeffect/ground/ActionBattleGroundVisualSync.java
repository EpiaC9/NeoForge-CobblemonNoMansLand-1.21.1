package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleGroundVisualPayload;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ActionBattleGroundVisualSync {
    private ActionBattleGroundVisualSync() {}

    public static void update(PokemonEntity entity, UUID sessionId, int depthPercent) {
        if (entity == null || entity.isRemoved() || entity.level().isClientSide() || sessionId == null
                || entity.getPokemon() == null || !ActionBattleGroundVisualRules.isSupportedDepth(depthPercent)) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                ActionBattleGroundVisualPayload.update(sessionId, entity.getPokemon().getUuid(), depthPercent));
    }

    public static void clearSession(UUID sessionId) {
        if (sessionId != null) broadcast(ActionBattleGroundVisualPayload.clearSession(sessionId));
    }

    public static void clearAll() { broadcast(ActionBattleGroundVisualPayload.clearAll()); }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof PokemonEntity pokemon) || pokemon.getPokemon() == null) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID());
        if (session == null) return;
        int depth = ActionBattleTypeEffectController.global().groundView(
                session.dungeonSessionId(), pokemon.getPokemon().getUuid(), pokemon.level().getGameTime())
                .map(ActionBattleGroundState.View::depthPercent).orElse(0);
        PacketDistributor.sendToPlayer(player,
                ActionBattleGroundVisualPayload.update(session.dungeonSessionId(), pokemon.getPokemon().getUuid(), depth));
    }

    private static void broadcast(ActionBattleGroundVisualPayload payload) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, payload);
    }
}
