package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyTracker;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleFireState fire;
    private ActionBattleIceTracker ice;
    private ActionBattleDrowsyTracker drowsy;

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

    boolean applyDrowsy(long currentTick) {
        return applyDrowsy(currentTick, ActionBattleDrowsyTracker.CompletionRoute.SLEEP);
    }

    boolean applyDrowsy(long currentTick, ActionBattleDrowsyTracker.CompletionRoute route) {
        if (drowsy == null) drowsy = new ActionBattleDrowsyTracker();
        boolean applied = drowsy.apply(currentTick, route) == ActionBattleDrowsyTracker.ApplyResult.APPLIED;
        if (drowsy.isEmpty()) drowsy = null;
        return applied;
    }

    boolean completeDrowsy(long currentTick, int durationTicks, ActionBattleDrowsyTracker.CompletionRoute route) {
        return drowsy != null && drowsy.completeNaturally(currentTick, durationTicks, route);
    }

    void tick(long currentTick) { tick(currentTick, false); }

    void tick(long currentTick, boolean sleepCompletionActive) {
        if (fire != null) {
            fire.tick(currentTick);
            if (fire.isEmpty()) fire = null;
        }
        if (ice != null) {
            ice.tick(currentTick);
            if (ice.isEmpty()) ice = null;
        }
        if (drowsy != null) {
            drowsy.tick(currentTick, sleepCompletionActive);
            if (drowsy.isEmpty()) drowsy = null;
        }
    }

    void suppressFireBonusByHaze() {
        if (fire != null) fire.suppressFireBonusByHaze();
    }

    void suppressIceDefenseByHaze() {
        if (ice != null) ice.suppressDefenseContributionByHaze();
    }

    void suppressFairySpecialDefenseByHaze() {
        if (drowsy != null) drowsy.suppressFairySpecialDefenseByHaze();
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

    Optional<DrowsyView> drowsyView(long currentTick) {
        return drowsy != null ? drowsy.activeDrowsy().map(state -> DrowsyView.from(state, currentTick))
                : Optional.empty();
    }

    Optional<ActionBattleDrowsyTracker.CompletionState> fairyCompletionView(long currentTick) {
        tick(currentTick);
        return drowsy != null ? drowsy.completion() : Optional.empty();
    }

    int fireAttackStages(long currentTick) {
        tick(currentTick);
        return fire != null ? fire.ownedAttackStages() : 0;
    }

    int iceDefenseStages(long currentTick) {
        tick(currentTick);
        return ice != null ? ice.activeState().map(ActionBattleIceState::ownedDefenseStages).orElse(0) : 0;
    }

    int fairySpecialDefenseStages(long currentTick) {
        return drowsy != null ? drowsy.ownedSpecialDefenseStages(currentTick) : 0;
    }

    int nextDrowsyDurationTicks() {
        return drowsy != null ? drowsy.nextDrowsyDurationTicks()
                : net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyRules.BASE_DROWSY_DURATION_TICKS;
    }

    ActionBattleDrowsyTracker.CompletionRoute pendingDrowsyCompletionRoute() {
        return drowsy != null ? drowsy.pendingCompletionRoute() : ActionBattleDrowsyTracker.CompletionRoute.SLEEP;
    }

    boolean cancelDrowsyOnRecall(long currentTick) {
        return drowsy != null && drowsy.cancelOnRecall(currentTick);
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

    boolean isEmpty() { return fire == null && ice == null && drowsy == null; }
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

    public record DrowsyView(long remainingTicks, long totalDurationTicks) {
        private static DrowsyView from(ActionBattleDrowsyState state, long currentTick) {
            return new DrowsyView(state.remainingTicks(currentTick), state.totalDurationTicks());
        }
    }
}
