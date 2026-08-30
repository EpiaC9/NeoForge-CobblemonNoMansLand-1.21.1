package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;

public final class ActionBattleHudSync {
    private ActionBattleHudSync() {}

    public static void send(ServerPlayer player, ActionBattleSession session, Pokemon playerPokemon, Pokemon trainerPokemon) {
        if (player == null || session == null || playerPokemon == null || trainerPokemon == null || session.state() != ActionBattleState.ACTIVE) return;
        long currentTick = player.serverLevel().getGameTime();
        long cooldownEnd = session.pokemonMoveCooldownEndTick(playerPokemon.getUuid());
        long remaining = Math.max(0L, cooldownEnd - currentTick);
        long cooldownDuration = session.pokemonMoveCooldownDurationTicks(playerPokemon.getUuid());
        long swapCooldownRemaining = Math.max(0L, session.playerSwapCooldownEndTick() - currentTick);
        long swapCooldownDuration = session.playerSwapCooldownDurationTicks();
        long moveHereCooldownRemaining = Math.max(0L, session.pokemonMovementCommandCooldownEndTick(playerPokemon.getUuid()) - currentTick);
        long moveHereCooldownDuration = session.pokemonMovementCommandCooldownDurationTicks(playerPokemon.getUuid());
        ActionBattleHudPayload payload = new ActionBattleHudPayload(
                true,
                playerPokemon.getSpecies().getName(), playerPokemon.getLevel(), currentHealth(playerPokemon), maxHealth(playerPokemon), session.playerActivePartyIndex(),
                trainerPokemon.getSpecies().getName(), trainerPokemon.getLevel(), currentHealth(trainerPokemon), maxHealth(trainerPokemon), session.trainerActivePartyIndex(),
                swapCooldownRemaining, swapCooldownDuration,
                moveHereCooldownRemaining, moveHereCooldownDuration,
                moveState(playerPokemon, 0, remaining, cooldownDuration), moveState(playerPokemon, 1, remaining, cooldownDuration),
                moveState(playerPokemon, 2, remaining, cooldownDuration), moveState(playerPokemon, 3, remaining, cooldownDuration)
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void hide(ServerPlayer player) {
        if (player != null) PacketDistributor.sendToPlayer(player, ActionBattleHudPayload.hidden());
    }

    private static ActionBattleHudPayload.MoveState moveState(Pokemon pokemon, int slot, long cooldownRemaining, long cooldownDuration) {
        Move move = pokemon.getMoveSet().get(slot);
        if (move == null) return ActionBattleHudPayload.MoveState.empty();
        return new ActionBattleHudPayload.MoveState(
                move.getName(), move.getType().getName(), FightOrFlightAdapter.currentPp(move), FightOrFlightAdapter.maxPp(move), FightOrFlightAdapter.supports(move),
                cooldownRemaining, cooldownDuration
        );
    }

    private static int currentHealth(Pokemon pokemon) { return invokeIntGetter(pokemon, "getCurrentHealth", 0); }
    private static int maxHealth(Pokemon pokemon) { return Math.max(1, invokeIntGetter(pokemon, "getMaxHealth", Math.max(1, currentHealth(pokemon)))); }

    private static int invokeIntGetter(Object target, String methodName, int fallback) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException exception) {
            return fallback;
        }
    }
}
