package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ActionBattlePropulsionRules {
    public static final int HALF_SAMPLES = 16;
    private static final double MIN_DISTANCE = 0.05D;
    private static final double CURVE_SCALE = 0.18D;
    private static final double MAX_CURVE_OFFSET = 2.0D;

    private ActionBattlePropulsionRules() {}

    public static double speedBlocksPerTick(int momentum) {
        return ActionBattleFlyingRules.propulsionBlocksPerSecond(true, true, momentum) / 20.0D;
    }

    public static CommitMode commitMode(boolean flyingMove, boolean meleeMove, int momentum,
                                        boolean normalCanCommit, boolean propulsionCanLaunch) {
        return commitMode(ActionBattleFlyingRules.usesPropulsion(flyingMove, meleeMove, momentum),
                normalCanCommit, propulsionCanLaunch);
    }

    public static CommitMode commitMode(boolean propulsionQualified,
                                        boolean normalCanCommit, boolean propulsionCanLaunch) {
        if (propulsionQualified) {
            return propulsionCanLaunch ? CommitMode.PROPULSION : CommitMode.REPOSITION;
        }
        return normalCanCommit ? CommitMode.NORMAL : CommitMode.REPOSITION;
    }

    public static Plan plan(Point start, Point target, Mode mode, Predicate<Point> safe) {
        if (start == null || target == null || mode == null || safe == null
                || !start.finite() || !target.finite()) return Plan.INVALID;
        Point delta = target.subtract(start);
        double distance = delta.length();
        if (distance < MIN_DISTANCE) return Plan.INVALID;
        if (mode == Mode.STRAIGHT) return straightPlan(start, target, safe);
        return bellPlan(start, target, delta.scale(1.0D / distance), distance, safe);
    }

    private static Plan straightPlan(Point start, Point target, Predicate<Point> safe) {
        List<Point> points = new ArrayList<>(HALF_SAMPLES + 1);
        for (int index = 0; index <= HALF_SAMPLES; index++) {
            Point point = lerp(start, target, index / (double) HALF_SAMPLES);
            if (!safe.test(point)) return Plan.INVALID;
            points.add(point);
        }
        return new Plan(Mode.STRAIGHT, List.copyOf(points), HALF_SAMPLES,
                target, target, false);
    }

    private static Plan bellPlan(Point start, Point target, Point axis,
                                 double approachDistance, Predicate<Point> safe) {
        Point endpoint = target.add(target.subtract(start));
        Point normal = stableNormal(axis);
        double amplitude = Math.min(MAX_CURVE_OFFSET, approachDistance * CURVE_SCALE);
        List<Point> points = new ArrayList<>(HALF_SAMPLES * 2 + 1);
        boolean shortened = false;
        for (int index = 0; index <= HALF_SAMPLES * 2; index++) {
            double progress = index / (double) (HALF_SAMPLES * 2);
            Point line = lerp(start, endpoint, progress);
            double offset = -Math.sin(Math.PI * 2.0D * progress) * amplitude;
            Point point = index == HALF_SAMPLES ? target : line.add(normal.scale(offset));
            if (!safe.test(point)) {
                if (index <= HALF_SAMPLES) return Plan.INVALID;
                shortened = true;
                break;
            }
            points.add(point);
        }
        if (points.size() <= HALF_SAMPLES) return Plan.INVALID;
        return new Plan(Mode.BELL, List.copyOf(points), HALF_SAMPLES,
                target, endpoint, shortened);
    }

    private static Point stableNormal(Point axis) {
        Point up = new Point(0.0D, 1.0D, 0.0D);
        Point projectedUp = up.subtract(axis.scale(up.dot(axis)));
        if (projectedUp.length() >= MIN_DISTANCE) return projectedUp.normalized();
        Point east = new Point(1.0D, 0.0D, 0.0D);
        Point projectedEast = east.subtract(axis.scale(east.dot(axis)));
        if (projectedEast.length() >= MIN_DISTANCE) return projectedEast.normalized();
        return new Point(0.0D, 0.0D, 1.0D);
    }

    private static Point lerp(Point from, Point to, double progress) {
        return from.add(to.subtract(from).scale(progress));
    }

    public enum Mode {
        STRAIGHT,
        BELL
    }

    public enum CommitMode {
        NORMAL,
        PROPULSION,
        REPOSITION
    }

    public record Point(double x, double y, double z) {
        public Point add(Point other) {
            return new Point(x + other.x, y + other.y, z + other.z);
        }

        public Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        public Point scale(double multiplier) {
            return new Point(x * multiplier, y * multiplier, z * multiplier);
        }

        public double dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public double length() {
            return Math.sqrt(dot(this));
        }

        public Point normalized() {
            double length = length();
            return length >= MIN_DISTANCE ? scale(1.0D / length) : new Point(0.0D, 0.0D, 0.0D);
        }

        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
        }
    }

    public record Plan(Mode mode, List<Point> points, int targetIndex,
                       Point targetPoint, Point idealEndpoint, boolean shortened) {
        public static final Plan INVALID = new Plan(Mode.STRAIGHT, List.of(), -1,
                new Point(0.0D, 0.0D, 0.0D), new Point(0.0D, 0.0D, 0.0D), false);

        public boolean valid() {
            return targetIndex >= 0 && points.size() > targetIndex;
        }
    }
}
