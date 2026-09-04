package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;

import java.util.UUID;

public final class ActionBattlePoisonDeliveryTest {
    private ActionBattlePoisonDeliveryTest() {}

    public static void main(String[] args) {
        steelIsImmuneButPoisonTypingIsNot();
        freshPenetrationUsesStrictChance();
        directGainUsesTheExactMultiplierTable();
        shieldHistoryDoesNothingWithoutAnActiveStance();
    }

    private static void steelIsImmuneButPoisonTypingIsNot() {
        assertFalse(ActionBattlePoisonController.canReceivePoison(true));
        assertTrue(ActionBattlePoisonController.canReceivePoison(false));
    }

    private static void freshPenetrationUsesStrictChance() {
        assertFalse(ActionBattlePoisonController.passesPenetration(0.20D, 0.20D));
        assertTrue(ActionBattlePoisonController.passesPenetration(0.20D, 0.199999D));
    }

    private static void directGainUsesTheExactMultiplierTable() {
        double[] multipliers = {0.00D, 0.08D, 0.16D, 0.24D, 0.32D, 0.45D, 0.60D, 0.75D, 0.90D};
        int[] expected = {0, 1, 1, 2, 3, 4, 5, 7, 8};
        for (int index = 0; index < multipliers.length; index++) {
            assertEquals(expected[index], ActionBattlePoisonController.penetratedDirectGain(9, multipliers[index]));
        }
        assertEquals(1, ActionBattlePoisonController.penetratedDirectGain(1, 0.01D));
    }

    private static void shieldHistoryDoesNothingWithoutAnActiveStance() {
        ActionBattleProtectController protect = new ActionBattleProtectController();
        UUID battle = UUID.randomUUID();
        UUID pokemon = UUID.randomUUID();
        protect.startBalefulBunker(battle, pokemon, 0L);
        protect.startBalefulBunker(battle, pokemon, 1L);
        assertEquals(0.05D, ActionBattlePoisonController.freshPenetrationChance(protect, battle, pokemon, 1L));
        assertEquals(1, ActionBattlePoisonController.penetratedDirectGain(protect, battle, pokemon, 1L, 9));
        assertEquals(1.00D, ActionBattlePoisonController.freshPenetrationChance(protect, battle, pokemon, 41L));
        assertEquals(9, ActionBattlePoisonController.penetratedDirectGain(protect, battle, pokemon, 41L, 9));
        assertEquals(2, protect.deterioratingShieldLevel(battle, pokemon));
    }

    private static void assertEquals(double expected, double actual) {
        if (Math.abs(expected - actual) > 0.000001D) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
