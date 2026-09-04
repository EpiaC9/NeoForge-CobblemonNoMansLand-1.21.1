package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

public final class ActionBattleFairyControllerTest {
    private ActionBattleFairyControllerTest() {}

    public static void main(String[] args) {
        steelTypingRejectsDrowsyBeforeFairyRouting();
        dragonCompletionPrecedesFairyCompletion();
        deterioratingShieldUsesExactPenetrationThresholds();
        onlyEnemyTargetCategoriesQualify();
    }

    private static void steelTypingRejectsDrowsyBeforeFairyRouting() {
        assertFalse(ActionBattleFairyController.canReceiveDrowsy(true));
        assertTrue(ActionBattleFairyController.canReceiveDrowsy(false));
    }

    private static void dragonCompletionPrecedesFairyCompletion() {
        assertSame(ActionBattleDrowsyTracker.CompletionRoute.DRAGON_UPROAR,
                ActionBattleFairyController.completionRoute(true, true));
        assertSame(ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF,
                ActionBattleFairyController.completionRoute(false, true));
        assertSame(ActionBattleDrowsyTracker.CompletionRoute.SLEEP,
                ActionBattleFairyController.completionRoute(false, false));
    }

    private static void deterioratingShieldUsesExactPenetrationThresholds() {
        double[] chances = {1.00D, 0.00D, 0.05D, 0.10D, 0.20D, 0.30D, 0.60D, 0.70D, 0.80D, 0.90D};
        for (int level = 1; level <= 9; level++) {
            double chance = chances[level];
            assertFalse(ActionBattleFairyController.passesPenetration(chance, chance));
            if (chance > 0.0D) assertTrue(ActionBattleFairyController.passesPenetration(chance, chance - 0.001D));
        }
        assertTrue(ActionBattleFairyController.passesPenetration(chances[0], 0.999D));
    }

    private static void onlyEnemyTargetCategoriesQualify() {
        assertTrue(ActionBattleFairyController.isEnemyTargetCategory("adjacentPokemon"));
        assertTrue(ActionBattleFairyController.isEnemyTargetCategory("normal"));
        assertFalse(ActionBattleFairyController.isEnemyTargetCategory("self"));
        assertFalse(ActionBattleFairyController.isEnemyTargetCategory("ally"));
        assertFalse(ActionBattleFairyController.isEnemyTargetCategory("allAllies"));
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
