package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

public final class ActionBattleIceStateTest {
    private static final double EPSILON = 0.000001D;

    private ActionBattleIceStateTest() {}

    public static void main(String[] args) {
        progressesFromChillThroughFreezeToFrostbite();
        lateHitStartsANewFixedStackWindow();
        intermediateHitsDoNotExtendTheStackWindow();
        chillAndFreezeShareTheOriginalLifecycle();
        frostbiteDoesNotAcceptOrRefreshApplications();
        expiryIncreasesTheRequirementWithoutACap();
        iceFreeResetRestoresTheBaseRequirement();
        earlyReapplicationKeepsTheIncreasedRequirement();
        ownsNormalAndIceTypedDefenseStages();
        hazeSuppressesOnlyTheOwnedDefenseContribution();
        frostbiteTransitionMayEstablishANewContribution();
        modifiesOnlyIncomingIceDamageDuringFrostbite();
    }

    private static void progressesFromChillThroughFreezeToFrostbite() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.CHILL, 1, 3);
        tracker.applyApplication(10L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.CHILL, 2, 3);
        tracker.applyApplication(20L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.FREEZE, 0, 3);
        tracker.applyApplication(30L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.FREEZE, 1, 3);
        tracker.applyApplication(40L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.FREEZE, 2, 3);
        tracker.applyApplication(50L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.FROSTBITE, 0, 3);
        assertEquals(230L, tracker.activeState().orElseThrow().frostbiteEndTick());
    }

    private static void lateHitStartsANewFixedStackWindow() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        tracker.applyApplication(20L, false, false);
        tracker.applyApplication(60L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.CHILL, 1, 3);
        assertEquals(60L, tracker.activeState().orElseThrow().stackWindowStartTick());
    }

    private static void intermediateHitsDoNotExtendTheStackWindow() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        tracker.applyApplication(59L, false, false);
        assertEquals(0L, tracker.activeState().orElseThrow().stackWindowStartTick());
        tracker.applyApplication(60L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.CHILL, 1, 3);
    }

    private static void chillAndFreezeShareTheOriginalLifecycle() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        tracker.applyApplication(10L, false, false);
        tracker.applyApplication(20L, false, false);
        assertEquals(360L, tracker.activeState().orElseThrow().lifecycleEndTick());
        tracker.applyApplication(200L, false, false);
        assertEquals(360L, tracker.activeState().orElseThrow().lifecycleEndTick());
        tracker.tick(359L);
        assertTrue(tracker.activeState().isPresent());
        tracker.tick(360L);
        assertTrue(tracker.activeState().isEmpty());
        assertEquals(4, tracker.hitsRequired());
        assertEquals(720L, tracker.resetEndTick());
    }

    private static void frostbiteDoesNotAcceptOrRefreshApplications() {
        ActionBattleIceTracker tracker = frostbitten(false, false);
        long endTick = tracker.activeState().orElseThrow().frostbiteEndTick();
        assertFalse(tracker.applyApplication(60L, false, false));
        assertEquals(endTick, tracker.activeState().orElseThrow().frostbiteEndTick());
        tracker.tick(endTick - 1L);
        assertTrue(tracker.activeState().isPresent());
        tracker.tick(endTick);
        assertTrue(tracker.activeState().isEmpty());
        assertEquals(4, tracker.hitsRequired());
    }

    private static void expiryIncreasesTheRequirementWithoutACap() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        long tick = 0L;
        for (int expected = 4; expected <= 12; expected++) {
            tracker.applyApplication(tick, false, false);
            tick += ActionBattleIceRules.LIFECYCLE_TICKS;
            tracker.tick(tick);
            assertEquals(expected, tracker.hitsRequired());
            tick++;
        }
    }

    private static void iceFreeResetRestoresTheBaseRequirement() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        tracker.tick(360L);
        tracker.tick(719L);
        assertEquals(4, tracker.hitsRequired());
        tracker.tick(720L);
        assertEquals(3, tracker.hitsRequired());
        assertTrue(tracker.isEmpty());
    }

    private static void earlyReapplicationKeepsTheIncreasedRequirement() {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, false, false);
        tracker.tick(360L);
        tracker.applyApplication(600L, false, false);
        assertState(tracker, ActionBattleIceState.Phase.CHILL, 1, 4);
        assertEquals(-1L, tracker.resetEndTick());
    }

    private static void ownsNormalAndIceTypedDefenseStages() {
        ActionBattleIceTracker normal = frozen(false, false);
        assertEquals(-1, normal.activeState().orElseThrow().ownedDefenseStages());
        normal.applyApplication(30L, false, false);
        normal.applyApplication(40L, false, false);
        normal.applyApplication(50L, false, false);
        assertEquals(-2, normal.activeState().orElseThrow().ownedDefenseStages());

        ActionBattleIceTracker iceTyped = frozen(true, false);
        assertEquals(1, iceTyped.activeState().orElseThrow().ownedDefenseStages());
        iceTyped.applyApplication(30L, true, false);
        iceTyped.applyApplication(40L, true, false);
        iceTyped.applyApplication(50L, true, false);
        assertEquals(2, iceTyped.activeState().orElseThrow().ownedDefenseStages());
    }

    private static void hazeSuppressesOnlyTheOwnedDefenseContribution() {
        ActionBattleIceTracker tracker = frozen(false, false);
        tracker.suppressDefenseContributionByHaze();
        assertState(tracker, ActionBattleIceState.Phase.FREEZE, 0, 3);
        assertEquals(0, tracker.activeState().orElseThrow().ownedDefenseStages());
        assertTrue(tracker.activeState().orElseThrow().defenseContributionSuppressedByHaze());
        assertEquals(360L, tracker.activeState().orElseThrow().lifecycleEndTick());
    }

    private static void frostbiteTransitionMayEstablishANewContribution() {
        ActionBattleIceTracker tracker = frozen(false, false);
        tracker.suppressDefenseContributionByHaze();
        tracker.applyApplication(30L, false, false);
        tracker.applyApplication(40L, false, false);
        tracker.applyApplication(50L, false, false);
        assertEquals(-2, tracker.activeState().orElseThrow().ownedDefenseStages());
        assertFalse(tracker.activeState().orElseThrow().defenseContributionSuppressedByHaze());

        ActionBattleIceTracker hazeStillActive = frozen(false, false);
        hazeStillActive.suppressDefenseContributionByHaze();
        hazeStillActive.applyApplication(30L, false, true);
        hazeStillActive.applyApplication(40L, false, true);
        hazeStillActive.applyApplication(50L, false, true);
        assertEquals(0, hazeStillActive.activeState().orElseThrow().ownedDefenseStages());
        assertTrue(hazeStillActive.activeState().orElseThrow().defenseContributionSuppressedByHaze());
    }

    private static void modifiesOnlyIncomingIceDamageDuringFrostbite() {
        assertEquals(120.0D, ActionBattleIceRules.modifyIncomingDamage(100.0D, true, true));
        assertEquals(100.0D, ActionBattleIceRules.modifyIncomingDamage(100.0D, false, true));
        assertEquals(100.0D, ActionBattleIceRules.modifyIncomingDamage(100.0D, true, false));
    }

    private static ActionBattleIceTracker frozen(boolean iceTyped, boolean hazeActive) {
        ActionBattleIceTracker tracker = new ActionBattleIceTracker();
        tracker.applyApplication(0L, iceTyped, hazeActive);
        tracker.applyApplication(10L, iceTyped, hazeActive);
        tracker.applyApplication(20L, iceTyped, hazeActive);
        return tracker;
    }

    private static ActionBattleIceTracker frostbitten(boolean iceTyped, boolean hazeActive) {
        ActionBattleIceTracker tracker = frozen(iceTyped, hazeActive);
        tracker.applyApplication(30L, iceTyped, hazeActive);
        tracker.applyApplication(40L, iceTyped, hazeActive);
        tracker.applyApplication(50L, iceTyped, hazeActive);
        return tracker;
    }

    private static void assertState(ActionBattleIceTracker tracker, ActionBattleIceState.Phase phase,
                                    int currentHits, int hitsRequired) {
        ActionBattleIceState state = tracker.activeState().orElseThrow();
        assertSame(phase, state.phase());
        assertEquals(currentHits, state.currentHits());
        assertEquals(hitsRequired, tracker.activeHitsRequired());
    }

    private static void assertEquals(double expected, double actual) {
        if (Math.abs(expected - actual) > EPSILON) throw new AssertionError("Expected " + expected + " but got " + actual);
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
