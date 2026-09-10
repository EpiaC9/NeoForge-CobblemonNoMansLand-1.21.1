package net.epiac9.cobblemonnml.battle.action.effect.status;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public record ActionBattleTimedStatusState(long startTick, long endTick, long durationTicks) {
    public ActionBattleTimedStatusState(long currentTick, long durationTicks) {
        this(currentTick, ActionBattleTiming.safeAdd(currentTick, durationTicks), durationTicks);
    }

    public ActionBattleTimedStatusState {
        if (startTick < 0L || durationTicks <= 0L || endTick < startTick) {
            throw new IllegalArgumentException("Invalid timed ACTION status state.");
        }
    }

    public boolean active(long currentTick) {
        return currentTick >= startTick && currentTick < endTick;
    }

    public long remainingTicks(long currentTick) {
        return currentTick >= 0L ? Math.max(0L, endTick - currentTick) : 0L;
    }
}
