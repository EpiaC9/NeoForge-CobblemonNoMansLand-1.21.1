package net.epiac9.cobblemonnml.battle.action.effect;

public final class ActionBattleSleepState {
    public static final long DROWSINESS_DURATION_TICKS = 360L;
    public static final long DROWSINESS_REAPPLICATION_REDUCTION_TICKS = 60L;
    public static final long SLEEP_MIN_DURATION_TICKS = 60L;
    public static final long SLEEP_MAX_DURATION_TICKS = 180L;
    public static final long DROWSINESS_GRACE_DURATION_TICKS = 120L;

    private long drowsinessExpiresAtTick;
    private long sleepExpiresAtTick;
    private long graceExpiresAtTick;

    public DrowsinessResult applyDrowsiness(long currentTick) {
        if (currentTick < 0L) return DrowsinessResult.REJECTED;
        prune(currentTick);
        if (isSleeping(currentTick)) return DrowsinessResult.BLOCKED_BY_SLEEP;
        if (hasDrowsinessGrace(currentTick)) return DrowsinessResult.BLOCKED_BY_GRACE;
        if (isDrowsy(currentTick)) {
            drowsinessExpiresAtTick = Math.max(currentTick, drowsinessExpiresAtTick - DROWSINESS_REAPPLICATION_REDUCTION_TICKS);
            return drowsinessExpiresAtTick <= currentTick ? DrowsinessResult.READY_FOR_SLEEP : DrowsinessResult.SHORTENED;
        }
        drowsinessExpiresAtTick = safeAdd(currentTick, DROWSINESS_DURATION_TICKS);
        return DrowsinessResult.APPLIED;
    }

    public boolean shouldBeginSleep(long currentTick) {
        return currentTick >= 0L && drowsinessExpiresAtTick > 0L && currentTick >= drowsinessExpiresAtTick && !isSleeping(currentTick);
    }

    public boolean beginSleep(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks < SLEEP_MIN_DURATION_TICKS || durationTicks > SLEEP_MAX_DURATION_TICKS) return false;
        drowsinessExpiresAtTick = 0L;
        graceExpiresAtTick = 0L;
        sleepExpiresAtTick = safeAdd(currentTick, durationTicks);
        return true;
    }

    public boolean wake(long currentTick) {
        if (currentTick < 0L) return false;
        boolean wasSleeping = isSleeping(currentTick) || sleepExpiresAtTick > 0L;
        sleepExpiresAtTick = 0L;
        drowsinessExpiresAtTick = 0L;
        graceExpiresAtTick = safeAdd(currentTick, DROWSINESS_GRACE_DURATION_TICKS);
        return wasSleeping;
    }

    public boolean isDrowsy(long currentTick) { return isActiveAt(currentTick, drowsinessExpiresAtTick); }
    public boolean isSleeping(long currentTick) { return isActiveAt(currentTick, sleepExpiresAtTick); }
    public boolean hasDrowsinessGrace(long currentTick) { return isActiveAt(currentTick, graceExpiresAtTick); }
    public long drowsinessRemainingTicks(long currentTick) { return remainingTicks(currentTick, drowsinessExpiresAtTick); }
    public long sleepRemainingTicks(long currentTick) { return remainingTicks(currentTick, sleepExpiresAtTick); }
    public long graceRemainingTicks(long currentTick) { return remainingTicks(currentTick, graceExpiresAtTick); }
    public long drowsinessExpiresAtTick() { return drowsinessExpiresAtTick; }


    public boolean cleanse(long currentTick) {
        if (currentTick < 0L) return false;
        if (isSleeping(currentTick) || sleepExpiresAtTick > 0L) return wake(currentTick);
        boolean changed = drowsinessExpiresAtTick > 0L;
        drowsinessExpiresAtTick = 0L;
        return changed;
    }

    public void clearAll() {
        drowsinessExpiresAtTick = 0L;
        sleepExpiresAtTick = 0L;
        graceExpiresAtTick = 0L;
    }

    public boolean isEmpty(long currentTick) {
        prune(currentTick);
        return drowsinessExpiresAtTick == 0L && sleepExpiresAtTick == 0L && graceExpiresAtTick == 0L;
    }

    public NaturalWakeResult tick(long currentTick) {
        if (currentTick < 0L) return NaturalWakeResult.NONE;
        if (sleepExpiresAtTick > 0L && currentTick >= sleepExpiresAtTick) {
            sleepExpiresAtTick = 0L;
            graceExpiresAtTick = safeAdd(currentTick, DROWSINESS_GRACE_DURATION_TICKS);
            return NaturalWakeResult.WOKE_NATURALLY;
        }
        if (graceExpiresAtTick > 0L && currentTick >= graceExpiresAtTick) graceExpiresAtTick = 0L;
        return NaturalWakeResult.NONE;
    }

    private void prune(long currentTick) {
        if (graceExpiresAtTick > 0L && currentTick >= graceExpiresAtTick) graceExpiresAtTick = 0L;
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

    public enum DrowsinessResult { APPLIED, SHORTENED, READY_FOR_SLEEP, BLOCKED_BY_SLEEP, BLOCKED_BY_GRACE, REJECTED }
    public enum NaturalWakeResult { NONE, WOKE_NATURALLY }
}
