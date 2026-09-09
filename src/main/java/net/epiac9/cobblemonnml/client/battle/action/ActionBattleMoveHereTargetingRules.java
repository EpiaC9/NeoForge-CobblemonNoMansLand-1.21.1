package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleMoveHereTargetingRules {
    public static final double AIR_TARGET_DISTANCE = 5.0D;

    private ActionBattleMoveHereTargetingRules() {}

    public static Point target(Point eye, Point look, boolean blockHit, Point hitLocation) {
        if (blockHit) return finite(hitLocation) ? hitLocation : null;
        if (!finite(eye) || !finite(look)) return null;
        double length = Math.sqrt(look.x() * look.x() + look.y() * look.y() + look.z() * look.z());
        if (!Double.isFinite(length) || length <= 1.0E-9D) return null;
        double scale = AIR_TARGET_DISTANCE / length;
        return new Point(eye.x() + look.x() * scale,
                eye.y() + look.y() * scale,
                eye.z() + look.z() * scale);
    }

    private static boolean finite(Point point) {
        return point != null && Double.isFinite(point.x())
                && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }

    public record Point(double x, double y, double z) {}
}
