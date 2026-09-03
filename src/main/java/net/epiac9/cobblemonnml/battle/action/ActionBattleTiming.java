package net.epiac9.cobblemonnml.battle.action;

public final class ActionBattleTiming {
    public static final long TICKS_PER_SECOND = 20L;
    public static final long MOVE_HERE_COOLDOWN_TICKS = TICKS_PER_SECOND;
    public static final long SWAP_COOLDOWN_TICKS = 16L * TICKS_PER_SECOND;
    public static final long HUD_SYNC_INTERVAL_TICKS = 2L;
    public static final long UNIVERSAL_RESET_WINDOW_TICKS = 18L * TICKS_PER_SECOND;

    private ActionBattleTiming() {}

    public static long seconds(long seconds) {
        if (seconds <= 0L) return 0L;
        return safeMultiply(seconds, TICKS_PER_SECOND);
    }

    public static long scaledTicks(long ticks, float multiplier) {
        if (ticks <= 0L || !(multiplier > 0.0F)) return 0L;
        return Math.max(1L, Math.round(ticks * multiplier));
    }

    public static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        if (right < 0L && left < Long.MIN_VALUE - right) return Long.MIN_VALUE;
        return left + right;
    }

    private static long safeMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
