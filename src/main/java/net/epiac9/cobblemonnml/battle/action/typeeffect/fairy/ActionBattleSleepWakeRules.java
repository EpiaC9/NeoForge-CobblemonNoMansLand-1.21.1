package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

public final class ActionBattleSleepWakeRules {
    public static final float ORDINARY_WAKE_DAMAGE_MULTIPLIER = 1.20F;
    public static final float FAIRY_WAKE_DAMAGE_MULTIPLIER = 1.25F;
    public static final float EXPLICIT_WAKE_DAMAGE_MULTIPLIER = 1.25F;
    public static final float FAIRY_EXPLICIT_WAKE_DAMAGE_MULTIPLIER = 1.50F;

    private ActionBattleSleepWakeRules() {}

    public static int sleepDurationTicksFromRoll(int roll) {
        if (roll < 0 || roll > 6) throw new IllegalArgumentException("Sleep duration roll must be between 0 and 6.");
        return (3 + roll) * 20;
    }

    public static float damageMultiplier(boolean sleeping, boolean fairyMove, boolean explicitWakeMove) {
        if (!sleeping) return 1.0F;
        if (fairyMove && explicitWakeMove) return FAIRY_EXPLICIT_WAKE_DAMAGE_MULTIPLIER;
        if (fairyMove) return FAIRY_WAKE_DAMAGE_MULTIPLIER;
        if (explicitWakeMove) return EXPLICIT_WAKE_DAMAGE_MULTIPLIER;
        return ORDINARY_WAKE_DAMAGE_MULTIPLIER;
    }
}
