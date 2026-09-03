package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceTracker;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleFireState fire;
    private ActionBattleIceTracker ice;

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

    boolean applyIceApplication(long currentTick, boolean iceTyped, boolean hazeActive) {
        if (ice == null) ice = new ActionBattleIceTracker();
        boolean applied = ice.applyApplication(currentTick, iceTyped, hazeActive);
        if (ice.isEmpty()) ice = null;
        return applied;
    }

    void tick(long currentTick) {
        if (fire != null) {
            fire.tick(currentTick);
            if (fire.isEmpty()) fire = null;
        }
        if (ice != null) {
            ice.tick(currentTick);
            if (ice.isEmpty()) ice = null;
        }
    }

    void suppressFireBonusByHaze() {
        if (fire != null) fire.suppressFireBonusByHaze();
    }

    void suppressIceDefenseByHaze() {
        if (ice != null) ice.suppressDefenseContributionByHaze();
    }

    Optional<FireView> fireView(long currentTick) {
        tick(currentTick);
        return fire != null ? Optional.of(FireView.from(fire, currentTick)) : Optional.empty();
    }

    Optional<IceView> iceView(long currentTick) {
        tick(currentTick);
        return ice != null ? ice.activeState().map(state -> IceView.from(state, ice.activeHitsRequired(), currentTick))
                : Optional.empty();
    }

    int fireAttackStages(long currentTick) {
        tick(currentTick);
        return fire != null ? fire.ownedAttackStages() : 0;
    }

    int iceDefenseStages(long currentTick) {
        tick(currentTick);
        return ice != null ? ice.activeState().map(ActionBattleIceState::ownedDefenseStages).orElse(0) : 0;
    }

    int iceHitsRequired(long currentTick) {
        tick(currentTick);
        return ice != null ? ice.hitsRequired() : 3;
    }

    boolean isBurning(long currentTick) {
        tick(currentTick);
        return fire != null && fire.isBurning();
    }

    boolean isFrostbitten(long currentTick) {
        tick(currentTick);
        return ice != null && ice.activeState().map(ActionBattleIceState::isFrostbitten).orElse(false);
    }

    boolean isEmpty() { return fire == null && ice == null; }
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

    public record IceView(
            ActionBattleIceState.Phase phase,
            int currentHits,
            int hitsRequired,
            long frostbiteRemainingTicks,
            int ownedDefenseStages,
            boolean defenseContributionSuppressedByHaze
    ) {
        private static IceView from(ActionBattleIceState state, int hitsRequired, long currentTick) {
            return new IceView(state.phase(), state.currentHits(), hitsRequired, state.frostbiteRemainingTicks(currentTick),
                    state.ownedDefenseStages(), state.defenseContributionSuppressedByHaze());
        }
    }
}
