package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

public final class ActionBattlePoisonStateTest {
    private ActionBattlePoisonStateTest() {}

    public static void main(String[] args) {
        boundariesAreLiteral();
        freshApplicationAndSameLevelGainPreserveDeadline();
        thresholdCrossingsResetTheLevelDeadline();
        passiveGainUsesTheCurrentLevel();
        expiryDecaysBeforePassiveAndRemovesAtZero();
        toxicIsFixedAndCannotBeRefreshed();
        poisonTypingInvertsOwnedSpecialAttack();
        hazeSuppressesOnlyTheOwnedStat();
        toxicAmplifiesPoisonMoveDamage();
        directGainClampsWithoutIntegerOverflow();
    }

    private static void directGainClampsWithoutIntegerOverflow() {
        ActionBattlePoisonState state = stateAt(32, 0L);
        state.applyDirectGain(Integer.MAX_VALUE, 1L);
        assertEquals(99, state.accumulation());
        assertSame(ActionBattlePoisonRules.PoisonLevel.TOXIC, state.level());
    }

    private static void boundariesAreLiteral() {
        int[] values = {0, 1, 32, 33, 65, 66, 98, 99};
        ActionBattlePoisonRules.PoisonLevel[] levels = {
                ActionBattlePoisonRules.PoisonLevel.NONE,
                ActionBattlePoisonRules.PoisonLevel.POISON,
                ActionBattlePoisonRules.PoisonLevel.POISON,
                ActionBattlePoisonRules.PoisonLevel.POISON_LV1,
                ActionBattlePoisonRules.PoisonLevel.POISON_LV1,
                ActionBattlePoisonRules.PoisonLevel.POISON_LV2,
                ActionBattlePoisonRules.PoisonLevel.POISON_LV2,
                ActionBattlePoisonRules.PoisonLevel.TOXIC
        };
        for (int index = 0; index < values.length; index++) {
            assertSame(levels[index], ActionBattlePoisonRules.levelForAccumulation(values[index]));
        }
    }

    private static void freshApplicationAndSameLevelGainPreserveDeadline() {
        ActionBattlePoisonState state = new ActionBattlePoisonState();
        assertTrue(state.applyDirectGain(1, 10L));
        assertEquals(1, state.accumulation());
        assertEquals(130L, state.levelEndTick());
        assertTrue(state.applyDirectGain(9, 20L));
        assertEquals(10, state.accumulation());
        assertEquals(130L, state.levelEndTick());
    }

    private static void thresholdCrossingsResetTheLevelDeadline() {
        ActionBattlePoisonState state = stateAt(32, 0L);
        state.applyDirectGain(40, 50L);
        assertSame(ActionBattlePoisonRules.PoisonLevel.POISON_LV2, state.level());
        assertEquals(72, state.accumulation());
        assertEquals(170L, state.levelEndTick());
        state.applyDirectGain(40, 60L);
        assertSame(ActionBattlePoisonRules.PoisonLevel.TOXIC, state.level());
        assertEquals(99, state.accumulation());
        assertEquals(240L, state.levelEndTick());
    }

    private static void passiveGainUsesTheCurrentLevel() {
        assertPassive(1, 3);
        assertPassive(33, 6);
        assertPassive(66, 9);
    }

    private static void expiryDecaysBeforePassiveAndRemovesAtZero() {
        ActionBattlePoisonState weak = stateAt(4, 0L);
        assertSame(ActionBattlePoisonState.TickResult.COMPLETED_NATURALLY, weak.tick(120L));
        assertEquals(0, weak.accumulation());

        ActionBattlePoisonState levelOne = stateAt(33, 0L);
        assertSame(ActionBattlePoisonState.TickResult.CHANGED, levelOne.tick(120L));
        assertEquals(28, levelOne.accumulation());
        assertSame(ActionBattlePoisonRules.PoisonLevel.POISON, levelOne.level());
        assertEquals(240L, levelOne.levelEndTick());

        ActionBattlePoisonState levelTwo = stateAt(66, 0L);
        levelTwo.tick(120L);
        assertEquals(60, levelTwo.accumulation());
        assertSame(ActionBattlePoisonRules.PoisonLevel.POISON_LV1, levelTwo.level());
    }

    private static void toxicIsFixedAndCannotBeRefreshed() {
        ActionBattlePoisonState state = stateAt(99, 0L);
        assertEquals(180L, state.levelEndTick());
        assertFalse(state.applyDirectGain(9, 50L));
        assertEquals(180L, state.levelEndTick());
        assertSame(ActionBattlePoisonState.TickResult.NONE, state.tick(100L));
        assertEquals(99, state.accumulation());
        assertSame(ActionBattlePoisonState.TickResult.COMPLETED_NATURALLY, state.tick(180L));
        assertEquals(0, state.accumulation());
    }

    private static void poisonTypingInvertsOwnedSpecialAttack() {
        assertEquals(-1, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.POISON, false));
        assertEquals(-1, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.POISON_LV1, false));
        assertEquals(-1, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.POISON_LV2, false));
        assertEquals(-2, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.TOXIC, false));
        assertEquals(1, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.POISON, true));
        assertEquals(1, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.POISON_LV2, true));
        assertEquals(2, ActionBattlePoisonRules.specialAttackStages(ActionBattlePoisonRules.PoisonLevel.TOXIC, true));
    }

    private static void hazeSuppressesOnlyTheOwnedStat() {
        ActionBattlePoisonState state = new ActionBattlePoisonState(true);
        state.applyDirectGain(33, 0L);
        state.suppressSpecialAttackByHaze();
        assertEquals(33, state.accumulation());
        assertEquals(0, state.ownedSpecialAttackStages());
        assertTrue(state.statSuppressedByHaze());
    }

    private static void toxicAmplifiesPoisonMoveDamage() {
        assertEquals(120.0, ActionBattlePoisonRules.modifyIncomingDamage(100.0, true, true));
        assertEquals(100.0, ActionBattlePoisonRules.modifyIncomingDamage(100.0, false, true));
        assertEquals(100.0, ActionBattlePoisonRules.modifyIncomingDamage(100.0, true, false));
    }

    private static void assertPassive(int start, int gain) {
        ActionBattlePoisonState state = stateAt(start, 0L);
        state.tick(20L);
        assertEquals(Math.min(99, start + gain), state.accumulation());
    }

    private static ActionBattlePoisonState stateAt(int accumulation, long tick) {
        ActionBattlePoisonState state = new ActionBattlePoisonState();
        state.applyDirectGain(accumulation, tick);
        return state;
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertEquals(double expected, double actual) {
        if (Double.compare(expected, actual) != 0) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
