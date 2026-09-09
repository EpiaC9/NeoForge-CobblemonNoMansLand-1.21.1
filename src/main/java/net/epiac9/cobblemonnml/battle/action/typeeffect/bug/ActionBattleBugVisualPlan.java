package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public final class ActionBattleBugVisualPlan {
    private ActionBattleBugVisualPlan() {}

    public static Followup followup(boolean attack, boolean specialAttack, boolean ranged) {
        if (attack && specialAttack) return ranged ? Followup.RANGED_EXPLOSION : Followup.MELEE_EXPLOSION;
        if (attack) return ranged ? Followup.RANGED : Followup.MELEE;
        return specialAttack ? Followup.EXPLOSION : Followup.NONE;
    }

    public static int threadPoints() { return 9; }
    public static int dashSamples(double distance) { return Math.max(1, (int) Math.ceil(Math.max(0.0D, distance) * 3.0D) + 1); }
    public static double explosionRadius() { return 1.0D; }
    public static boolean showsStatue(boolean physical, boolean effectZone) { return physical || effectZone; }

    public enum Followup { NONE, MELEE, RANGED, EXPLOSION, MELEE_EXPLOSION, RANGED_EXPLOSION }
}
