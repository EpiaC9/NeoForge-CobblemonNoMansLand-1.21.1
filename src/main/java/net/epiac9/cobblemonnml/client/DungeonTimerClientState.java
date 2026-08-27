package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.network.DungeonTimerPayload;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;

public final class DungeonTimerClientState {
    private static boolean visible = false;
    private static boolean paused = false;
    private static int remainingSeconds = 0;
    private static int themeIndex = 0;
    private static int tierIndex = 0;
    private static long receivedAtNanos = 0L;

    private DungeonTimerClientState() {}

    public static void apply(DungeonTimerPayload payload) {
        visible = payload.visible();
        paused = payload.paused();
        remainingSeconds = Math.max(0, payload.remainingSeconds());
        themeIndex = payload.themeIndex();
        tierIndex = Math.clamp(payload.tierIndex(), 0, 4);
        receivedAtNanos = System.nanoTime();
    }

    public static boolean isVisible() { return visible; }
    public static boolean isPaused() { return paused; }

    public static DungeonTheme getTheme() {
        return DungeonTheme.fromVisualIndex(themeIndex);
    }

    public static int getTierIndex() { return tierIndex; }

    public static double getEstimatedRemainingSeconds() {
        if (!visible) return 0.0D;
        if (paused) return remainingSeconds;

        double elapsedSeconds =
                (System.nanoTime() - receivedAtNanos) / 1_000_000_000.0D;

        return Math.max(0.0D, remainingSeconds - elapsedSeconds);
    }

    public static int getDisplayedSeconds() {
        return (int) Math.ceil(getEstimatedRemainingSeconds());
    }

    public static void clear() {
        visible = false;
        paused = false;
        remainingSeconds = 0;
        themeIndex = 0;
        tierIndex = 0;
        receivedAtNanos = 0L;
    }
}
