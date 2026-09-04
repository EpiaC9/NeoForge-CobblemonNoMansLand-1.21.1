package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;

import java.util.UUID;

public final class ActionBattlePoisonRuntimeTest {
    private ActionBattlePoisonRuntimeTest() {}

    public static void main(String[] args) {
        damagingDeliveryRequiresFinalPositiveHpDamage();
        sessionTicksProgressPoisonWithoutAnEntity();
    }

    private static void damagingDeliveryRequiresFinalPositiveHpDamage() {
        assertTrue(ActionBattlePoisonRules.isQualifyingDamagingHit(true, 100, 80));
        assertFalse(ActionBattlePoisonRules.isQualifyingDamagingHit(false, 100, 80));
        assertFalse(ActionBattlePoisonRules.isQualifyingDamagingHit(true, 100, 100));
        assertFalse(ActionBattlePoisonRules.isQualifyingDamagingHit(true, 0, 0));
    }

    private static void sessionTicksProgressPoisonWithoutAnEntity() {
        ActionBattleTypeEffectController controller = new ActionBattleTypeEffectController();
        UUID session = UUID.randomUUID();
        UUID pokemon = UUID.randomUUID();
        controller.guardSession(session);
        controller.applyPoisonMove(session, pokemon, 0L, false, 9);
        controller.tickSession(session, 20L);
        assertEquals(4, controller.poisonView(session, pokemon, 20L).orElseThrow().accumulation());
        for (long tick = 40L; tick <= 120L; tick += 20L) controller.tickSession(session, tick);
        assertEquals(14, controller.poisonView(session, pokemon, 120L).orElseThrow().accumulation());

        boolean sawLevelOne = false;
        boolean sawLevelTwo = false;
        long toxicEndTick = -1L;
        for (long tick = 140L; tick <= 2000L; tick += 20L) {
            controller.tickSession(session, tick);
            var view = controller.poisonView(session, pokemon, tick);
            if (view.isEmpty()) break;
            sawLevelOne |= view.orElseThrow().level() == ActionBattlePoisonRules.PoisonLevel.POISON_LV1;
            sawLevelTwo |= view.orElseThrow().level() == ActionBattlePoisonRules.PoisonLevel.POISON_LV2;
            if (view.orElseThrow().level() == ActionBattlePoisonRules.PoisonLevel.TOXIC) {
                toxicEndTick = tick + view.orElseThrow().toxicRemainingTicks();
                break;
            }
        }
        assertTrue(sawLevelOne);
        assertTrue(sawLevelTwo);
        assertTrue(toxicEndTick > 0L);
        controller.tickSession(session, toxicEndTick);
        assertTrue(controller.poisonView(session, pokemon, toxicEndTick).isEmpty());
        assertEquals(8, controller.poisonMoveAccumulationGain(session, pokemon));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("Expected false"); }
}
