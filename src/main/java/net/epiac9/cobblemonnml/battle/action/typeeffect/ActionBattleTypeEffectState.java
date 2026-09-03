package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleFireState fire;

    ActionBattleTypeEffectState(UUID pokemonUUID) {
        if (pokemonUUID == null) throw new IllegalArgumentException("Pokemon ID cannot be null.");
        this.pokemonUUID = pokemonUUID;
    }

    boolean applyFirePressure(double amount, long currentTick, boolean fireTyped, boolean hazeActive) {
        if (fire == null) fire = new ActionBattleFireState();
        boolean applied = fire.applyPressure(amount, currentTick, fireTyped, hazeActive);
        if (fire.isEmpty()) fire = null;
        return applied;
    }

    void tick(long currentTick) {
        if (fire == null) return;
        fire.tick(currentTick);
        if (fire.isEmpty()) fire = null;
    }

    void suppressFireBonusByHaze() {
        if (fire != null) fire.suppressFireBonusByHaze();
    }

    Optional<FireView> fireView(long currentTick) {
        tick(currentTick);
        return fire != null ? Optional.of(FireView.from(fire, currentTick)) : Optional.empty();
    }

    int fireAttackStages(long currentTick) {
        tick(currentTick);
        return fire != null ? fire.ownedAttackStages() : 0;
    }

    boolean isBurning(long currentTick) {
        tick(currentTick);
        return fire != null && fire.isBurning();
    }

    boolean isEmpty() { return fire == null; }
    UUID pokemonUUID() { return pokemonUUID; }

    public record FireView(
            ActionBattleFireState.Phase phase,
            double pressure,
            long burnRemainingTicks,
            int ownedAttackStages,
            boolean fireBonusSuppressedByHaze
    ) {
        private static FireView from(ActionBattleFireState state, long currentTick) {
            return new FireView(state.phase(), state.pressure(), state.burnRemainingTicks(currentTick),
                    state.ownedAttackStages(), state.fireBonusSuppressedByHaze());
        }
    }
}
