package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

public final class ActionBattleIceHudTest {
    private ActionBattleIceHudTest() {}

    public static void main(String[] args) {
        chillUsesExactHitProgress();
        freezeUsesTheCurrentCycleRequirement();
        frostbiteUsesItsFixedTimer();
        freezeRemainsVisibleWithAnEmptyProgressRing();
    }

    private static void chillUsesExactHitProgress() {
        assertEquals("TYPE_ICE_CHILL", ActionBattleIceRules.hudStatusId(ActionBattleIceState.Phase.CHILL));
        assertEquals(1L, ActionBattleIceRules.hudRemaining(ActionBattleIceState.Phase.CHILL, 1, 0L));
        assertEquals(3L, ActionBattleIceRules.hudDuration(ActionBattleIceState.Phase.CHILL, 3));
    }

    private static void freezeUsesTheCurrentCycleRequirement() {
        assertEquals("TYPE_ICE_FREEZE", ActionBattleIceRules.hudStatusId(ActionBattleIceState.Phase.FREEZE));
        assertEquals(3L, ActionBattleIceRules.hudRemaining(ActionBattleIceState.Phase.FREEZE, 3, 0L));
        assertEquals(7L, ActionBattleIceRules.hudDuration(ActionBattleIceState.Phase.FREEZE, 7));
        assertEquals(0L, ActionBattleIceRules.hudRemaining(ActionBattleIceState.Phase.FREEZE, 0, 0L));
    }

    private static void frostbiteUsesItsFixedTimer() {
        assertEquals("TYPE_ICE_FROSTBITE", ActionBattleIceRules.hudStatusId(ActionBattleIceState.Phase.FROSTBITE));
        assertEquals(179L, ActionBattleIceRules.hudRemaining(ActionBattleIceState.Phase.FROSTBITE, 0, 179L));
        assertEquals(180L, ActionBattleIceRules.hudDuration(ActionBattleIceState.Phase.FROSTBITE, 9));
    }

    private static void freezeRemainsVisibleWithAnEmptyProgressRing() {
        assertTrue(ActionBattleIceRules.shouldDisplayHudState("TYPE_ICE_FREEZE", 0L));
        assertFalse(ActionBattleIceRules.shouldDisplayHudState("TYPE_ICE_CHILL", 0L));
        assertFalse(ActionBattleIceRules.shouldDisplayHudState("TYPE_ICE_FROSTBITE", 0L));
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }


    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
