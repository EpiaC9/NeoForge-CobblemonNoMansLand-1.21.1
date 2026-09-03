package net.epiac9.cobblemonnml.battle.action.effect;

public final class ActionBattleSleepState {
    public static final long SLEEP_MIN_DURATION_TICKS = 60L;
    public static final long SLEEP_MAX_DURATION_TICKS = 180L;

    private long sleepExpiresAtTick;

    public boolean beginSleep(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks < SLEEP_MIN_DURATION_TICKS || durationTicks > SLEEP_MAX_DURATION_TICKS) return false;
        sleepExpiresAtTick = safeAdd(currentTick, durationTicks);
        return true;
    }

    public boolean wake(long currentTick) {
        if (currentTick < 0L) return false;
        boolean wasSleeping = isSleeping(currentTick) || sleepExpiresAtTick > 0L;
        sleepExpiresAtTick = 0L;
        return wasSleeping;
    }

    public boolean isSleeping(long currentTick) { return isActiveAt(currentTick, sleepExpiresAtTick); }
    public long sleepRemainingTicks(long currentTick) { return remainingTicks(currentTick, sleepExpiresAtTick); }

    public boolean cleanse(long currentTick) {
        if (currentTick < 0L) return false;
        return wake(currentTick);
    }

    public void clearAll() { sleepExpiresAtTick = 0L; }

    public boolean isEmpty(long currentTick) {
        prune(currentTick);
        return sleepExpiresAtTick == 0L;
    }

    public NaturalWakeResult tick(long currentTick) {
        if (currentTick < 0L) return NaturalWakeResult.NONE;
        if (sleepExpiresAtTick > 0L && currentTick >= sleepExpiresAtTick) {
            sleepExpiresAtTick = 0L;
            return NaturalWakeResult.WOKE_NATURALLY;
        }
        return NaturalWakeResult.NONE;
    }

    private void prune(long currentTick) {
        if (sleepExpiresAtTick > 0L && currentTick >= sleepExpiresAtTick) sleepExpiresAtTick = 0L;
    }

    private static boolean isActiveAt(long currentTick, long expiresAtTick) {
        return currentTick >= 0L && expiresAtTick > currentTick;
    }

    private static long remainingTicks(long currentTick, long expiresAtTick) {
        return isActiveAt(currentTick, expiresAtTick) ? expiresAtTick - currentTick : 0L;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public enum NaturalWakeResult { NONE, WOKE_NATURALLY }
}
