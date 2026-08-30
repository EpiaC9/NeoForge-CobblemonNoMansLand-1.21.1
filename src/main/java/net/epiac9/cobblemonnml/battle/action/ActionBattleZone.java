package net.epiac9.cobblemonnml.battle.action;

public record ActionBattleZone(double centerX, double centerZ, double radius) {
    public ActionBattleZone {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(radius) || radius <= 0.0D) {
            throw new IllegalArgumentException("Action battle zone values must be finite and radius must be positive.");
        }
    }

    public boolean contains(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= radius * radius;
    }
}
