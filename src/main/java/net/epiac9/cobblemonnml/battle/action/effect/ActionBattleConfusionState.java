package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleConfusionState {
    private final ActionBattleReapplicationTracker reapplication = new ActionBattleReapplicationTracker();
    private long expiresAtTick;
    private long activeDurationTicks;

    public ApplyResult apply(long currentTick) {
        if (currentTick < 0L) return null;
        expireIfNeeded(currentTick);
        if (expiresAtTick > currentTick) return ApplyResult.IGNORED_ACTIVE;
        long duration = reapplication.durationForApplication(ActionBattleConfusionRules.DURATION_TICKS, currentTick);
        activeDurationTicks = duration;
        expiresAtTick = ActionBattleTiming.safeAdd(currentTick, duration);
        return ApplyResult.APPLIED;
    }

    public boolean isActive(long currentTick) {
        expireIfNeeded(currentTick);
        return currentTick >= 0L && currentTick < expiresAtTick;
    }

    public long remainingTicks(long currentTick) { return isActive(currentTick) ? Math.max(0L, expiresAtTick - currentTick) : 0L; }
    public long durationTicks(long currentTick) { return isActive(currentTick) ? activeDurationTicks : 0L; }

    public void clear(long currentTick) {
        expireIfNeeded(currentTick);
        if (expiresAtTick > currentTick) reapplication.onEffectEnded(currentTick);
        expiresAtTick = 0L;
        activeDurationTicks = 0L;
    }

    public boolean isEmpty(long currentTick) { return !isActive(currentTick) && !reapplication.isTracking(currentTick); }

    private void expireIfNeeded(long currentTick) {
        if (expiresAtTick <= 0L || currentTick < expiresAtTick) return;
        long endedTick = expiresAtTick;
        expiresAtTick = 0L;
        activeDurationTicks = 0L;
        reapplication.onEffectEnded(endedTick);
    }

    public enum ApplyResult { APPLIED, IGNORED_ACTIVE }
}
