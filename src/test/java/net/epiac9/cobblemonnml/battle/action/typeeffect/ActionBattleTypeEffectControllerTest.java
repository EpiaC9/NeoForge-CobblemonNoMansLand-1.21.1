package net.epiac9.cobblemonnml.battle.action.typeeffect;

import java.util.UUID;

public final class ActionBattleTypeEffectControllerTest {
    private ActionBattleTypeEffectControllerTest() {}

    public static void main(String[] args) {
        tracksPokemonIndependentlyWithinOneSession();
        rejectsOperationsForTheWrongSession();
        replacingTheSessionDiscardsStaleState();
        clearsOnePokemonWithoutClearingOthers();
        ticksRecalledPokemonWithoutAnEntity();
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
