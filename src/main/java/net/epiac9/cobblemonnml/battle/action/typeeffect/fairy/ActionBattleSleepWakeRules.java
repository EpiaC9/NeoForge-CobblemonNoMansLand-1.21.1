package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

public final class ActionBattleSleepWakeRules {
    public static final float NORMAL_RANGED_DAMAGE_MULTIPLIER = 1.05F;
    public static final float NORMAL_MELEE_DAMAGE_MULTIPLIER = 1.10F;
    public static final float FAIRY_RANGED_DAMAGE_MULTIPLIER = 1.15F;
    public static final float FAIRY_MELEE_DAMAGE_MULTIPLIER = 1.20F;

    private ActionBattleSleepWakeRules() {}

    public static int sleepDurationTicksFromRoll(int roll) {
        if (roll < 0 || roll > 6) throw new IllegalArgumentException("Sleep duration roll must be between 0 and 6.");
        return (3 + roll) * 20;
    }

    public static float damageMultiplier(boolean sleeping, boolean ranged, boolean fairyTypedAttacker) {
        if (!sleeping) return 1.0F;
        if (fairyTypedAttacker) return ranged ? FAIRY_RANGED_DAMAGE_MULTIPLIER : FAIRY_MELEE_DAMAGE_MULTIPLIER;
        return ranged ? NORMAL_RANGED_DAMAGE_MULTIPLIER : NORMAL_MELEE_DAMAGE_MULTIPLIER;
    }
}
