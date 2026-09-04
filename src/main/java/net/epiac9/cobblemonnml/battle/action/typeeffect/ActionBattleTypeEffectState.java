package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricRules;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleFireState fire;
    private ActionBattleIceTracker ice;
    private ActionBattleDrowsyTracker drowsy;
    private ActionBattlePoisonTracker poison;
    private ActionBattleElectricTracker electric;

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

    boolean applyPoisonMove(long currentTick, boolean poisonTyped, int penetratedGain) {
        if (poison == null) poison = new ActionBattlePoisonTracker();
        boolean applied = poison.applyMove(currentTick, poisonTyped, penetratedGain);
        if (poison.isEmpty()) poison = null;
        return applied;
    }

    ActionBattleElectricTracker.ApplyChargeResult addElectricCharge(int amount, long currentTick,
                                                                      boolean electricTyped, boolean hazeActive) {
        if (electric == null) electric = new ActionBattleElectricTracker();
        ActionBattleElectricTracker.ApplyChargeResult result = electric.addCharge(amount, currentTick, electricTyped, hazeActive);
        if (electric.isEmpty()) electric = null;
        return result;
    }

    boolean applyExternalElectricParalysis(long currentTick, boolean electricTyped, boolean hazeActive) {
        if (electric == null) electric = new ActionBattleElectricTracker();
        boolean applied = electric.applyExternalParalysis(currentTick, electricTyped, hazeActive);
        if (electric.isEmpty()) electric = null;
        return applied;
    }

    ActionBattleParalysisState.FlinchContributionResult addElectricParalysisFlinch(int amount, long currentTick) {
        return electric == null ? ActionBattleParalysisState.FlinchContributionResult.IGNORED
                : electric.addParalysisFlinch(amount, currentTick);
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
        if (poison != null) {
            poison.tick(currentTick);
            if (poison.isEmpty()) poison = null;
        }
        if (electric != null) {
            electric.tick(currentTick);
            if (electric.isEmpty()) electric = null;
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

    void suppressPoisonSpecialAttackByHaze() {
        if (poison != null) poison.suppressSpecialAttackByHaze();
    }

    void suppressElectricSpeedByHaze() {
        if (electric != null) electric.suppressParalysisSpeedByHaze();
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

    Optional<PoisonView> poisonView(long currentTick) {
        tick(currentTick);
        return poison != null ? poison.activeState().map(state -> PoisonView.from(state, poison, currentTick))
                : Optional.empty();
    }

    Optional<ElectricChargeView> electricChargeView(long currentTick) {
        if (electric == null) return Optional.empty();
        electric.tick(currentTick);
        return electric.activeCharge().map(state -> new ElectricChargeView(state.charge(), electric.depletionPerTick()));
    }

    Optional<ElectricParalysisView> electricParalysisView(long currentTick) {
        if (electric == null) return Optional.empty();
        electric.tick(currentTick);
        return electric.activeParalysis().map(state -> new ElectricParalysisView(state.remainingTicks(currentTick),
                state.totalDurationTicks(), state.electricTyped(), state.hiddenFlinch(), state.flinchThreshold(),
                state.ownedSpeedStages(currentTick), state.speedSuppressedByHaze()));
    }

    int electricSpeedStages(long currentTick) {
        if (electric != null) electric.tick(currentTick);
        return electric == null ? 0 : electric.activeParalysis().map(state -> state.ownedSpeedStages(currentTick)).orElse(0);
    }

    double modifyOutgoingElectricDamage(boolean electricMove, boolean damaging, double damage, long currentTick) {
        if (electric != null) electric.tick(currentTick);
        if (!electricMove || !damaging || electric == null) return damage;
        boolean activeElectric = electric.activeParalysis().map(state -> state.active(currentTick) && state.electricTyped()).orElse(false);
        return activeElectric ? damage * ActionBattleElectricRules.ELECTRIC_PARALYSIS_DAMAGE_MULTIPLIER : damage;
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

    int poisonSpecialAttackStages(long currentTick) {
        tick(currentTick);
        return poison != null ? poison.activeState().map(ActionBattlePoisonState::ownedSpecialAttackStages).orElse(0) : 0;
    }

    int poisonMoveAccumulationGain() {
        return poison != null ? poison.moveAccumulationGain() : ActionBattlePoisonRules.BASE_MOVE_GAIN;
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

    boolean isToxic(long currentTick) {
        tick(currentTick);
        return poison != null && poison.activeState()
                .map(state -> state.level() == ActionBattlePoisonRules.PoisonLevel.TOXIC).orElse(false);
    }

    boolean isEmpty() { return fire == null && ice == null && drowsy == null && poison == null && electric == null; }
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

    public record PoisonView(
            ActionBattlePoisonRules.PoisonLevel level,
            int accumulation,
            long levelRemainingTicks,
            long toxicRemainingTicks,
            int ownedSpecialAttackStages,
            boolean statSuppressedByHaze,
            int moveAccumulationGain
    ) {
        private static PoisonView from(ActionBattlePoisonState state, ActionBattlePoisonTracker tracker, long currentTick) {
            long remaining = Math.max(0L, state.levelEndTick() - currentTick);
            return new PoisonView(state.level(), state.accumulation(), remaining,
                    state.level() == ActionBattlePoisonRules.PoisonLevel.TOXIC ? remaining : 0L,
                    state.ownedSpecialAttackStages(), state.statSuppressedByHaze(), tracker.moveAccumulationGain());
        }
    }

    public record ElectricChargeView(int charge, int depletionPerTick) {}

    public record ElectricParalysisView(long remainingTicks, long totalDurationTicks,
                                        boolean electricTyped, int hiddenFlinch, int flinchThreshold,
                                        int ownedSpeedStages, boolean speedSuppressedByHaze) {}
}
