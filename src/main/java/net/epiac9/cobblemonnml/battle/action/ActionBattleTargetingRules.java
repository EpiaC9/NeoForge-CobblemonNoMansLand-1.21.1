package net.epiac9.cobblemonnml.battle.action;

import java.util.ArrayList;
import java.util.List;

public final class ActionBattleTargetingRules {
    public static final int VISIBILITY_SAMPLE_COUNT = 32;
    public static final int REQUIRED_CLEAR_RAYS = 16;
    private static final double FORWARD_CONE_COSINE = Math.cos(Math.toRadians(140.0D));
    private static final double[] X_SAMPLES = {0.15D, 0.3833333333D, 0.6166666667D, 0.85D};
    private static final double[] Y_SAMPLES = {0.10D, 0.3666666667D, 0.6333333333D, 0.90D};
    private static final double[] Z_SAMPLES = {0.20D, 0.80D};

    private ActionBattleTargetingRules() {}

    public static boolean canPerceive(double hitboxGapSquared, double awareness) {
        return ActionBattleRangeRules.withinHitboxRangeSquared(hitboxGapSquared, awareness);
    }

    public static boolean maySelectMove(boolean enemyVisible, boolean enemyTargeted) {
        return !enemyTargeted || enemyVisible;
    }

    public static boolean shouldPursue(boolean enemyVisible, boolean enemyTargeted) {
        return enemyVisible && enemyTargeted;
    }

    public static boolean shouldStopNavigationOnTargetLoss(boolean explicitMovementIntent) {
        return !explicitMovementIntent;
    }

    public static boolean mayCommit(boolean enemyTargeted, boolean aware, boolean lineOfSight, boolean inRange) {
        return inRange && (!enemyTargeted || aware && lineOfSight);
    }

    public static boolean hasVisibleMajority(int clearRays, int totalRays) {
        return totalRays > 0 && clearRays * 2 >= totalRays;
    }

    public static boolean insideForwardCone(Point origin, Point facing, Point target) {
        if (origin == null || facing == null || target == null) return false;
        double facingLength = Math.sqrt(facing.x() * facing.x() + facing.z() * facing.z());
        double dx = target.x() - origin.x();
        double dz = target.z() - origin.z();
        double targetLength = Math.sqrt(dx * dx + dz * dz);
        if (facingLength <= 1.0E-9D || targetLength <= 1.0E-9D) return false;
        double cosine = (facing.x() * dx + facing.z() * dz) / (facingLength * targetLength);
        return cosine + 1.0E-9D >= FORWARD_CONE_COSINE;
    }

    public static List<Point> samplePoints(Hitbox hitbox) {
        if (hitbox == null) return List.of();
        List<Point> samples = new ArrayList<>(VISIBILITY_SAMPLE_COUNT);
        for (double xFraction : X_SAMPLES) {
            for (double yFraction : Y_SAMPLES) {
                for (double zFraction : Z_SAMPLES) {
                    samples.add(new Point(
                            lerp(hitbox.minX(), hitbox.maxX(), xFraction),
                            lerp(hitbox.minY(), hitbox.maxY(), yFraction),
                            lerp(hitbox.minZ(), hitbox.maxZ(), zFraction)
                    ));
                }
            }
        }
        return List.copyOf(samples);
    }

    public static VisibilityResult evaluateVisibility(Point origin, Point facing, Hitbox target,
                                                       boolean insideArena, RayProbe probe) {
        if (origin == null || facing == null || target == null || probe == null) {
            return new VisibilityResult(0, VISIBILITY_SAMPLE_COUNT, insideArena, false, false);
        }
        int coneRays = 0;
        int clearRays = 0;
        for (Point sample : samplePoints(target)) {
            if (!insideForwardCone(origin, facing, sample)) continue;
            coneRays++;
            if (probe.clear(origin, sample)) clearRays++;
        }
        boolean insideCone = coneRays >= REQUIRED_CLEAR_RAYS;
        boolean visible = insideArena && insideCone && clearRays >= REQUIRED_CLEAR_RAYS;
        return new VisibilityResult(clearRays, VISIBILITY_SAMPLE_COUNT, insideArena, insideCone, visible);
    }

    private static double lerp(double min, double max, double fraction) {
        return min + (max - min) * fraction;
    }

    public record Point(double x, double y, double z) {}

    public record Hitbox(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {
        public Hitbox {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("Hitbox maximums must not be below minimums");
            }
        }

        public boolean contains(Point point) {
            return point != null
                    && point.x() >= minX && point.x() <= maxX
                    && point.y() >= minY && point.y() <= maxY
                    && point.z() >= minZ && point.z() <= maxZ;
        }
    }

    public record VisibilityResult(int clearRays, int totalRays, boolean insideArena,
                                   boolean insideCone, boolean visible) {
        public static VisibilityResult blocked() {
            return new VisibilityResult(0, VISIBILITY_SAMPLE_COUNT, false, false, false);
        }
    }

    @FunctionalInterface
    public interface RayProbe {
        boolean clear(Point from, Point to);
    }
}
