package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlEffect;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                playerPokemon.getSpecies().getName(), playerPokemon.getUuid().toString(), playerPokemon.getLevel(), currentHealth(playerPokemon), maxHealth(playerPokemon), session.playerActivePartyIndex(),
                trainerPokemon.getSpecies().getName(), trainerPokemon.getUuid().toString(), trainerPokemon.getLevel(), currentHealth(trainerPokemon), maxHealth(trainerPokemon), session.trainerActivePartyIndex(),
                statusStates(session.battleId(), playerPokemon.getUuid(), currentTick),
                statusStates(session.battleId(), trainerPokemon.getUuid(), currentTick),
                statStages(session.battleId(), playerPokemon.getUuid(), currentTick),
                statStages(session.battleId(), trainerPokemon.getUuid(), currentTick),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), playerPokemon.getUuid())),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), trainerPokemon.getUuid())),
                swapCooldownRemaining, swapCooldownDuration,
                moveHereCooldownRemaining, moveHereCooldownDuration,
                moveState(session.battleId(), playerPokemon, 0, currentTick, remaining, cooldownDuration), moveState(session.battleId(), playerPokemon, 1, currentTick, remaining, cooldownDuration),
                moveState(session.battleId(), playerPokemon, 2, currentTick, remaining, cooldownDuration), moveState(session.battleId(), playerPokemon, 3, currentTick, remaining, cooldownDuration)
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
            long duration = controller.statusDurationTicks(battleId, pokemonUUID, status, currentTick);
            states.add(new ActionBattleHudPayload.StatusState(status.name(), remaining, duration));
        }
        ActionBattleControlController controls = ActionBattleControlController.global();
        ActionBattleControlEffect activeControl = controls.activeEffect(battleId, pokemonUUID, currentTick);
        if (activeControl != null) {
            long controlRemaining = controls.activeRemainingTicks(battleId, pokemonUUID, currentTick);
            long controlDuration = controls.activeDurationTicks(battleId, pokemonUUID, currentTick);
            long shownRemaining = controlRemaining == Long.MAX_VALUE ? 1L : controlRemaining;
            long shownDuration = controlDuration == Long.MAX_VALUE ? 1L : Math.max(1L, controlDuration);
            states.add(new ActionBattleHudPayload.StatusState("CONTROL_" + activeControl.type().name(), shownRemaining, shownDuration));
        }
        ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
        for (ActionBattlePersistentType type : ActionBattlePersistentType.values()) {
            long persistentRemaining = persistent.remainingTicks(battleId, pokemonUUID, type, currentTick);
            if (persistentRemaining <= 0L) continue;
            long persistentDuration = persistent.durationTicks(battleId, pokemonUUID, type);
            if (persistentRemaining == Long.MAX_VALUE || persistentDuration == Long.MAX_VALUE) {
                persistentRemaining = 1L;
                persistentDuration = 1L;
            }
            states.add(new ActionBattleHudPayload.StatusState("PERSISTENT_" + type.name(), persistentRemaining, Math.max(1L, persistentDuration)));
        }
        fireStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        return List.copyOf(states);
    }

    private static java.util.Optional<ActionBattleHudPayload.StatusState> fireStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleTypeEffectController.global().fireView(sessionId, pokemonUUID, currentTick)
                .map(ActionBattleHudSync::toFireStatusState);
    }

    private static ActionBattleHudPayload.StatusState toFireStatusState(ActionBattleTypeEffectState.FireView fire) {
        if (fire.phase() == ActionBattleFireState.Phase.BURN) {
            return new ActionBattleHudPayload.StatusState("TYPE_FIRE_BURN", fire.burnRemainingTicks(), ActionBattleFireRules.BURN_DURATION_TICKS);
        }
        long pressure = Math.clamp(Math.round(fire.pressure()), 1L, Math.round(ActionBattleFireRules.BURN_THRESHOLD));
        String statusId = fire.phase() == ActionBattleFireState.Phase.CINDERS ? "TYPE_FIRE_CINDERS" : "TYPE_FIRE_BUILDUP";
        return new ActionBattleHudPayload.StatusState(statusId, pressure, Math.round(ActionBattleFireRules.BURN_THRESHOLD));
    }


    private static ActionBattleHudPayload.StatStageState statStages(UUID battleId, UUID pokemonUUID, long currentTick) {
        return new ActionBattleHudPayload.StatStageState(
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.ATTACK, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.DEFENSE, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPECIAL_ATTACK, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPECIAL_DEFENSE, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPEED, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.ACCURACY, currentTick)
        );
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

    private static ActionBattleHudPayload.MoveState moveState(UUID battleId, Pokemon pokemon, int slot, long currentTick, long cooldownRemaining, long cooldownDuration) {
        Move move = pokemon.getMoveSet().get(slot);
        if (move == null) return ActionBattleHudPayload.MoveState.empty();
        boolean controlAllowed = ActionBattleControlController.global().canUseMove(battleId, pokemon.getUuid(), move, currentTick);
        return new ActionBattleHudPayload.MoveState(
                move.getName(), move.getType().getName(), FightOrFlightAdapter.currentPp(move), FightOrFlightAdapter.maxPp(move), FightOrFlightAdapter.supports(move) && controlAllowed,
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
