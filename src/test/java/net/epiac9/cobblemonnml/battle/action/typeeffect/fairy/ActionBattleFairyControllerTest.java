package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;

import java.util.UUID;

public final class ActionBattleFairyControllerTest {
    private ActionBattleFairyControllerTest() {}

    public static void main(String[] args) {
        steelTypingRejectsDrowsyBeforeFairyRouting();
        dragonCompletionPrecedesFairyCompletion();
        deterioratingShieldUsesExactPenetrationThresholds();
        onlyEnemyTargetCategoriesQualify();
        shieldHistoryDoesNotReduceDrowsyWithoutAnActiveStance();
    }

    private static void shieldHistoryDoesNotReduceDrowsyWithoutAnActiveStance() {
        ActionBattleProtectController protect = new ActionBattleProtectController();
        UUID battle = UUID.randomUUID();
        UUID pokemon = UUID.randomUUID();
        protect.startBalefulBunker(battle, pokemon, 0L);
        protect.startBalefulBunker(battle, pokemon, 1L);
        assertEquals(0.05D, ActionBattleFairyController.penetrationChance(protect, battle, pokemon, 1L));
        assertEquals(1.00D, ActionBattleFairyController.penetrationChance(protect, battle, pokemon, 41L));
        assertEquals(2, protect.deterioratingShieldLevel(battle, pokemon));
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
    private static void assertEquals(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001D) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
