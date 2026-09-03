package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleDeterioratingShieldState;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaPreset;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaState;

import java.util.UUID;

public final class ActionBattleIceDeliveryTest {
    private static final double EPSILON = 0.000001D;

    private ActionBattleIceDeliveryTest() {}

    public static void main(String[] args) {
        exposesTheExactShieldPenetrationTable();
        acceptsOnlyRollsStrictlyBelowTheChance();
        hailAcceptsOnlyBattlePokemonInsideItsArea();
        directApplicationsRequireSuccessfulHpDamage();
    }

    private static void exposesTheExactShieldPenetrationTable() {
        double[] expected = { 0.00D, 0.05D, 0.10D, 0.20D, 0.30D, 0.60D, 0.70D, 0.80D, 0.90D };
        ActionBattleDeterioratingShieldState shield = new ActionBattleDeterioratingShieldState();
        for (double chance : expected) {
            shield.increaseLevel();
            assertEquals(chance, shield.effectPenetrationChance());
        }
        shield.clear();
        assertEquals(1.00D, shield.effectPenetrationChance());
    }

    private static void acceptsOnlyRollsStrictlyBelowTheChance() {
        assertFalse(ActionBattleIceController.passesPenetration(0.00D, 0.00D));
        assertTrue(ActionBattleIceController.passesPenetration(0.20D, 0.199999D));
        assertFalse(ActionBattleIceController.passesPenetration(0.20D, 0.20D));
        assertTrue(ActionBattleIceController.passesPenetration(1.00D, 0.999999D));
        assertFalse(ActionBattleIceController.passesPenetration(1.00D, 1.00D));
    }

    private static void hailAcceptsOnlyBattlePokemonInsideItsArea() {
        UUID battleId = UUID.randomUUID();
        UUID pokemonId = UUID.randomUUID();
        ActionBattlePersistentAreaState area = new ActionBattlePersistentAreaState(
                UUID.randomUUID(), battleId, UUID.randomUUID(), "hail", new ActionBattlePosition(0.0D, 10.0D, 0.0D),
                new ActionBattlePersistentAreaPreset(7.0D, 6.0D, 180, 20, true));
        assertTrue(ActionBattleIceRules.isValidAreaApplication(area, battleId, pokemonId, 6.9D, 12.0D, 0.0D));
        assertFalse(ActionBattleIceRules.isValidAreaApplication(area, battleId, pokemonId, 7.1D, 12.0D, 0.0D));
        assertFalse(ActionBattleIceRules.isValidAreaApplication(area, UUID.randomUUID(), pokemonId, 0.0D, 12.0D, 0.0D));
        assertFalse(ActionBattleIceRules.isValidAreaApplication(area, battleId, null, 0.0D, 12.0D, 0.0D));
    }

    private static void directApplicationsRequireSuccessfulHpDamage() {
        assertTrue(ActionBattleIceRules.isQualifyingDamagingHit(true, 100, 80));
        assertFalse(ActionBattleIceRules.isQualifyingDamagingHit(false, 100, 80));
        assertFalse(ActionBattleIceRules.isQualifyingDamagingHit(true, 100, 100));
        assertFalse(ActionBattleIceRules.isQualifyingDamagingHit(true, 0, 0));
    }

    private static void assertEquals(double expected, double actual) {
        if (Math.abs(expected - actual) > EPSILON) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
