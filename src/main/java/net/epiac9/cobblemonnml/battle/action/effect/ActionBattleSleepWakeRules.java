package net.epiac9.cobblemonnml.battle.action.effect;

public final class ActionBattleSleepWakeRules {
    public static final float RANGED_WAKE_CHANCE = 0.10F;
    public static final float MELEE_WAKE_CHANCE = 0.20F;
    public static final float ORDINARY_WAKE_DAMAGE_MULTIPLIER = 1.25F;
    public static final float EXPLICIT_WAKE_DAMAGE_MULTIPLIER = 1.50F;

    private ActionBattleSleepWakeRules() {}

    public static float wakeChance(boolean ranged) { return ranged ? RANGED_WAKE_CHANCE : MELEE_WAKE_CHANCE; }
    public static boolean rollWake(boolean ranged, float roll) { return roll >= 0.0F && roll < wakeChance(ranged); }
    public static float damageMultiplier(boolean wakesTarget, boolean explicitWake) {
        if (!wakesTarget) return 1.0F;
        return explicitWake ? EXPLICIT_WAKE_DAMAGE_MULTIPLIER : ORDINARY_WAKE_DAMAGE_MULTIPLIER;
    }
}
