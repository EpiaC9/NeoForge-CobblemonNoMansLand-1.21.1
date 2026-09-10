package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleGrassContactRules {
    public enum Outcome { GRASS_PICKUP, TRAMPLE }

    private ActionBattleGrassContactRules() {}

    public static Outcome resolve(boolean allied, boolean grassTyped, boolean alreadyLeechSeeded) {
        return grassTyped ? Outcome.GRASS_PICKUP : Outcome.TRAMPLE;
    }

    public static boolean shouldTrampleSeed(boolean grassTyped) {
        return !grassTyped;
    }
}
