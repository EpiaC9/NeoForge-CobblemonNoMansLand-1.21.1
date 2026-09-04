package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;

import java.util.UUID;

public final class ActionBattleSleepRulesTest {
    private static final double EPSILON = 0.000001D;

    private ActionBattleSleepRulesTest() {}

    public static void main(String[] args) {
        durationRollUsesTheExistingThreeToNineSecondRange();
        sleepingDamageUsesTheApprovedWakeTable();
        nonSleepingDamageIsUnchanged();
        positiveDamageWakesButZeroDamageDoesNot();
        recallPreservesExistingSleep();
    }

    private static void durationRollUsesTheExistingThreeToNineSecondRange() {
        assertEquals(60, ActionBattleSleepWakeRules.sleepDurationTicksFromRoll(0));
        assertEquals(120, ActionBattleSleepWakeRules.sleepDurationTicksFromRoll(3));
        assertEquals(180, ActionBattleSleepWakeRules.sleepDurationTicksFromRoll(6));
    }

    private static void sleepingDamageUsesTheApprovedWakeTable() {
        assertEquals(1.20F, ActionBattleSleepWakeRules.damageMultiplier(true, false, false));
        assertEquals(1.25F, ActionBattleSleepWakeRules.damageMultiplier(true, true, false));
        assertEquals(1.25F, ActionBattleSleepWakeRules.damageMultiplier(true, false, true));
        assertEquals(1.50F, ActionBattleSleepWakeRules.damageMultiplier(true, true, true));
    }

    private static void nonSleepingDamageIsUnchanged() {
        assertEquals(1.0F, ActionBattleSleepWakeRules.damageMultiplier(false, true, true));
    }

    private static void positiveDamageWakesButZeroDamageDoesNot() {
        ActionBattleSleepController.WakePlan plan = ActionBattleSleepController.planDamagingWake(true, true, false);
        assertTrue(ActionBattleSleepController.shouldWakeAfterDamage(plan, 100, 80));
        assertFalse(ActionBattleSleepController.shouldWakeAfterDamage(plan, 100, 100));
        assertFalse(ActionBattleSleepController.shouldWakeAfterDamage(ActionBattleSleepController.WakePlan.NONE, 100, 80));
    }

    private static void recallPreservesExistingSleep() {
        UUID battle = UUID.randomUUID();
        UUID pokemon = UUID.randomUUID();
        ActionBattleEffectController effects = ActionBattleEffectController.global();
        assertTrue(effects.beginSleep(battle, pokemon, 0L, 120L));
        assertEquals(120L, effects.statusDurationTicks(battle, pokemon, ActionBattleStatus.SLEEP, 0L));
        effects.onPokemonRecalled(battle, pokemon, 40L);
        assertTrue(effects.hasStatus(battle, pokemon, ActionBattleStatus.SLEEP, 40L));
        assertEquals(80L, effects.statusRemainingTicks(battle, pokemon, ActionBattleStatus.SLEEP, 40L));
        effects.clearBattle(battle);
    }

    private static void assertEquals(float expected, float actual) {
        if (Math.abs(expected - actual) > EPSILON) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
