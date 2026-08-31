package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.concurrent.CompletableFuture;

final class ActionBattlePokemonRuntime {
    private ActionBattlePokemonRuntime() {}

    static ServerPlayer findServerPlayer(ActionBattleSession session) {
        if (session == null) return null;
        var server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(session.playerUUID()) : null;
    }

    static void seedDamageFeedback(ActionBattleSession session, Pokemon pokemon) {
        ActionBattleEffectRuntime.seedDamageFeedback(session, pokemon);
    }

    static void sendOut(ActionBattleSession session, boolean playerSide, LivingEntity source, LivingEntity opponent,
                        ActionBattlePokemonSelection.Selection selected) {
        if (session == null || source == null || opponent == null || selected == null || selected.pokemon() == null) return;
        Pokemon pokemon = selected.pokemon();
        ServerLevel level = (ServerLevel) source.level();
        PokemonEntity existing = pokemon.getEntity();
        if (existing != null && !existing.isRemoved() && existing.level() == level) {
            bindActive(session, playerSide, selected.slot(), pokemon, existing);
            return;
        }
        if (existing != null) recall(pokemon);
        Vec3 sendOutPosition = calculateSendOutPosition(source, opponent);
        CompletableFuture<PokemonEntity> future = ActionBattlePokemonControlGuard.callInternal(() -> pokemon.sendOutWithAnimation(
                source, level, sendOutPosition, null, true, null, entity -> Unit.INSTANCE
        ));
        future.whenComplete((entity, throwable) -> {
            if (throwable != null || entity == null) {
                if (throwable != null) DebugLog.log("[CobblemonNML] Action battle Pokemon send-out failed for battle " + session.battleId(), throwable);
                else DebugLog.log("[CobblemonNML] Action battle Pokemon send-out returned no entity for battle " + session.battleId());
                if (ActionBattleRegistry.isCurrent(session)) ActionBattleManager.invalidateBattle(session.playerUUID());
                return;
            }
            if (!ActionBattleRegistry.isCurrent(session) || session.state() != ActionBattleState.ACTIVE) {
                recall(pokemon);
                return;
            }
            if (!bindActive(session, playerSide, selected.slot(), pokemon, entity)) recall(pokemon);
        });
    }

    static boolean bindActive(ActionBattleSession session, boolean playerSide, int partyIndex, Pokemon pokemon, PokemonEntity entity) {
        if (session == null || pokemon == null || entity == null) return false;
        boolean bound = playerSide
                ? session.bindPlayerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID())
                : session.bindTrainerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID());
        if (!bound) return false;
        if (playerSide) session.setPlayerSendOutPending(false);
        else session.setTrainerSendOutPending(false);
        if (entity.level() instanceof ServerLevel level) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
            if (player != null && player.level() == level && session.battleZone().contains(player.getX(), player.getZ())) {
                ActionBattleMovementController.suppressAutonomousMovementNow(session, entity);
            }
        }
        DebugLog.log("[CobblemonNML] Action battle " + (playerSide ? "player" : "trainer") + " Pokemon active. Battle=" + session.battleId()
                + ", slot=" + partyIndex + ", pokemon=" + pokemon.getUuid() + ", entity=" + entity.getUUID());
        if (session.playerActiveEntityUUID() != null && session.trainerActiveEntityUUID() != null) {
            DebugLog.log("[CobblemonNML] Action battle combatants ready. Battle=" + session.battleId());
        }
        return true;
    }

    static void recall(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            ActionBattlePokemonControlGuard.runInternal(pokemon::recall);
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to recall action battle Pokemon " + pokemon.getUuid(), exception);
        }
    }

    private static Vec3 calculateSendOutPosition(LivingEntity source, LivingEntity opponent) {
        Vec3 delta = opponent.position().subtract(source.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 0.0001D) {
            Vec3 look = source.getLookAngle();
            horizontal = new Vec3(look.x, 0.0D, look.z);
        }
        if (horizontal.lengthSqr() < 0.0001D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        return source.position().add(horizontal.normalize().scale(1.75D)).add(0.0D, 0.15D, 0.0D);
    }
}
