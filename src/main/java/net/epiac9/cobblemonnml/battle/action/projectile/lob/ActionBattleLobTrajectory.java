package net.epiac9.cobblemonnml.battle.action.projectile.lob;

public final class ActionBattleLobTrajectory {
    public record Point(double x, double y, double z) {}

    private ActionBattleLobTrajectory() {}

    public static Point position(Point origin, Point destination, double arcHeight,
                                 int elapsedTicks, int durationTicks) {
        if (origin == null || destination == null || durationTicks <= 0 || arcHeight < 0.0D) {
            throw new IllegalArgumentException("Lob trajectory requires endpoints, positive duration, and nonnegative arc.");
        }
        double progress = Math.clamp(elapsedTicks / (double) durationTicks, 0.0D, 1.0D);
        double arc = 4.0D * arcHeight * progress * (1.0D - progress);
        return new Point(lerp(origin.x(), destination.x(), progress),
                lerp(origin.y(), destination.y(), progress) + arc,
                lerp(origin.z(), destination.z(), progress));
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
