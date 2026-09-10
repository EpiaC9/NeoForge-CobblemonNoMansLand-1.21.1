package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;

import java.util.Locale;

public final class ActionBattleGroundRules {
    public static final long NON_GROUND_STAGE_TICKS = 120L;
    public static final long GROUND_STAGE_TICKS = 180L;
    public static final long OVERALL_TICKS = 360L;
    public static final double RADIAL_DAMAGE_FRACTION = 0.05D;
    public static final int SINK_PER_SECOND = 3;
    public static final int SWAP_LOCK_PERCENT = 60;
    public static final int KO_PERCENT = 100;
    public static final int SHOCKWAVE_HALF_WIDTH = 2;

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
        return 1.0D - Math.clamp(depthPercent, 0, 100) / 100.0D;
    }

    public static double collisionScale(int depthPercent) {
        return 1.0D - Math.clamp(depthPercent, 0, 99) / 100.0D;
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
