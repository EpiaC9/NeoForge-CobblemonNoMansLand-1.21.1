package net.epiac9.cobblemonnml.battle.action.effect;

public final class ActionBattleStatRules {
    public static final long DEFAULT_STAT_DURATION_TICKS = 300L;
    private static final int STANDARD_MAX_STAGE = 6;
    private static final int ACCURACY_MAX_STAGE = 2;
    private static final double STANDARD_STAGE_STEP = 0.05D;
    private static final double ACCURACY_PROJECTILE_STAGE_STEP = 0.10D;

    private ActionBattleStatRules() {}

    public static int maxStage(ActionBattleStat stat) {
        return stat == ActionBattleStat.ACCURACY ? ACCURACY_MAX_STAGE : STANDARD_MAX_STAGE;
    }

    public static int clampStage(ActionBattleStat stat, int stage) {
        int max = maxStage(stat);
        return Math.max(-max, Math.min(max, stage));
    }

    public static double standardMultiplier(int stage) {
        int clamped = Math.max(-STANDARD_MAX_STAGE, Math.min(STANDARD_MAX_STAGE, stage));
        return 1.0D + clamped * STANDARD_STAGE_STEP;
    }

    public static double accuracyProjectileMultiplier(int stage) {
        int clamped = Math.max(-ACCURACY_MAX_STAGE, Math.min(ACCURACY_MAX_STAGE, stage));
        return 1.0D + clamped * ACCURACY_PROJECTILE_STAGE_STEP;
    }

    public static double damageMultiplier(int offenseStage, int defenseStage) {
        return standardMultiplier(offenseStage) * standardMultiplier(-defenseStage);
    }
}
