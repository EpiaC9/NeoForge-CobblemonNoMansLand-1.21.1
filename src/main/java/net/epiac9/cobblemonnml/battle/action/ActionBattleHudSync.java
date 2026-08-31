package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActionBattleHudSync {
    private static final long POISON_TOXIC_HUD_DURATION_TICKS = 360L;

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
                playerPokemon.getSpecies().getName(), playerPokemon.getUuid().toString(), playerPokemon.getLevel(), currentHealth(playerPokemon), maxHealth(playerPokemon), session.playerActivePartyIndex(),
                trainerPokemon.getSpecies().getName(), trainerPokemon.getUuid().toString(), trainerPokemon.getLevel(), currentHealth(trainerPokemon), maxHealth(trainerPokemon), session.trainerActivePartyIndex(),
                statusStates(session.battleId(), playerPokemon.getUuid(), currentTick),
                statusStates(session.battleId(), trainerPokemon.getUuid(), currentTick),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), playerPokemon.getUuid())),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), trainerPokemon.getUuid())),
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

    private static List<ActionBattleHudPayload.StatusState> statusStates(UUID battleId, UUID pokemonUUID, long currentTick) {
        ActionBattleEffectController controller = ActionBattleEffectController.global();
        ActionBattleProtectController protect = ActionBattleProtectController.global();
        List<ActionBattleHudPayload.StatusState> states = new ArrayList<>();
        int shieldLevel = protect.deterioratingShieldLevel(battleId, pokemonUUID);
        long shieldRemaining = protect.deterioratingShieldRemainingTicks(battleId, pokemonUUID);
        if (shieldLevel > 0 && shieldRemaining > 0L) {
            long shieldDuration = shieldLevel * 200L;
            states.add(new ActionBattleHudPayload.StatusState("DETERIORATING_SHIELD_" + shieldLevel, shieldRemaining, shieldDuration));
        }
        for (ActionBattleStatus status : ActionBattleStatus.values()) {
            long remaining = controller.statusRemainingTicks(battleId, pokemonUUID, status, currentTick);
            if (remaining <= 0L) continue;
            long duration = isPoisonToxic(status) ? POISON_TOXIC_HUD_DURATION_TICKS : controller.statusDurationTicks(status);
            states.add(new ActionBattleHudPayload.StatusState(status.name(), remaining, duration));
        }
        return List.copyOf(states);
    }

    private static boolean isPoisonToxic(ActionBattleStatus status) {
        return status == ActionBattleStatus.POISON || status == ActionBattleStatus.TOXIC_1 || status == ActionBattleStatus.TOXIC_2 || status == ActionBattleStatus.TOXIC_3;
    }

    private static List<ActionBattleHudPayload.DamageState> damageStates(List<ActionBattleDamageFeedbackEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<ActionBattleHudPayload.DamageState> states = new ArrayList<>(events.size());
        for (ActionBattleDamageFeedbackEvent event : events) {
            if (event == null || event.damage() <= 0) continue;
            states.add(new ActionBattleHudPayload.DamageState(event.eventId(), event.damage(), event.category().name()));
        }
        return List.copyOf(states);
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
