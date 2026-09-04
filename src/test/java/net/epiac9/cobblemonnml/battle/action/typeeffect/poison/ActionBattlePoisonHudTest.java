package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

public final class ActionBattlePoisonHudTest {
    private ActionBattlePoisonHudTest() {}

    public static void main(String[] args) {
        preToxicLevelsUseAccumulationOutOfNinetyNine();
        toxicUsesItsRemainingFixedDuration();
        backwardDecayChangesTheStatusIdImmediately();
        particleMixShiftsFromGreenToPurple();
    }

    private static void particleMixShiftsFromGreenToPurple() {
        assertEquals(1L, ActionBattlePoisonVisuals.greenParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON));
        assertEquals(0L, ActionBattlePoisonVisuals.purpleParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON));
        assertEquals(1L, ActionBattlePoisonVisuals.greenParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON_LV1));
        assertEquals(1L, ActionBattlePoisonVisuals.purpleParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON_LV1));
        assertEquals(1L, ActionBattlePoisonVisuals.greenParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON_LV2));
        assertEquals(2L, ActionBattlePoisonVisuals.purpleParticleCount(ActionBattlePoisonRules.PoisonLevel.POISON_LV2));
        assertEquals(0L, ActionBattlePoisonVisuals.greenParticleCount(ActionBattlePoisonRules.PoisonLevel.TOXIC));
        assertEquals(3L, ActionBattlePoisonVisuals.purpleParticleCount(ActionBattlePoisonRules.PoisonLevel.TOXIC));
    }

    private static void preToxicLevelsUseAccumulationOutOfNinetyNine() {
        assertHud(ActionBattlePoisonRules.PoisonLevel.POISON, 1, 0L, "TYPE_POISON", 1L, 99L);
        assertHud(ActionBattlePoisonRules.PoisonLevel.POISON_LV1, 33, 0L, "TYPE_POISON_LV1", 33L, 99L);
        assertHud(ActionBattlePoisonRules.PoisonLevel.POISON_LV2, 66, 0L, "TYPE_POISON_LV2", 66L, 99L);
        assertHud(ActionBattlePoisonRules.PoisonLevel.POISON_LV2, 98, 0L, "TYPE_POISON_LV2", 98L, 99L);
    }

    private static void toxicUsesItsRemainingFixedDuration() {
        assertHud(ActionBattlePoisonRules.PoisonLevel.TOXIC, 99, 180L, "TYPE_TOXIC", 180L, 180L);
        assertHud(ActionBattlePoisonRules.PoisonLevel.TOXIC, 99, 75L, "TYPE_TOXIC", 75L, 180L);
    }

    private static void backwardDecayChangesTheStatusIdImmediately() {
        assertEquals("TYPE_POISON", ActionBattlePoisonVisuals.hudStatusId(
                ActionBattlePoisonRules.levelForAccumulation(28)));
    }

    private static void assertHud(ActionBattlePoisonRules.PoisonLevel level, int accumulation, long toxicRemaining,
                                  String id, long remaining, long duration) {
        assertEquals(id, ActionBattlePoisonVisuals.hudStatusId(level));
        assertEquals(remaining, ActionBattlePoisonVisuals.hudRemaining(level, accumulation, toxicRemaining));
        assertEquals(duration, ActionBattlePoisonVisuals.hudDuration(level));
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
