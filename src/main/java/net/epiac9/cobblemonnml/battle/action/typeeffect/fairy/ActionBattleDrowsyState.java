package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public record ActionBattleDrowsyState(long startTick, long endTick, int totalDurationTicks) {
    public ActionBattleDrowsyState(long startTick, int totalDurationTicks) {
        this(startTick, ActionBattleTiming.safeAdd(startTick, totalDurationTicks), totalDurationTicks);
    }

    public ActionBattleDrowsyState {
        if (startTick < 0L) throw new IllegalArgumentException("Drowsy start tick cannot be negative.");
        if (totalDurationTicks <= 0) throw new IllegalArgumentException("Drowsy duration must be positive.");
        if (endTick < startTick) throw new IllegalArgumentException("Drowsy end tick cannot precede its start.");
    }

    public boolean isActive(long currentTick) {
        return currentTick >= startTick && currentTick < endTick;
    }

    public long remainingTicks(long currentTick) {
        return currentTick >= 0L ? Math.max(0L, endTick - currentTick) : 0L;
    }
}
