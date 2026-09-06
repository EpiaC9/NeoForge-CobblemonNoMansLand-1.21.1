package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

public final class ActionBattlePsycUpRules {
    public static final long HISTORY_RESET_TICKS = 360L;
    private static final long[] DURATIONS = {180L, 140L, 100L, 60L, 20L};

    private ActionBattlePsycUpRules() {}

    public static long durationTicks(int completedCycles) {
        return DURATIONS[Math.max(0, Math.min(completedCycles, DURATIONS.length - 1))];
    }

    public static int nextCompletedCycles(int completedCycles) {
        return Math.min(DURATIONS.length - 1, Math.max(0, completedCycles) + 1);
    }
}
