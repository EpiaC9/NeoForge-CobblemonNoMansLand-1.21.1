package net.epiac9.cobblemonnml.battle.action;

final class ActionBattleParalysisMovementTracker {
    private boolean initialized;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double distanceRemainder;

    int observe(double x, double y, double z, boolean movementActive) {
        if (!initialized) {
            initialized = true;
            lastX = x;
            lastY = y;
            lastZ = z;
            return 0;
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        lastX = x;
        lastY = y;
        lastZ = z;
        if (!movementActive) return 0;
        distanceRemainder += Math.sqrt(dx * dx + dy * dy + dz * dz);
        int blocks = (int) Math.floor(distanceRemainder);
        if (blocks > 0) distanceRemainder -= blocks;
        return blocks;
    }

    void resetChain() {
        initialized = false;
        distanceRemainder = 0.0D;
    }
}
