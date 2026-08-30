package net.epiac9.cobblemonnml.battle.action;

public final class ActionBattleTrainerTactics {
    private static final int MAX_REPOSITION_ATTEMPTS = 3;
    private static final double RANGED_DISTANCE = 8.5D;
    private static final double MELEE_DISTANCE = 2.5D;
    private static final double[] ANGLE_OFFSETS = {Math.toRadians(55.0D), Math.toRadians(-55.0D), Math.PI};

    private ActionBattleTrainerTactics() {}

    public static int maxRepositionAttempts() { return MAX_REPOSITION_ATTEMPTS; }

    public static int engagementScore(boolean hasRangedMove, boolean hasMeleeMove) {
        if (hasRangedMove) return 2;
        if (hasMeleeMove) return 1;
        return 0;
    }

    public static boolean isMeaningfullyBetter(int currentScore, int candidateScore) {
        return candidateScore > currentScore;
    }

    public static Point[] repositionCandidates(double attackerX, double attackerZ, double targetX, double targetZ, boolean ranged) {
        double baseAngle = Math.atan2(attackerZ - targetZ, attackerX - targetX);
        double distance = ranged ? RANGED_DISTANCE : MELEE_DISTANCE;
        Point[] points = new Point[MAX_REPOSITION_ATTEMPTS];
        for (int i = 0; i < points.length; i++) {
            double angle = baseAngle + ANGLE_OFFSETS[i];
            points[i] = new Point(targetX + Math.cos(angle) * distance, targetZ + Math.sin(angle) * distance);
        }
        return points;
    }

    public record Point(double x, double z) {}
}
