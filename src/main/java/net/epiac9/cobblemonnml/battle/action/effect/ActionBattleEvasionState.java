package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

final class ActionBattleEvasionState {
    static final long DURATION_TICKS = 120L;
    private final ActionBattleReapplicationTracker reapplication = new ActionBattleReapplicationTracker();
    private long expiresAtTick = -1L;
    private long activeDurationTicks;

    ApplyResult apply(long currentTick) {
        if (currentTick < 0L) return null;
        expireIfNeeded(currentTick);
        if (expiresAtTick > currentTick) return ApplyResult.IGNORED_ACTIVE;
        long duration = reapplication.durationForApplication(DURATION_TICKS, currentTick);
        activeDurationTicks = duration;
        expiresAtTick = ActionBattleTiming.safeAdd(currentTick, duration);
        return ApplyResult.APPLIED;
    }

    boolean isActive(long currentTick) {
        expireIfNeeded(currentTick);
        return currentTick >= 0L && expiresAtTick > currentTick;
    }

    long remainingTicks(long currentTick) { return isActive(currentTick) ? Math.max(0L, expiresAtTick - currentTick) : 0L; }
    long durationTicks(long currentTick) { return isActive(currentTick) ? activeDurationTicks : 0L; }
    boolean isEmpty(long currentTick) { return !isActive(currentTick) && !reapplication.isTracking(currentTick); }

    void clear(long currentTick) {
        expireIfNeeded(currentTick);
        if (expiresAtTick > currentTick) reapplication.onEffectEnded(currentTick);
        expiresAtTick = -1L;
        activeDurationTicks = 0L;
    }

    private void expireIfNeeded(long currentTick) {
        if (expiresAtTick < 0L || currentTick < expiresAtTick) return;
        long endedTick = expiresAtTick;
        expiresAtTick = -1L;
        activeDurationTicks = 0L;
        reapplication.onEffectEnded(endedTick);
    }

    enum ApplyResult { APPLIED, IGNORED_ACTIVE }
}
