package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleElectricTracker {
    public enum ApplyChargeResult { IGNORED, CHARGE_APPLIED, PARALYSIS_STARTED }

    private ActionBattleElectricState charge;
    private ActionBattleParalysisState paralysis;
    private int depletionPerTick = ActionBattleElectricRules.BASE_DEPLETION_PER_TICK;
    private long cleanResetEndTick = -1L;
    private long lastTick = -1L;

    public ApplyChargeResult addCharge(int amount, long currentTick, boolean electricTyped, boolean hazeActive) {
        if (amount <= 0 || !validTick(currentTick) || currentTick < lastTick) return ApplyChargeResult.IGNORED;
        tick(currentTick);
        cleanResetEndTick = -1L;
        if (paralysis != null && paralysis.active(currentTick)) return ApplyChargeResult.IGNORED;
        if (charge == null) charge = new ActionBattleElectricState(Math.min(amount, 99), currentTick);
        else charge.add(amount, currentTick);
        if (amount >= ActionBattleElectricRules.MAX_CHARGE || charge.charge() >= ActionBattleElectricRules.MAX_CHARGE) {
            charge = null;
            paralysis = new ActionBattleParalysisState(currentTick, electricTyped,
                    ActionBattleParalysisState.ParalysisOrigin.ELECTRIC_CHARGE, hazeActive);
            return ApplyChargeResult.PARALYSIS_STARTED;
        }
        return ApplyChargeResult.CHARGE_APPLIED;
    }

    public boolean applyExternalParalysis(long currentTick, boolean electricTyped, boolean hazeActive) {
        if (!validTick(currentTick) || currentTick < lastTick) return false;
        tick(currentTick);
        if (paralysis != null && paralysis.active(currentTick)) return false;
        charge = null;
        cleanResetEndTick = -1L;
        paralysis = new ActionBattleParalysisState(currentTick, electricTyped,
                ActionBattleParalysisState.ParalysisOrigin.EXTERNAL, hazeActive);
        return true;
    }

    public ActionBattleParalysisState.FlinchContributionResult addParalysisFlinch(int amount, long currentTick) {
        if (!validTick(currentTick) || currentTick < lastTick) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        tick(currentTick);
        return paralysis == null ? ActionBattleParalysisState.FlinchContributionResult.IGNORED
                : paralysis.addFlinch(amount, currentTick);
    }

    public void suppressParalysisSpeedByHaze() {
        if (paralysis != null) paralysis.suppressSpeedByHaze();
    }

    public void removeParalysis(long currentTick) {
        if (!validTick(currentTick)) return;
        tick(currentTick);
        if (paralysis != null) {
            paralysis = null;
            beginCleanWindow(currentTick);
        }
    }

    public void tick(long currentTick) {
        if (!validTick(currentTick) || currentTick < lastTick) return;
        if (charge != null) {
            long ticksUntilEmpty = ((long) charge.charge() + depletionPerTick - 1L) / depletionPerTick;
            long emptyTick = ActionBattleTiming.safeAdd(charge.lastTick(), ticksUntilEmpty);
            charge.depleteTo(currentTick, depletionPerTick);
            if (charge.isEmpty()) {
                charge = null;
                beginCleanWindow(emptyTick);
            }
        }
        if (paralysis != null && !paralysis.active(currentTick)) {
            boolean naturalCharge = paralysis.origin() == ActionBattleParalysisState.ParalysisOrigin.ELECTRIC_CHARGE;
            long expiryTick = paralysis.endTick();
            paralysis = null;
            if (naturalCharge) depletionPerTick = ActionBattleElectricRules.saturatingIncrement(depletionPerTick);
            beginCleanWindow(expiryTick);
        }
        if (paralysis == null && charge == null && cleanResetEndTick >= 0L && currentTick >= cleanResetEndTick) {
            depletionPerTick = ActionBattleElectricRules.BASE_DEPLETION_PER_TICK;
            cleanResetEndTick = -1L;
        }
        lastTick = currentTick;
    }

    public Optional<ActionBattleElectricState> activeCharge() { return Optional.ofNullable(charge); }
    public Optional<ActionBattleParalysisState> activeParalysis() { return Optional.ofNullable(paralysis); }
    public int depletionPerTick() { return depletionPerTick; }
    public long cleanResetEndTick() { return cleanResetEndTick; }
    public boolean isEmpty() { return charge == null && paralysis == null && cleanResetEndTick < 0L
            && depletionPerTick == ActionBattleElectricRules.BASE_DEPLETION_PER_TICK; }

    private void beginCleanWindow(long currentTick) {
        cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleElectricRules.CLEAN_RESET_TICKS);
    }

    private boolean validTick(long currentTick) { return currentTick >= 0L; }
}
