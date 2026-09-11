package net.epiac9.cobblemonnml.battle.action.move;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;

public final class ActionBattleMoveTimingRules {
    public static final long BASE_STARTUP_TICKS = 10L;
    public static final long PRIORITY_STEP_TICKS = 2L;

    private ActionBattleMoveTimingRules() {}

    public static long startupTicks(int priority, int speedStage) {
        long priorityAdjusted = Math.max(0L, BASE_STARTUP_TICKS - (long) priority * PRIORITY_STEP_TICKS);
        if (priorityAdjusted == 0L) return 0L;
        double speedMultiplier = ActionBattleStatRules.standardMultiplier(speedStage);
        if (!(speedMultiplier > 0.0D)) return priorityAdjusted;
        return Math.max(1L, Math.round(priorityAdjusted / speedMultiplier));
    }

    public static long remainingStartupTicks(long readySinceTick, long currentTick, int priority, int speedStage) {
        long duration = startupTicks(priority, speedStage);
        if (duration <= 0L) return 0L;
        long elapsed = readySinceTick >= 0L && currentTick >= readySinceTick ? currentTick - readySinceTick : 0L;
        return Math.max(0L, duration - elapsed);
    }

    public static boolean ready(long readySinceTick, long currentTick, int priority, int speedStage) {
        return remainingStartupTicks(readySinceTick, currentTick, priority, speedStage) == 0L;
    }
}
