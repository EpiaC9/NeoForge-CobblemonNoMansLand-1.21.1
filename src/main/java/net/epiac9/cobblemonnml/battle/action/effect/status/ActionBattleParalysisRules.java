package net.epiac9.cobblemonnml.battle.action.effect.status;

public final class ActionBattleParalysisRules {
    public static final long DURATION_TICKS = 180L;
    public static final double MOVEMENT_MULTIPLIER = 0.75D;
    public static final long EXECUTION_DELAY_TICKS = 10L;
    public static final double ACTION_FAILURE_CHANCE = 0.25D;

    private ActionBattleParalysisRules() {}

    public static double movementMultiplier(boolean active) { return active ? MOVEMENT_MULTIPLIER : 1.0D; }
    public static long executionDelayTicks(boolean active) { return active ? EXECUTION_DELAY_TICKS : 0L; }

    public static boolean failsAction(boolean active, double roll) {
        return active && Double.isFinite(roll) && roll >= 0.0D && roll < ACTION_FAILURE_CHANCE;
    }

    public static CommitPlan commitPlan(boolean active, double roll) {
        return new CommitPlan(true, true, !failsAction(active, roll));
    }

    public record CommitPlan(boolean consumePp, boolean commitGlobalCooldown, boolean resolveEffect) {}
}
