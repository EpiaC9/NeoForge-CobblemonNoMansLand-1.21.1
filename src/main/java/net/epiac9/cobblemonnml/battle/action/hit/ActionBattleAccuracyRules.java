package net.epiac9.cobblemonnml.battle.action.hit;

/**
 * Translates canonical move accuracy into ACTION delivery reliability.
 * Ordinary ACTION projectiles do not perform a hidden percentage hit roll.
 * Instead, finite canonical accuracy scales projectile travel speed, while
 * the existing temporary Accuracy stat stage multiplier is applied separately.
 */
public final class ActionBattleAccuracyRules {
    public static final double ALWAYS_HIT_VALUE = -1.0D;

    private ActionBattleAccuracyRules() {}

    public static Mode mode(double canonicalAccuracy, boolean selfOrAllyTargeted, boolean specialAccuracyRule) {
        if (selfOrAllyTargeted) return Mode.SELF_OR_ALLY_BYPASS;
        if (specialAccuracyRule) return Mode.SPECIAL_RULE;
        if (Double.compare(canonicalAccuracy, ALWAYS_HIT_VALUE) == 0) return Mode.ALWAYS_HIT;
        return canonicalAccuracy > 0.0D && Double.isFinite(canonicalAccuracy)
                ? Mode.FINITE_SPEED : Mode.SPECIAL_RULE;
    }

    public static double projectileSpeedMultiplier(double canonicalAccuracy,
                                                   boolean selfOrAllyTargeted,
                                                   boolean specialAccuracyRule) {
        return switch (mode(canonicalAccuracy, selfOrAllyTargeted, specialAccuracyRule)) {
            case SELF_OR_ALLY_BYPASS, ALWAYS_HIT, SPECIAL_RULE -> 1.0D;
            case FINITE_SPEED -> Math.max(0.01D, Math.min(1.0D, canonicalAccuracy / 100.0D));
        };
    }

    public enum Mode {
        SELF_OR_ALLY_BYPASS,
        ALWAYS_HIT,
        FINITE_SPEED,
        SPECIAL_RULE
    }
}
