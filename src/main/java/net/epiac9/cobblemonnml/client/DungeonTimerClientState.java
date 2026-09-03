package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.network.DungeonTimerPayload;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;

public final class DungeonTimerClientState {
    private static boolean visible = false;
    private static boolean paused = false;
    private static int remainingSeconds = 0;
    private static int durationSeconds = 0;
    private static int themeIndex = 0;
    private static int tierIndex = 0;
    private static long receivedAtNanos = 0L;

    private DungeonTimerClientState() {}

    public static void apply(DungeonTimerPayload payload) {
        if (payload == null || !payload.visible()) {
            clear();
            return;
        }
        visible = payload.visible();
        paused = payload.paused();
        remainingSeconds = Math.max(0, payload.remainingSeconds());
        durationSeconds = Math.max(0, payload.durationSeconds());
        themeIndex = payload.themeIndex();
        tierIndex = Math.clamp(payload.tierIndex(), 0, 4);
        receivedAtNanos = System.nanoTime();
    }

    public static boolean isActive() { return visible && durationSeconds > 0 && getTheme() != null; }

    public static DungeonTheme getTheme() { return DungeonTheme.fromVisualIndex(themeIndex); }
    public static int getTierIndex() { return tierIndex; }

    public static double getEstimatedRemainingSeconds() {
        if (!visible) return 0.0D;
        if (paused) return remainingSeconds;
        double elapsedSeconds = (System.nanoTime() - receivedAtNanos) / 1_000_000_000.0D;
        return Math.max(0.0D, remainingSeconds - elapsedSeconds);
    }

    public static double getProgress(double remainingSeconds) {
        if (durationSeconds <= 0) return 0.0D;
        return Math.clamp(remainingSeconds / durationSeconds, 0.0D, 1.0D);
    }

    public static void clear() {
        visible = false;
        paused = false;
        remainingSeconds = 0;
        durationSeconds = 0;
        themeIndex = 0;
        tierIndex = 0;
        receivedAtNanos = 0L;
    }
}
