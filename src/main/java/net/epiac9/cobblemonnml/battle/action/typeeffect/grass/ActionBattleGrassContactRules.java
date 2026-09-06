package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleGrassContactRules {
    public enum Outcome { ALLY_EMPOWER_110, ALLY_EMPOWER_120, ENEMY_MOVEMENT, ENEMY_LEECH_SEED, ENEMY_LEECH_REACTIVATION }

    private ActionBattleGrassContactRules() {}

    public static Outcome resolve(boolean allied, boolean grassTyped, boolean alreadyLeechSeeded) {
        if (allied) return grassTyped ? Outcome.ALLY_EMPOWER_120 : Outcome.ALLY_EMPOWER_110;
        if (grassTyped) return Outcome.ENEMY_MOVEMENT;
        return alreadyLeechSeeded ? Outcome.ENEMY_LEECH_REACTIVATION : Outcome.ENEMY_LEECH_SEED;
    }
}
