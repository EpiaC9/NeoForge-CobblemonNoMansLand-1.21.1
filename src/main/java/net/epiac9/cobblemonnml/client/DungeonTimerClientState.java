package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.network.DungeonTimerPayload;

public final class DungeonTimerClientState {
    // CLIENT TIMER STATE
    private static boolean visible = false;
    private static int remainingSeconds = 0;
    private static int themeIndex = 0;
    private static int tierIndex = 0;
    private static long receivedAtNanos = 0L;
    // APPLY SERVER UPDATE
    public static void apply( DungeonTimerPayload payload ) {
        visible = payload.visible();
        remainingSeconds = Math.max( 0, payload.remainingSeconds() );
        themeIndex = payload.themeIndex();
        tierIndex = Math.clamp( payload.tierIndex() , 0, 4);
        receivedAtNanos = System.nanoTime();
    }
    // VISIBLE?
    public static boolean isVisible() {
        return visible;
    }
    // THEME
    public static DungeonTheme getTheme() {
        return DungeonTheme
                .fromVisualIndex( themeIndex );
    }
    // TIER
    public static int getTierIndex() {
        return tierIndex;
    }
    // ESTIMATED REMAINING TIME
    public static double getEstimatedRemainingSeconds() {
        if (!visible) {
            return 0.0D;
        }
        double elapsedSeconds =
                ( System.nanoTime() - receivedAtNanos )
                        / 1_000_000_000.0D;
        return Math.max( 0.0D, remainingSeconds - elapsedSeconds );
    }
    // DISPLAYED SECOND
    public static int getDisplayedSeconds() {
        return (int) Math.ceil( getEstimatedRemainingSeconds() );
    }
    // CLEAR
    public static void clear() {
        visible = false;
        remainingSeconds = 0;
        themeIndex = 0;
        tierIndex = 0;
        receivedAtNanos = 0L;
    }
}
