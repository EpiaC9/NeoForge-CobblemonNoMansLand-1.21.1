package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

public final class ActionBattleAerialMoveRules {
    private ActionBattleAerialMoveRules() {}

    public static Mode classify(boolean flyingPokemon, boolean destinationSupported) {
        if (destinationSupported) return Mode.GROUND;
        return flyingPokemon ? Mode.AERIAL : Mode.REJECTED;
    }

    public static boolean shouldApplyHoverMotion(boolean propulsionActive) {
        return !propulsionActive;
    }

    public enum Mode { GROUND, AERIAL, REJECTED }
}
