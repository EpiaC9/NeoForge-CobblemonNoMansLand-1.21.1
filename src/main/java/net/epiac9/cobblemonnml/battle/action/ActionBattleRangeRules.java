package net.epiac9.cobblemonnml.battle.action;

import net.minecraft.world.phys.AABB;

public final class ActionBattleRangeRules {
    public static final double DEFAULT_AWARENESS_RANGE = 20.0D;
    public static final double DEFAULT_RANGED_EXECUTION_RANGE = 16.0D;
    public static final double DEFAULT_MELEE_EXECUTION_RANGE = 1.5D;

    private ActionBattleRangeRules() {}

    public static double hitboxGapSquared(AABB first, AABB second) {
        if (first == null || second == null) return Double.POSITIVE_INFINITY;
        return hitboxGapSquared(first.minX, first.minY, first.minZ, first.maxX, first.maxY, first.maxZ,
                second.minX, second.minY, second.minZ, second.maxX, second.maxY, second.maxZ);
    }

    public static double hitboxGapSquared(
            double firstMinX, double firstMinY, double firstMinZ,
            double firstMaxX, double firstMaxY, double firstMaxZ,
            double secondMinX, double secondMinY, double secondMinZ,
            double secondMaxX, double secondMaxY, double secondMaxZ) {
        double x = axisGap(firstMinX, firstMaxX, secondMinX, secondMaxX);
        double y = axisGap(firstMinY, firstMaxY, secondMinY, secondMaxY);
        double z = axisGap(firstMinZ, firstMaxZ, secondMinZ, secondMaxZ);
        return x * x + y * y + z * z;
    }

    public static boolean withinHitboxRange(AABB first, AABB second, double range) {
        return withinHitboxRangeSquared(hitboxGapSquared(first, second), range);
    }

    public static boolean withinHitboxRangeSquared(double gapSquared, double range) {
        return Double.isFinite(gapSquared) && Double.isFinite(range) && range >= 0.0D
                && gapSquared <= range * range;
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) return secondMin - firstMax;
        if (secondMax < firstMin) return firstMin - secondMax;
        return 0.0D;
    }
}
