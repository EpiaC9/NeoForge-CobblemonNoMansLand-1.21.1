package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

final class ActionBattleTwoStageDotState {
    enum ApplyResult {
        BUILDUP_APPLIED,
        DOT_APPLIED,
        DOT_REFRESHED
    }

    private final long baseDurationTicks;
    private final long dotIntervalTicks;
    private long buildupEndTick;
    private long dotEndTick;
    private long nextDotTick;

    ActionBattleTwoStageDotState(long baseDurationTicks, long dotIntervalTicks) {
        if (baseDurationTicks <= 0L || dotIntervalTicks <= 0L) throw new IllegalArgumentException("Durations must be positive.");
        this.baseDurationTicks = baseDurationTicks;
        this.dotIntervalTicks = dotIntervalTicks;
    }

    ApplyResult apply(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        pruneBuildup(currentTick);
        long scaledDuration = ActionBattleTiming.scaledTicks(baseDurationTicks, durationMultiplier);
        if (hasDot(currentTick)) {
            dotEndTick = Math.max(dotEndTick, ActionBattleTiming.safeAdd(currentTick, scaledDuration));
            nextDotTick = ActionBattleTiming.safeAdd(currentTick, dotIntervalTicks);
            return ApplyResult.DOT_REFRESHED;
        }
        if (hasBuildup(currentTick)) {
            buildupEndTick = 0L;
            dotEndTick = ActionBattleTiming.safeAdd(currentTick, scaledDuration);
            nextDotTick = ActionBattleTiming.safeAdd(currentTick, dotIntervalTicks);
            return ApplyResult.DOT_APPLIED;
        }
        buildupEndTick = ActionBattleTiming.safeAdd(currentTick, scaledDuration);
        return ApplyResult.BUILDUP_APPLIED;
    }

    boolean hasBuildup(long currentTick) {
        return currentTick >= 0L && currentTick < buildupEndTick;
    }

    boolean hasDot(long currentTick) {
        return currentTick >= 0L && currentTick < dotEndTick;
    }

    long buildupRemainingTicks(long currentTick) {
        return hasBuildup(currentTick) ? Math.max(0L, buildupEndTick - currentTick) : 0L;
    }

    long dotRemainingTicks(long currentTick) {
        return hasDot(currentTick) ? Math.max(0L, dotEndTick - currentTick) : 0L;
    }

    boolean pollDot(long currentTick) {
        if (currentTick < 0L || dotEndTick <= 0L || nextDotTick <= 0L || currentTick < nextDotTick || nextDotTick > dotEndTick) return false;
        nextDotTick = ActionBattleTiming.safeAdd(nextDotTick, dotIntervalTicks);
        return true;
    }

    void prune(long currentTick) {
        pruneBuildup(currentTick);
        if (dotEndTick > 0L && currentTick >= dotEndTick) {
            dotEndTick = 0L;
            nextDotTick = 0L;
        }
    }

    void clear() {
        buildupEndTick = 0L;
        dotEndTick = 0L;
        nextDotTick = 0L;
    }

    boolean isEmpty() {
        return buildupEndTick == 0L && dotEndTick == 0L;
    }

    void pruneBuildup(long currentTick) {
        if (buildupEndTick > 0L && currentTick >= buildupEndTick) buildupEndTick = 0L;
    }
}
