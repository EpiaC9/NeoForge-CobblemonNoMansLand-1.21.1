package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattlePoisonState {
    public enum TickResult { NONE, CHANGED, COMPLETED_NATURALLY }

    private final boolean poisonTyped;
    private int accumulation;
    private long levelEndTick = -1L;
    private long nextPassiveTick = -1L;
    private boolean statSuppressedByHaze;

    public ActionBattlePoisonState() { this(false); }

    public ActionBattlePoisonState(boolean poisonTyped) { this.poisonTyped = poisonTyped; }

    public boolean applyDirectGain(int gain, long currentTick) {
        if (gain <= 0 || currentTick < 0L || level() == ActionBattlePoisonRules.PoisonLevel.TOXIC) return false;
        ActionBattlePoisonRules.PoisonLevel previous = level();
        accumulation = (int) Math.min(ActionBattlePoisonRules.MAX_ACCUMULATION, (long) accumulation + gain);
        ActionBattlePoisonRules.PoisonLevel current = level();
        if (previous == ActionBattlePoisonRules.PoisonLevel.NONE) {
            levelEndTick = deadline(currentTick, current);
            nextPassiveTick = ActionBattleTiming.safeAdd(currentTick, ActionBattlePoisonRules.PASSIVE_INTERVAL_TICKS);
        } else if (current != previous) {
            levelEndTick = deadline(currentTick, current);
        }
        return true;
    }

    public TickResult tick(long currentTick) {
        if (currentTick < 0L || accumulation == 0) return TickResult.NONE;
        if (currentTick >= levelEndTick) {
            if (level() == ActionBattlePoisonRules.PoisonLevel.TOXIC) {
                clear();
                return TickResult.COMPLETED_NATURALLY;
            }
            accumulation = Math.max(0, accumulation - ActionBattlePoisonRules.decay(level()));
            if (accumulation == 0) {
                clear();
                return TickResult.COMPLETED_NATURALLY;
            }
            levelEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattlePoisonRules.LEVEL_DURATION_TICKS);
            applyDuePassive(currentTick);
            return TickResult.CHANGED;
        }
        return applyDuePassive(currentTick) ? TickResult.CHANGED : TickResult.NONE;
    }

    private boolean applyDuePassive(long currentTick) {
        if (nextPassiveTick >= 0L && currentTick >= nextPassiveTick
                && level() != ActionBattlePoisonRules.PoisonLevel.TOXIC) {
            ActionBattlePoisonRules.PoisonLevel previous = level();
            accumulation = Math.min(ActionBattlePoisonRules.MAX_ACCUMULATION,
                    accumulation + ActionBattlePoisonRules.passiveGain(previous));
            ActionBattlePoisonRules.PoisonLevel current = level();
            nextPassiveTick = ActionBattleTiming.safeAdd(currentTick, ActionBattlePoisonRules.PASSIVE_INTERVAL_TICKS);
            if (current != previous) levelEndTick = deadline(currentTick, current);
            return true;
        }
        return false;
    }

    private static long deadline(long currentTick, ActionBattlePoisonRules.PoisonLevel level) {
        int duration = level == ActionBattlePoisonRules.PoisonLevel.TOXIC
                ? ActionBattlePoisonRules.TOXIC_DURATION_TICKS : ActionBattlePoisonRules.LEVEL_DURATION_TICKS;
        return ActionBattleTiming.safeAdd(currentTick, duration);
    }

    private void clear() {
        accumulation = 0;
        levelEndTick = -1L;
        nextPassiveTick = -1L;
        statSuppressedByHaze = false;
    }

    public void suppressSpecialAttackByHaze() { statSuppressedByHaze = true; }
    public int accumulation() { return accumulation; }
    public ActionBattlePoisonRules.PoisonLevel level() { return ActionBattlePoisonRules.levelForAccumulation(accumulation); }
    public long levelEndTick() { return levelEndTick; }
    public long nextPassiveTick() { return nextPassiveTick; }
    public boolean poisonTyped() { return poisonTyped; }
    public boolean statSuppressedByHaze() { return statSuppressedByHaze; }
    public int ownedSpecialAttackStages() {
        return statSuppressedByHaze ? 0 : ActionBattlePoisonRules.specialAttackStages(level(), poisonTyped);
    }
    public boolean isEmpty() { return accumulation == 0; }
}
