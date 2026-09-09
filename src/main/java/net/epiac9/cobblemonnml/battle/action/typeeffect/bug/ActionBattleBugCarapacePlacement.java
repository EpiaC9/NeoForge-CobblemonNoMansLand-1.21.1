package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public final class ActionBattleBugCarapacePlacement {
    private static final double DISTANCE = 2.0D;

    private ActionBattleBugCarapacePlacement() {}

    public static Plan plan(boolean physical, boolean effectZone, double ownerX, double ownerZ,
                            double facingX, double facingZ) {
        Side side = physical && !effectZone ? Side.FRONT : Side.BEHIND;
        double length = Math.sqrt(facingX * facingX + facingZ * facingZ);
        double unitX = length > 0.000001D ? facingX / length : 0.0D;
        double unitZ = length > 0.000001D ? facingZ / length : 1.0D;
        double direction = side == Side.FRONT ? 1.0D : -1.0D;
        return new Plan((int) Math.round(ownerX + unitX * DISTANCE * direction),
                (int) Math.round(ownerZ + unitZ * DISTANCE * direction), side);
    }

    public static boolean validLanding(boolean empty, boolean supported) { return empty && supported; }

    public enum Side { FRONT, BEHIND }
    public record Plan(int blockX, int blockZ, Side side) {}
}
