package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleStatResolver;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;

import java.util.UUID;

public final class ActionBattleTypeEffectControllerTest {
    private ActionBattleTypeEffectControllerTest() {}

    public static void main(String[] args) {
        tracksPokemonIndependentlyWithinOneSession();
        rejectsOperationsForTheWrongSession();
        replacingTheSessionDiscardsStaleState();
        clearsOnePokemonWithoutClearingOthers();
        ticksRecalledPokemonWithoutAnEntity();
        fireAndIceCoexistWithoutClearingEachOther();
        ticksIceReapplicationHistoryWithoutAnEntity();
        routesOwnedDefenseAndHazeSuppression();
        chainsFireAndIceDamageModifiersIndependently();
        ownsFairyDrowsyAndSpecialDefenseIndependently();
        suppressesOnlyFairySpecialDefenseWithoutEndingCompletion();
        poisonCoexistsAndOwnsOnlySpecialAttack();
        poisonHazeSuppressionPreservesProgressAndResistance();
        toxicAmplifiesOnlyPoisonMoveDamage();
    }

    private static void poisonCoexistsAndOwnsOnlySpecialAttack() {
        Fixture fixture = new Fixture();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 20.0D, 0L, false, false);
        fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, 0L, false, false);
        fixture.controller.applyDrowsy(fixture.session, fixture.pokemon, 0L);
        assertTrue(fixture.controller.applyPoisonMove(fixture.session, fixture.pokemon, 0L, false, 9));
        assertTrue(fixture.controller.fireView(fixture.session, fixture.pokemon, 0L).isPresent());
        assertTrue(fixture.controller.iceView(fixture.session, fixture.pokemon, 0L).isPresent());
        assertTrue(fixture.controller.drowsyView(fixture.session, fixture.pokemon, 0L).isPresent());
        assertTrue(fixture.controller.poisonView(fixture.session, fixture.pokemon, 0L).isPresent());
        assertEquals(-1, fixture.controller.poisonSpecialAttackStages(fixture.session, fixture.pokemon, 0L));
        assertEquals(2, ActionBattleStatResolver.combineStages(ActionBattleStat.SPECIAL_ATTACK, 3,
                fixture.controller.poisonSpecialAttackStages(fixture.session, fixture.pokemon, 0L)));

        Fixture poisonTyped = new Fixture();
        poisonTyped.controller.applyPoisonMove(poisonTyped.session, poisonTyped.pokemon, 0L, true, 9);
        assertEquals(1, poisonTyped.controller.poisonSpecialAttackStages(
                poisonTyped.session, poisonTyped.pokemon, 0L));
    }

    private static void poisonHazeSuppressionPreservesProgressAndResistance() {
        Fixture fixture = new Fixture();
        fixture.controller.applyPoisonMove(fixture.session, fixture.pokemon, 0L, false, 9);
        fixture.controller.suppressPoisonSpecialAttackByHaze(fixture.session, fixture.pokemon);
        ActionBattleTypeEffectState.PoisonView view = fixture.controller
                .poisonView(fixture.session, fixture.pokemon, 0L).orElseThrow();
        assertEquals(1, view.accumulation());
        assertEquals(9, view.moveAccumulationGain());
        assertEquals(120L, view.levelRemainingTicks());
        assertEquals(0, view.ownedSpecialAttackStages());
        assertTrue(view.statSuppressedByHaze());
    }

    private static void toxicAmplifiesOnlyPoisonMoveDamage() {
        Fixture fixture = new Fixture();
        fixture.controller.applyPoisonMove(fixture.session, fixture.pokemon, 0L, true, 9);
        fixture.controller.applyPoisonMove(fixture.session, fixture.pokemon, 1L, true, 98);
        assertEquals(120.0D, fixture.controller.modifyDamage(
                fixture.session, fixture.pokemon, false, false, true, 100.0D, 1L));
        assertEquals(100.0D, fixture.controller.modifyDamage(
                fixture.session, fixture.pokemon, false, false, false, 100.0D, 1L));
    }

    private static void ownsFairyDrowsyAndSpecialDefenseIndependently() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.controller.applyDrowsy(fixture.session, fixture.pokemon, 0L));
        ActionBattleTypeEffectState.DrowsyView drowsy = fixture.controller
                .drowsyView(fixture.session, fixture.pokemon, 20L).orElseThrow();
        assertEquals(160L, drowsy.remainingTicks());
        assertEquals(180L, drowsy.totalDurationTicks());
        assertTrue(fixture.controller.completeDrowsy(fixture.session, fixture.pokemon, 180L, 120,
                net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF));
        assertTrue(fixture.controller.drowsyView(fixture.session, fixture.pokemon, 180L).isEmpty());
        assertEquals(2, fixture.controller.fairySpecialDefenseStages(fixture.session, fixture.pokemon, 200L));
        fixture.controller.tickSession(fixture.session, 300L);
        assertEquals(0, fixture.controller.fairySpecialDefenseStages(fixture.session, fixture.pokemon, 300L));
    }

    private static void suppressesOnlyFairySpecialDefenseWithoutEndingCompletion() {
        Fixture fixture = new Fixture();
        fixture.controller.applyDrowsy(fixture.session, fixture.pokemon, 0L);
        fixture.controller.completeDrowsy(fixture.session, fixture.pokemon, 180L, 120,
                net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF);
        fixture.controller.suppressFairySpecialDefenseByHaze(fixture.session, fixture.pokemon);
        assertEquals(0, fixture.controller.fairySpecialDefenseStages(fixture.session, fixture.pokemon, 200L));
        assertTrue(fixture.controller.fairyCompletionView(fixture.session, fixture.pokemon, 200L).isPresent());
        assertEquals(360, fixture.controller.nextDrowsyDurationTicks(fixture.session, fixture.pokemon));
    }

    private static void tracksPokemonIndependentlyWithinOneSession() {
        Fixture fixture = new Fixture();
        UUID other = UUID.randomUUID();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 20.0D, 0L, false, false);
        fixture.controller.applyFirePressure(fixture.session, other, 60.0D, 0L, true, false);
        assertEquals(20.0D, fixture.controller.fireView(fixture.session, fixture.pokemon, 0L).orElseThrow().pressure());
        assertEquals(60.0D, fixture.controller.fireView(fixture.session, other, 0L).orElseThrow().pressure());
        assertEquals(2, fixture.controller.trackedPokemonCount(fixture.session));
    }

    private static void rejectsOperationsForTheWrongSession() {
        Fixture fixture = new Fixture();
        UUID wrong = UUID.randomUUID();
        assertFalse(fixture.controller.applyFirePressure(wrong, fixture.pokemon, 20.0D, 0L, false, false));
        assertTrue(fixture.controller.fireView(wrong, fixture.pokemon, 0L).isEmpty());
    }

    private static void replacingTheSessionDiscardsStaleState() {
        Fixture fixture = new Fixture();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 20.0D, 0L, false, false);
        UUID nextSession = UUID.randomUUID();
        fixture.controller.guardSession(nextSession);
        assertTrue(fixture.controller.fireView(fixture.session, fixture.pokemon, 0L).isEmpty());
        assertEquals(0, fixture.controller.trackedPokemonCount(nextSession));
    }

    private static void clearsOnePokemonWithoutClearingOthers() {
        Fixture fixture = new Fixture();
        UUID other = UUID.randomUUID();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 20.0D, 0L, false, false);
        fixture.controller.applyFirePressure(fixture.session, other, 20.0D, 0L, false, false);
        fixture.controller.clearPokemon(fixture.session, fixture.pokemon);
        assertTrue(fixture.controller.fireView(fixture.session, fixture.pokemon, 0L).isEmpty());
        assertTrue(fixture.controller.fireView(fixture.session, other, 0L).isPresent());
    }

    private static void ticksRecalledPokemonWithoutAnEntity() {
        Fixture fixture = new Fixture();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 100.0D, 10L, false, false);
        fixture.controller.tickSession(fixture.session, 190L);
        assertTrue(fixture.controller.fireView(fixture.session, fixture.pokemon, 190L).isEmpty());
        assertEquals(0, fixture.controller.trackedPokemonCount(fixture.session));
    }

    private static void fireAndIceCoexistWithoutClearingEachOther() {
        Fixture iceExpiresFirst = new Fixture();
        iceExpiresFirst.controller.applyFirePressure(iceExpiresFirst.session, iceExpiresFirst.pokemon, 20.0D, 0L, false, false);
        iceExpiresFirst.controller.applyIceApplication(iceExpiresFirst.session, iceExpiresFirst.pokemon, 0L, false, false);
        assertTrue(iceExpiresFirst.controller.fireView(iceExpiresFirst.session, iceExpiresFirst.pokemon, 360L).isPresent());
        assertTrue(iceExpiresFirst.controller.iceView(iceExpiresFirst.session, iceExpiresFirst.pokemon, 360L).isEmpty());

        Fixture fireExpiresFirst = new Fixture();
        fireExpiresFirst.controller.applyFirePressure(fireExpiresFirst.session, fireExpiresFirst.pokemon, 100.0D, 0L, false, false);
        fireExpiresFirst.controller.applyIceApplication(fireExpiresFirst.session, fireExpiresFirst.pokemon, 0L, false, false);
        assertTrue(fireExpiresFirst.controller.fireView(fireExpiresFirst.session, fireExpiresFirst.pokemon, 180L).isEmpty());
        assertTrue(fireExpiresFirst.controller.iceView(fireExpiresFirst.session, fireExpiresFirst.pokemon, 180L).isPresent());
    }

    private static void ticksIceReapplicationHistoryWithoutAnEntity() {
        Fixture fixture = new Fixture();
        fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, 0L, false, false);
        fixture.controller.tickSession(fixture.session, 360L);
        assertEquals(4, fixture.controller.iceHitsRequired(fixture.session, fixture.pokemon, 360L));
        assertEquals(1, fixture.controller.trackedPokemonCount(fixture.session));
        fixture.controller.tickSession(fixture.session, 720L);
        assertEquals(3, fixture.controller.iceHitsRequired(fixture.session, fixture.pokemon, 720L));
        assertEquals(0, fixture.controller.trackedPokemonCount(fixture.session));
    }

    private static void routesOwnedDefenseAndHazeSuppression() {
        Fixture fixture = new Fixture();
        fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, 0L, false, false);
        fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, 10L, false, false);
        fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, 20L, false, false);
        assertEquals(-1, fixture.controller.iceDefenseStages(fixture.session, fixture.pokemon, 20L));
        fixture.controller.suppressIceDefenseByHaze(fixture.session, fixture.pokemon);
        assertEquals(0, fixture.controller.iceDefenseStages(fixture.session, fixture.pokemon, 20L));
        assertTrue(fixture.controller.iceView(fixture.session, fixture.pokemon, 20L).orElseThrow().defenseContributionSuppressedByHaze());
    }

    private static void chainsFireAndIceDamageModifiersIndependently() {
        Fixture fixture = new Fixture();
        fixture.controller.applyFirePressure(fixture.session, fixture.pokemon, 100.0D, 0L, false, false);
        for (long tick = 0L; tick < 6L; tick++) {
            fixture.controller.applyIceApplication(fixture.session, fixture.pokemon, tick, false, false);
        }
        assertEquals(120.0D, fixture.controller.modifyDamage(fixture.session, fixture.pokemon, true, false, 100.0D, 5L));
        assertEquals(120.0D, fixture.controller.modifyDamage(fixture.session, fixture.pokemon, false, true, 100.0D, 5L));
        assertEquals(144.0D, fixture.controller.modifyDamage(fixture.session, fixture.pokemon, true, true, 100.0D, 5L));
        assertEquals(100.0D, fixture.controller.modifyDamage(fixture.session, fixture.pokemon, false, false, 100.0D, 5L));
    }

    private static final class Fixture {
        private final ActionBattleTypeEffectController controller = new ActionBattleTypeEffectController();
        private final UUID session = UUID.randomUUID();
        private final UUID pokemon = UUID.randomUUID();

        private Fixture() { controller.guardSession(session); }
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
