package net.epiac9.cobblemonnml.battle.action.critical;

public final class ActionBattleCriticalRules {
    public static final float DAMAGE_MULTIPLIER = 1.5F;

    private ActionBattleCriticalRules() {}

    public static int clampStage(int stage) {
        return Math.max(0, Math.min(3, stage));
    }

    public static int combineStages(int baseStage, int bonusStage) {
        return clampStage(Math.max(0, baseStage) + Math.max(0, bonusStage));
    }

    public static double chance(int stage) {
        return switch (clampStage(stage)) {
            case 1 -> 1.0D / 8.0D;
            case 2 -> 1.0D / 2.0D;
            case 3 -> 1.0D;
            default -> 1.0D / 24.0D;
        };
    }

    public static boolean rollsCritical(int stage, double roll) {
        return Double.isFinite(roll) && roll >= 0.0D && roll < chance(stage);
    }

    public static float apply(float resolvedDamage, ActionBattleCriticalResult result) {
        if (!(resolvedDamage > 0.0F) || result == null || !result.critical()) return resolvedDamage;
        return resolvedDamage * DAMAGE_MULTIPLIER;
    }
}
