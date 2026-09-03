package net.epiac9.cobblemonnml.battle.action.protect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleProtectController {
    private static final ActionBattleProtectController GLOBAL = new ActionBattleProtectController();
    private static final long STANCE_TICKS = 40L;
    private final Map<Key, ActionBattleDeterioratingShieldState> deterioration = new HashMap<>();
    private final Map<Key, ActionBattleProtectStance> stances = new HashMap<>();

    public static ActionBattleProtectController global() { return GLOBAL; }

    public ActionBattleProtectStance startBalefulBunker(UUID battleId, UUID pokemonUUID, long currentTick) {
        if (battleId == null || pokemonUUID == null || currentTick < 0L) return null;
        Key key = new Key(battleId, pokemonUUID);
        ActionBattleDeterioratingShieldState shield = deterioration.computeIfAbsent(key, ignored -> new ActionBattleDeterioratingShieldState());
        int level = shield.increaseLevel();
        ActionBattleProtectStance stance = new ActionBattleProtectStance(
                battleId, pokemonUUID, currentTick, currentTick + STANCE_TICKS, level,
                shield.damageTakenMultiplier(), shield.timedEffectDurationMultiplier()
        );
        stances.put(key, stance);
        return stance;
    }

    public ActionBattleProtectStance activeStance(UUID battleId, UUID pokemonUUID, long currentTick) {
        if (battleId == null || pokemonUUID == null) return null;
        Key key = new Key(battleId, pokemonUUID);
        ActionBattleProtectStance stance = stances.get(key);
        if (stance == null) return null;
        if (!stance.isActive(currentTick)) {
            stances.remove(key);
            return null;
        }
        return stance;
    }

    public void breakStance(UUID battleId, UUID pokemonUUID) {
        if (battleId != null && pokemonUUID != null) stances.remove(new Key(battleId, pokemonUUID));
    }

    public void onPokemonRecalled(UUID battleId, UUID pokemonUUID) {
        breakStance(battleId, pokemonUUID);
    }

    public void onSuccessfulNonProtectMove(UUID battleId, UUID pokemonUUID) {
        ActionBattleDeterioratingShieldState state = state(battleId, pokemonUUID);
        if (state == null) return;
        state.reduceForNonProtectMove();
        removeIfInactive(new Key(battleId, pokemonUUID), state);
    }

    public void tickPokemon(UUID battleId, UUID pokemonUUID, boolean recalled) {
        ActionBattleDeterioratingShieldState state = state(battleId, pokemonUUID);
        if (state == null) return;
        state.tick(recalled);
        removeIfInactive(new Key(battleId, pokemonUUID), state);
    }

    public int deterioratingShieldLevel(UUID battleId, UUID pokemonUUID) {
        ActionBattleDeterioratingShieldState state = state(battleId, pokemonUUID);
        return state != null ? state.level() : 0;
    }

    public long deterioratingShieldRemainingTicks(UUID battleId, UUID pokemonUUID) {
        ActionBattleDeterioratingShieldState state = state(battleId, pokemonUUID);
        return state != null ? state.remainingTicks() : 0L;
    }

    public float deterioratingShieldDurationMultiplier(UUID battleId, UUID pokemonUUID) {
        ActionBattleDeterioratingShieldState state = state(battleId, pokemonUUID);
        return state != null ? state.timedEffectDurationMultiplier() : 1.0F;
    }


    public void tickBattle(UUID battleId, Set<UUID> activePokemonUUIDs) {
        if (battleId == null) return;
        Set<UUID> active = activePokemonUUIDs != null ? activePokemonUUIDs : Set.of();
        for (Key key : java.util.List.copyOf(deterioration.keySet())) {
            if (!battleId.equals(key.battleId())) continue;
            ActionBattleDeterioratingShieldState state = deterioration.get(key);
            if (state == null) continue;
            state.tick(!active.contains(key.pokemonUUID()));
            removeIfInactive(key, state);
        }
    }


    public int modifyFinalDamage(UUID battleId, UUID pokemonUUID, long currentTick, int finalDamage) {
        if (finalDamage <= 0) return Math.max(0, finalDamage);
        ActionBattleProtectStance stance = activeStance(battleId, pokemonUUID, currentTick);
        if (stance == null) return finalDamage;
        return Math.max(0, Math.round(finalDamage * stance.damageTakenMultiplier()));
    }

    public EffectInterception interceptTimedEffect(UUID battleId, UUID pokemonUUID, long currentTick, String effectId, int normalDurationTicks) {
        if (normalDurationTicks <= 0) return new EffectInterception(false, 0);
        ActionBattleProtectStance stance = activeStance(battleId, pokemonUUID, currentTick);
        if (stance == null) return new EffectInterception(true, normalDurationTicks);
        float multiplier = stance.timedEffectDurationMultiplier();
        if (multiplier <= 0.0F) return new EffectInterception(false, 0);
        int duration = Math.max(1, Math.round(normalDurationTicks * multiplier));
        return new EffectInterception(true, duration);
    }

    public record EffectInterception(boolean allowed, int durationTicks) {}

    public ControlBreakResult breakForControl(UUID battleId, UUID pokemonUUID, long currentTick, boolean contact) {
        ActionBattleProtectStance stance = activeStance(battleId, pokemonUUID, currentTick);
        if (stance == null) return new ControlBreakResult(false);
        breakStance(battleId, pokemonUUID);
        return new ControlBreakResult(true);
    }

    public record ControlBreakResult(boolean protectedHit) {}

    public void clearBattle(UUID battleId) {
        if (battleId == null) return;
        deterioration.keySet().removeIf(key -> battleId.equals(key.battleId()));
        stances.keySet().removeIf(key -> battleId.equals(key.battleId()));
    }

    private ActionBattleDeterioratingShieldState state(UUID battleId, UUID pokemonUUID) {
        return battleId != null && pokemonUUID != null ? deterioration.get(new Key(battleId, pokemonUUID)) : null;
    }

    private void removeIfInactive(Key key, ActionBattleDeterioratingShieldState state) {
        if (!state.isActive()) deterioration.remove(key);
    }

    private record Key(UUID battleId, UUID pokemonUUID) {}
}
