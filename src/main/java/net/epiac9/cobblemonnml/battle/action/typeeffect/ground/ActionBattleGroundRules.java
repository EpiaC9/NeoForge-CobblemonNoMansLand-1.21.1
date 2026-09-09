package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;

import java.util.Locale;

public final class ActionBattleGroundRules {
    public static final long NON_GROUND_STAGE_TICKS = 120L;
    public static final long GROUND_STAGE_TICKS = 180L;
    public static final long OVERALL_TICKS = 360L;
    public static final double RADIAL_DAMAGE_FRACTION = 0.05D;

    private ActionBattleGroundRules() {}

    public static boolean qualifies(String moveType, ActionMoveDeliveryType deliveryType) {
        return "ground".equals(normalize(moveType))
                && deliveryType == ActionMoveDeliveryType.GROUND_HUGGING_WAVE;
    }

    public static boolean isFlyingTyped(String primaryType, String secondaryType) {
        return "flying".equals(normalize(primaryType)) || "flying".equals(normalize(secondaryType));
    }

    public static int firstDepth(boolean groundTyped) { return groundTyped ? 45 : 30; }
    public static int depthIncrement(boolean groundTyped) { return groundTyped ? 45 : 30; }
    public static long stageDurationTicks(boolean groundTyped) {
        return groundTyped ? GROUND_STAGE_TICKS : NON_GROUND_STAGE_TICKS;
    }

    public static double movementMultiplier(int depthPercent, boolean groundTyped) {
        if (groundTyped || depthPercent <= 0) return 1.0D;
        return switch (depthPercent) {
            case 30 -> 0.70D;
            case 60 -> 0.40D;
            case 90 -> 0.00D;
            default -> throw new IllegalArgumentException("Unsupported non-Ground Sink depth: " + depthPercent);
        };
    }

    public static double collisionScale(int depthPercent) {
        if (depthPercent <= 0) return 1.0D;
        return switch (depthPercent) {
            case 30 -> 0.70D;
            case 45 -> 0.55D;
            case 60 -> 0.40D;
            case 90 -> 0.10D;
            default -> throw new IllegalArgumentException("Unsupported buried depth: " + depthPercent);
        };
    }

    public static double expelDamageMultiplier(boolean attackerGroundTyped) {
        return 1.0D;
    }

    public static int radialDamage(int maximumHealth) {
        return maximumHealth <= 0 ? 0 : Math.max(1, (int) Math.ceil(maximumHealth * RADIAL_DAMAGE_FRACTION));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
