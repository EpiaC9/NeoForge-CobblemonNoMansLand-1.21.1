package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public final class ActionBattleBugDashRules {
    private ActionBattleBugDashRules() {}

    public static DashPlan plan(double positionX, double positionZ, double directiveX,
                                double directiveZ, double facingX, double facingZ,
                                double distance) {
        double x = Double.isFinite(directiveX) && Double.isFinite(directiveZ)
                ? directiveX - positionX : facingX;
        double z = Double.isFinite(directiveX) && Double.isFinite(directiveZ)
                ? directiveZ - positionZ : facingZ;
        double length = Math.sqrt(x * x + z * z);
        if (!(length > 0.000001D) || !(distance > 0.0D)) return new DashPlan(0.0D, 0.0D);
        return new DashPlan(x / length * distance, z / length * distance);
    }

    public record DashPlan(double deltaX, double deltaZ) {}
}
