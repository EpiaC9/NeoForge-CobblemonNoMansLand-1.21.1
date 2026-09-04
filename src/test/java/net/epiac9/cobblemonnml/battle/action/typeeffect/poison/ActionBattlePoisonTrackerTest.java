package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

public final class ActionBattlePoisonTrackerTest {
    private ActionBattlePoisonTrackerTest() {}

    public static void main(String[] args) {
        firstExposureStartsAtOne();
        reapplicationUsesTheSuppliedPenetratedGain();
        naturalCyclesReduceFutureGainToOne();
        cleanResetRequiresEveryTick();
        earlyReapplicationCancelsTheReset();
        toxicRejectsReapplication();
    }

    private static void firstExposureStartsAtOne() {
        ActionBattlePoisonTracker tracker = new ActionBattlePoisonTracker();
        assertTrue(tracker.applyMove(10L, true, 7));
        assertEquals(1, tracker.activeState().orElseThrow().accumulation());
        assertEquals(9, tracker.moveAccumulationGain());
        assertTrue(tracker.poisonTyped());
    }

    private static void reapplicationUsesTheSuppliedPenetratedGain() {
        ActionBattlePoisonTracker tracker = new ActionBattlePoisonTracker();
        tracker.applyMove(0L, false, 9);
        assertTrue(tracker.applyMove(1L, false, 4));
        assertEquals(5, tracker.activeState().orElseThrow().accumulation());
        assertFalse(tracker.applyMove(2L, false, 0));
        assertEquals(5, tracker.activeState().orElseThrow().accumulation());
    }

    private static void naturalCyclesReduceFutureGainToOne() {
        ActionBattlePoisonTracker tracker = new ActionBattlePoisonTracker();
        long tick = 0L;
        int[] expected = {8, 7, 6, 5, 4, 3, 2, 1, 1};
        for (int gain : expected) {
            tracker.applyMove(tick, false, tracker.moveAccumulationGain());
            tick += 120L;
            assertSame(ActionBattlePoisonState.TickResult.COMPLETED_NATURALLY, tracker.tick(tick));
            assertEquals(gain, tracker.moveAccumulationGain());
            tick++;
        }
    }

    private static void cleanResetRequiresEveryTick() {
        ActionBattlePoisonTracker tracker = completedAt120();
        assertEquals(480L, tracker.cleanResetEndTick());
        tracker.tick(479L);
        assertEquals(8, tracker.moveAccumulationGain());
        tracker.tick(480L);
        assertEquals(9, tracker.moveAccumulationGain());
        assertTrue(tracker.isEmpty());
    }

    private static void earlyReapplicationCancelsTheReset() {
        ActionBattlePoisonTracker tracker = completedAt120();
        assertTrue(tracker.applyMove(300L, false, 8));
        assertEquals(-1L, tracker.cleanResetEndTick());
        assertEquals(1, tracker.activeState().orElseThrow().accumulation());
        assertEquals(8, tracker.moveAccumulationGain());
    }

    private static void toxicRejectsReapplication() {
        ActionBattlePoisonTracker tracker = new ActionBattlePoisonTracker();
        tracker.applyMove(0L, false, 9);
        tracker.applyMove(1L, false, 98);
        assertSame(ActionBattlePoisonRules.PoisonLevel.TOXIC, tracker.activeState().orElseThrow().level());
        assertFalse(tracker.applyMove(2L, false, 9));
        assertEquals(99, tracker.activeState().orElseThrow().accumulation());
    }

    private static ActionBattlePoisonTracker completedAt120() {
        ActionBattlePoisonTracker tracker = new ActionBattlePoisonTracker();
        tracker.applyMove(0L, false, 9);
        tracker.tick(120L);
        return tracker;
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
