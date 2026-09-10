package net.epiac9.cobblemonnml.battle.action.effect.status;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleSleepState {
    private long sleepExpiresAtTick;
    private long sleepDurationTicks;

    public boolean beginSleep(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks < ActionBattleSleepRules.MIN_DURATION_TICKS
                || durationTicks > ActionBattleSleepRules.MAX_DURATION_TICKS) return false;
        sleepExpiresAtTick = ActionBattleTiming.safeAdd(currentTick, durationTicks);
        sleepDurationTicks = durationTicks;
        return true;
    }

    public boolean wake(long currentTick) {
        if (currentTick < 0L) return false;
        boolean wasSleeping = isSleeping(currentTick) || sleepExpiresAtTick > 0L;
        clearAll();
        return wasSleeping;
    }

    public boolean isSleeping(long currentTick) {
        return currentTick >= 0L && sleepExpiresAtTick > currentTick;
    }

    public long sleepRemainingTicks(long currentTick) {
        return isSleeping(currentTick) ? sleepExpiresAtTick - currentTick : 0L;
    }

    public long sleepDurationTicks(long currentTick) {
        return isSleeping(currentTick) ? sleepDurationTicks : 0L;
    }

    public boolean cleanse(long currentTick) {
        return currentTick >= 0L && wake(currentTick);
    }

    public void clearAll() {
        sleepExpiresAtTick = 0L;
        sleepDurationTicks = 0L;
    }

    public boolean isEmpty(long currentTick) {
        tick(currentTick);
        return sleepExpiresAtTick == 0L;
    }

    public NaturalWakeResult tick(long currentTick) {
        if (currentTick < 0L || sleepExpiresAtTick <= 0L || currentTick < sleepExpiresAtTick) {
            return NaturalWakeResult.NONE;
        }
        clearAll();
        return NaturalWakeResult.WOKE_NATURALLY;
    }

    public enum NaturalWakeResult { NONE, WOKE_NATURALLY }
}
