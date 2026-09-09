package net.epiac9.cobblemonnml.battle.action.interrupt;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleInterruptState {
    private long endTick = -1L;

    public boolean apply(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks <= 0L) return false;
        endTick = ActionBattleTiming.safeAdd(currentTick, durationTicks);
        return true;
    }

    public boolean isActive(long currentTick) {
        if (currentTick < 0L || endTick < 0L) return false;
        if (currentTick >= endTick) endTick = -1L;
        return endTick >= 0L;
    }

    public long remainingTicks(long currentTick) {
        return isActive(currentTick) ? Math.max(0L, endTick - currentTick) : 0L;
    }

    public void clear() { endTick = -1L; }
    public boolean isEmpty(long currentTick) { return !isActive(currentTick); }
}
