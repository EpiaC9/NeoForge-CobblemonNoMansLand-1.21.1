package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleDeterioratingShieldState;
import net.epiac9.cobblemonnml.battle.action.ActionBattleStatResolver;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;

public final class ActionBattleFireStateTest {
    private static final double EPSILON = 0.000001D;

    private ActionBattleFireStateTest() {}

    public static void main(String[] args) {
        progressesThroughBuildUpCindersAndBurn();
        retainsFractionalPressure();
        delaysThenDecaysWithoutSkippingIntervals();
        pressureDuringDecayRestartsTheDelay();
        burnDoesNotRefreshAndExpiresCompletely();
        resolvesTypingWithFirePrecedence();
        ownsOnlyFirePokemonAttackStages();
        hazeSuppressesWithoutClearingFireState();
        burnTransitionCanEstablishANewBonusAfterHaze();
        modifiesOnlyIncomingFireDamageDuringBurn();
        retainsFractionalPressureThroughShieldPenetration();
        combinesGenericAndFireOwnedStages();
    }

    private static void progressesThroughBuildUpCindersAndBurn() {
        ActionBattleFireState state = new ActionBattleFireState();
        state.applyPressure(20.0D, 0L, false, false);
        assertState(state, 20.0D, ActionBattleFireState.Phase.BUILDUP);
        state.applyPressure(20.0D, 1L, false, false);
        assertState(state, 40.0D, ActionBattleFireState.Phase.BUILDUP);
        state.applyPressure(20.0D, 2L, false, false);
        assertState(state, 60.0D, ActionBattleFireState.Phase.CINDERS);
        state.applyPressure(20.0D, 3L, false, false);
        assertState(state, 80.0D, ActionBattleFireState.Phase.CINDERS);
        state.applyPressure(20.0D, 4L, false, false);
        assertState(state, 100.0D, ActionBattleFireState.Phase.BURN);
        assertEquals(184L, state.burnEndTick());
    }

    private static void retainsFractionalPressure() {
        ActionBattleFireState state = new ActionBattleFireState();
        state.applyPressure(1.6D, 10L, false, false);
        assertEquals(1.6D, state.pressure());
        assertEquals(370L, state.decayDelayEndTick());
    }

    private static void delaysThenDecaysWithoutSkippingIntervals() {
        ActionBattleFireState state = stateAt(60.0D, 0L);
        state.tick(360L);
        assertEquals(55.0D, state.pressure());
        state.tick(380L);
        assertEquals(50.0D, state.pressure());
        state.tick(400L);
        assertState(state, 45.0D, ActionBattleFireState.Phase.BUILDUP);
        state.tick(600L);
        assertTrue(state.isEmpty());
    }

    private static void pressureDuringDecayRestartsTheDelay() {
        ActionBattleFireState state = stateAt(60.0D, 0L);
        state.tick(380L);
        state.applyPressure(20.0D, 381L, false, false);
        assertEquals(70.0D, state.pressure());
        state.tick(741L);
        assertEquals(65.0D, state.pressure());
        state.tick(761L);
        assertEquals(60.0D, state.pressure());
    }

    private static void burnDoesNotRefreshAndExpiresCompletely() {
        ActionBattleFireState state = stateAt(100.0D, 50L);
        long originalEnd = state.burnEndTick();
        state.applyPressure(20.0D, 80L, false, false);
        assertEquals(100.0D, state.pressure());
        assertEquals(originalEnd, state.burnEndTick());
        state.tick(originalEnd);
        assertTrue(state.isEmpty());
        assertEquals(0.0D, state.pressure());
        assertEquals(0, state.ownedAttackStages());
    }

    private static void resolvesTypingWithFirePrecedence() {
        assertSame(ActionBattleFireRules.TargetInteraction.FIRE_POSITIVE, ActionBattleFireRules.targetInteraction(true, false));
        assertSame(ActionBattleFireRules.TargetInteraction.FIRE_POSITIVE, ActionBattleFireRules.targetInteraction(true, true));
        assertSame(ActionBattleFireRules.TargetInteraction.IMMUNE, ActionBattleFireRules.targetInteraction(false, true));
        assertSame(ActionBattleFireRules.TargetInteraction.HARMFUL, ActionBattleFireRules.targetInteraction(false, false));
    }

    private static void ownsOnlyFirePokemonAttackStages() {
        ActionBattleFireState normal = stateAt(60.0D, 0L);
        assertEquals(0, normal.ownedAttackStages());
        ActionBattleFireState fire = new ActionBattleFireState();
        fire.applyPressure(60.0D, 0L, true, false);
        assertEquals(1, fire.ownedAttackStages());
        fire.applyPressure(40.0D, 1L, true, false);
        assertEquals(2, fire.ownedAttackStages());
    }

    private static void hazeSuppressesWithoutClearingFireState() {
        ActionBattleFireState state = new ActionBattleFireState();
        state.applyPressure(60.0D, 0L, true, false);
        state.suppressFireBonusByHaze();
        state.tick(20L);
        assertState(state, 60.0D, ActionBattleFireState.Phase.CINDERS);
        assertEquals(0, state.ownedAttackStages());
        assertTrue(state.fireBonusSuppressedByHaze());
    }

    private static void burnTransitionCanEstablishANewBonusAfterHaze() {
        ActionBattleFireState state = new ActionBattleFireState();
        state.applyPressure(60.0D, 0L, true, false);
        state.suppressFireBonusByHaze();
        state.applyPressure(40.0D, 1L, true, false);
        assertSame(ActionBattleFireState.Phase.BURN, state.phase());
        assertEquals(2, state.ownedAttackStages());
        assertFalse(state.fireBonusSuppressedByHaze());
    }

    private static void modifiesOnlyIncomingFireDamageDuringBurn() {
        assertEquals(120.0D, ActionBattleFireRules.modifyIncomingDamage(100.0D, true, true));
        assertEquals(100.0D, ActionBattleFireRules.modifyIncomingDamage(100.0D, false, true));
        assertEquals(100.0D, ActionBattleFireRules.modifyIncomingDamage(100.0D, true, false));
    }

    private static void retainsFractionalPressureThroughShieldPenetration() {
        double[] expected = { 0.0D, 1.6D, 3.2D, 4.8D, 6.4D, 9.0D, 12.0D, 15.0D, 18.0D };
        ActionBattleDeterioratingShieldState shield = new ActionBattleDeterioratingShieldState();
        for (double pressure : expected) {
            shield.increaseLevel();
            assertEquals(pressure, ActionBattleFireRules.NORMAL_PRESSURE * shield.effectPenetrationMultiplier());
        }
    }

    private static void combinesGenericAndFireOwnedStages() {
        assertEquals(3, ActionBattleStatResolver.combineStages(ActionBattleStat.ATTACK, 2, 1));
        assertEquals(6, ActionBattleStatResolver.combineStages(ActionBattleStat.ATTACK, 5, 2));
        assertEquals(-6, ActionBattleStatResolver.combineStages(ActionBattleStat.ATTACK, -5, -2));
        assertEquals(2, ActionBattleStatResolver.combineStages(ActionBattleStat.DEFENSE, 2, 0));
    }

    private static ActionBattleFireState stateAt(double pressure, long tick) {
        ActionBattleFireState state = new ActionBattleFireState();
        state.applyPressure(pressure, tick, false, false);
        return state;
    }

    private static void assertState(ActionBattleFireState state, double pressure, ActionBattleFireState.Phase phase) {
        assertEquals(pressure, state.pressure());
        assertSame(phase, state.phase());
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
