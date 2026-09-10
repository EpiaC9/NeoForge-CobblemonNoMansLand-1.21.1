package net.epiac9.cobblemonnml.battle.action.effect.status;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public record ActionBattleDrowsyState(long startTick, long endTick, int totalDurationTicks) {
    public ActionBattleDrowsyState(long startTick, int totalDurationTicks) {
        this(startTick, ActionBattleTiming.safeAdd(startTick, totalDurationTicks), totalDurationTicks);
    }

    public ActionBattleDrowsyState {
        if (startTick < 0L || totalDurationTicks <= 0 || endTick < startTick) {
            throw new IllegalArgumentException("Invalid Drowsy state.");
        }
    }

    public boolean isActive(long currentTick) {
        return currentTick >= startTick && currentTick < endTick;
    }

    public long remainingTicks(long currentTick) {
        return currentTick >= 0L ? Math.max(0L, endTick - currentTick) : 0L;
    }
}
