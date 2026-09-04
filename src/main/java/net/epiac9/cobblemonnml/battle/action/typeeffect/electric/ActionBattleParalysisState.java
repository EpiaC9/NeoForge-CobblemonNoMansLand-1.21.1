package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleParalysisState {
    public enum ParalysisOrigin { ELECTRIC_CHARGE, EXTERNAL }
    public enum FlinchContributionResult { IGNORED, ACCUMULATED, FLINCH_TRIGGERED }

    private final long startTick;
    private final long endTick;
    private final boolean electricTyped;
    private final ParalysisOrigin origin;
    private boolean speedSuppressedByHaze;
    private int hiddenFlinch;

    public ActionBattleParalysisState(long currentTick, boolean electricTyped,
                                      ParalysisOrigin origin, boolean hazeActive) {
        if (currentTick < 0L || origin == null) throw new IllegalArgumentException("Invalid Paralysis state.");
        this.startTick = currentTick;
        this.endTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleElectricRules.PARALYSIS_DURATION_TICKS);
        this.electricTyped = electricTyped;
        this.origin = origin;
        this.speedSuppressedByHaze = hazeActive;
    }

    public boolean active(long currentTick) { return remainingTicks(currentTick) > 0L; }
    public long startTick() { return startTick; }
    public long endTick() { return endTick; }
    public long totalDurationTicks() { return ActionBattleElectricRules.PARALYSIS_DURATION_TICKS; }
    public long remainingTicks(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("Tick cannot be negative.");
        return Math.max(0L, endTick - currentTick);
    }
    public boolean electricTyped() { return electricTyped; }
    public ParalysisOrigin origin() { return origin; }
    public int hiddenFlinch() { return hiddenFlinch; }
    public int flinchThreshold() {
        return electricTyped ? ActionBattleElectricRules.ELECTRIC_FLINCH_THRESHOLD
                : ActionBattleElectricRules.NORMAL_FLINCH_THRESHOLD;
    }
    public int ownedSpeedStages(long currentTick) {
        return active(currentTick) && !speedSuppressedByHaze ? (electricTyped
                ? ActionBattleElectricRules.ELECTRIC_PARALYSIS_SPEED_STAGE
                : ActionBattleElectricRules.NORMAL_PARALYSIS_SPEED_STAGE) : 0;
    }
    public boolean speedSuppressedByHaze() { return speedSuppressedByHaze; }
    public void suppressSpeedByHaze() { speedSuppressedByHaze = true; }

    public FlinchContributionResult addFlinch(int amount, long currentTick) {
        if (amount <= 0 || !active(currentTick)) return FlinchContributionResult.IGNORED;
        long total = (long) hiddenFlinch + amount;
        if (total >= flinchThreshold()) {
            hiddenFlinch = 0;
            return FlinchContributionResult.FLINCH_TRIGGERED;
        }
        hiddenFlinch = (int) Math.min(Integer.MAX_VALUE, total);
        return FlinchContributionResult.ACCUMULATED;
    }
}