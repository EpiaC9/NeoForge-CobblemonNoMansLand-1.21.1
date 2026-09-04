package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

public final class ActionBattlePoisonRules {
    public static final int MAX_ACCUMULATION = 99;
    public static final int POISON_LV1_THRESHOLD = 33;
    public static final int POISON_LV2_THRESHOLD = 66;
    public static final int TOXIC_THRESHOLD = 99;
    public static final int LEVEL_DURATION_TICKS = 120;
    public static final int TOXIC_DURATION_TICKS = 180;
    public static final int PASSIVE_INTERVAL_TICKS = 20;
    public static final int CLEAN_RESET_TICKS = 360;
    public static final int BASE_MOVE_GAIN = 9;
    public static final double TOXIC_POISON_DAMAGE_MULTIPLIER = 1.20D;

    private ActionBattlePoisonRules() {}

    public enum PoisonLevel { NONE, POISON, POISON_LV1, POISON_LV2, TOXIC }

    public static PoisonLevel levelForAccumulation(int accumulation) {
        if (accumulation <= 0) return PoisonLevel.NONE;
        if (accumulation < POISON_LV1_THRESHOLD) return PoisonLevel.POISON;
        if (accumulation < POISON_LV2_THRESHOLD) return PoisonLevel.POISON_LV1;
        if (accumulation < TOXIC_THRESHOLD) return PoisonLevel.POISON_LV2;
        return PoisonLevel.TOXIC;
    }

    public static int passiveGain(PoisonLevel level) {
        return switch (level) {
            case POISON -> 3;
            case POISON_LV1 -> 6;
            case POISON_LV2 -> 9;
            case NONE, TOXIC -> 0;
        };
    }

    public static int decay(PoisonLevel level) {
        return switch (level) {
            case POISON -> 5;
            case POISON_LV1 -> 8;
            case POISON_LV2 -> 12;
            case NONE, TOXIC -> 0;
        };
    }

    public static int specialAttackStages(PoisonLevel level, boolean poisonTyped) {
        int magnitude = switch (level) {
            case POISON, POISON_LV1, POISON_LV2 -> 1;
            case TOXIC -> 2;
            case NONE -> 0;
        };
        return poisonTyped ? magnitude : -magnitude;
    }

    public static double modifyIncomingDamage(double damage, boolean poisonMove, boolean toxic) {
        return poisonMove && toxic ? damage * TOXIC_POISON_DAMAGE_MULTIPLIER : damage;
    }

    public static boolean isQualifyingDamagingHit(boolean hitSucceeded, int beforeHp, int afterHp) {
        return hitSucceeded && beforeHp > 0 && afterHp < beforeHp;
    }
}
