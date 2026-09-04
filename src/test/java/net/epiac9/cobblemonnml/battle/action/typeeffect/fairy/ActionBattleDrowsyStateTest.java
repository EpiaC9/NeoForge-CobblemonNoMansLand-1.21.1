package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

public final class ActionBattleDrowsyStateTest {
    private ActionBattleDrowsyStateTest() {}

    public static void main(String[] args) {
        firstApplicationUsesBaseDuration();
        activeReapplicationIsANoop();
        naturalCompletionsIncreaseDurationWithoutACap();
        recallCancelsWithoutCompleting();
        cleanResetRequiresTheFullWindowAfterCompletionEnds();
        reapplicationInterruptsTheCleanResetAndUsesThePenalty();
        completionResultBlocksOverlappingDrowsy();
    }

    private static void completionResultBlocksOverlappingDrowsy() {
        ActionBattleDrowsyTracker tracker = completedAt180(tracker());
        assertSame(ActionBattleDrowsyTracker.ApplyResult.IGNORED_COMPLETION, tracker.apply(200L));
        assertTrue(tracker.activeDrowsy().isEmpty());
        assertTrue(tracker.completion().isPresent());
        assertEquals(360, tracker.nextDrowsyDurationTicks());
    }

    private static void firstApplicationUsesBaseDuration() {
        ActionBattleDrowsyTracker tracker = new ActionBattleDrowsyTracker();
        assertSame(ActionBattleDrowsyTracker.ApplyResult.APPLIED, tracker.apply(40L));
        ActionBattleDrowsyState state = tracker.activeDrowsy().orElseThrow();
        assertEquals(40L, state.startTick());
        assertEquals(220L, state.endTick());
        assertEquals(180, state.totalDurationTicks());
        assertEquals(180L, state.remainingTicks(40L));
    }

    private static void activeReapplicationIsANoop() {
        ActionBattleDrowsyTracker tracker = new ActionBattleDrowsyTracker();
        tracker.apply(0L);
        ActionBattleDrowsyState original = tracker.activeDrowsy().orElseThrow();
        assertSame(ActionBattleDrowsyTracker.ApplyResult.IGNORED_ACTIVE, tracker.apply(100L));
        ActionBattleDrowsyState unchanged = tracker.activeDrowsy().orElseThrow();
        assertEquals(original.endTick(), unchanged.endTick());
        assertEquals(original.totalDurationTicks(), unchanged.totalDurationTicks());
        assertEquals(180, tracker.nextDrowsyDurationTicks());
    }

    private static void naturalCompletionsIncreaseDurationWithoutACap() {
        ActionBattleDrowsyTracker tracker = new ActionBattleDrowsyTracker();
        long tick = 0L;
        int[] expectedDurations = {360, 540, 720, 900, 1080, 1260};
        for (int expected : expectedDurations) {
            tracker.apply(tick);
            ActionBattleDrowsyState active = tracker.activeDrowsy().orElseThrow();
            tick = active.endTick();
            assertTrue(tracker.completeNaturally(tick, 120, ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF));
            assertEquals(expected, tracker.nextDrowsyDurationTicks());
            tracker.tick(tick + 120L, false);
            tick += 121L;
        }
    }

    private static void recallCancelsWithoutCompleting() {
        ActionBattleDrowsyTracker tracker = new ActionBattleDrowsyTracker();
        tracker.apply(0L);
        assertTrue(tracker.cancelOnRecall(60L));
        assertTrue(tracker.activeDrowsy().isEmpty());
        assertTrue(tracker.completion().isEmpty());
        assertEquals(180, tracker.nextDrowsyDurationTicks());
        assertEquals(420L, tracker.cleanResetEndTick());
    }

    private static void cleanResetRequiresTheFullWindowAfterCompletionEnds() {
        ActionBattleDrowsyTracker tracker = completedAt180(tracker());
        tracker.tick(300L, false);
        assertEquals(360, tracker.nextDrowsyDurationTicks());
        assertEquals(660L, tracker.cleanResetEndTick());
        tracker.tick(659L, false);
        assertEquals(360, tracker.nextDrowsyDurationTicks());
        tracker.tick(660L, false);
        assertEquals(180, tracker.nextDrowsyDurationTicks());
        assertTrue(tracker.isEmpty());
    }

    private static void reapplicationInterruptsTheCleanResetAndUsesThePenalty() {
        ActionBattleDrowsyTracker tracker = completedAt180(tracker());
        tracker.tick(300L, false);
        assertSame(ActionBattleDrowsyTracker.ApplyResult.APPLIED, tracker.apply(500L));
        ActionBattleDrowsyState active = tracker.activeDrowsy().orElseThrow();
        assertEquals(360, active.totalDurationTicks());
        assertEquals(860L, active.endTick());
        assertEquals(-1L, tracker.cleanResetEndTick());
    }

    private static ActionBattleDrowsyTracker tracker() {
        ActionBattleDrowsyTracker tracker = new ActionBattleDrowsyTracker();
        tracker.apply(0L);
        return tracker;
    }

    private static ActionBattleDrowsyTracker completedAt180(ActionBattleDrowsyTracker tracker) {
        tracker.completeNaturally(180L, 120, ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF);
        return tracker;
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
