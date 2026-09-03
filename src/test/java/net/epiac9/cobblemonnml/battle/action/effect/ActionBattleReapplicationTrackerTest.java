package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleReapplicationTrackerTest {
    private ActionBattleReapplicationTrackerTest() {}

    public static void main(String[] args) {
        preservesDurationProgressionAndResetWindow();
        preservesApprovedRoundingExamples();
        activeReapplicationIsANoOp();
    }

    private static void preservesDurationProgressionAndResetWindow() {
        ActionBattleReapplicationTracker tracker = new ActionBattleReapplicationTracker();

        assertEquals(180L, tracker.durationForApplication(180L, 0L));
        tracker.onEffectEnded(180L);
        assertEquals(120L, tracker.durationForApplication(180L, 200L));
        tracker.onEffectEnded(320L);
        assertEquals(60L, tracker.durationForApplication(180L, 340L));
        tracker.onEffectEnded(400L);

        long justBeforeReset = 400L + ActionBattleTiming.UNIVERSAL_RESET_WINDOW_TICKS - 1L;
        assertTrue(tracker.isTracking(justBeforeReset));
        assertEquals(60L, tracker.durationForApplication(180L, justBeforeReset));
        tracker.onEffectEnded(justBeforeReset + 60L);

        long resetTick = justBeforeReset + 60L + ActionBattleTiming.UNIVERSAL_RESET_WINDOW_TICKS;
        assertFalse(tracker.isTracking(resetTick));
        assertEquals(180L, tracker.durationForApplication(180L, resetTick));
    }

    private static void preservesApprovedRoundingExamples() {
        assertProgression(180L, 180L, 120L, 60L);
        assertProgression(120L, 120L, 80L, 40L);
        assertProgression(300L, 300L, 200L, 100L);
    }

    private static void activeReapplicationIsANoOp() {
        ActionBattleEvasionState state = new ActionBattleEvasionState();
        assertSame(ActionBattleEvasionState.ApplyResult.APPLIED, state.apply(100L));
        long duration = state.durationTicks(120L);
        long remaining = state.remainingTicks(120L);

        assertSame(ActionBattleEvasionState.ApplyResult.IGNORED_ACTIVE, state.apply(120L));
        assertEquals(duration, state.durationTicks(120L));
        assertEquals(remaining, state.remainingTicks(120L));
    }

    private static void assertProgression(long base, long full, long reduced, long minimum) {
        ActionBattleReapplicationTracker tracker = new ActionBattleReapplicationTracker();
        assertEquals(full, tracker.durationForApplication(base, 0L));
        tracker.onEffectEnded(full);
        assertEquals(reduced, tracker.durationForApplication(base, full + 1L));
        tracker.onEffectEnded(full + reduced + 1L);
        assertEquals(minimum, tracker.durationForApplication(base, full + reduced + 2L));
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
