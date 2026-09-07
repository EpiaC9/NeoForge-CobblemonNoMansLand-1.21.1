package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleRockRules {
    public static final long HISTORY_RESET_TICKS = ActionBattleTiming.seconds(18L);
    public static final long ENDURANCE_TICKS = ActionBattleTiming.seconds(18L);
    private static final long[] STOCKPILE_DURATIONS = {
            ActionBattleTiming.seconds(9L), ActionBattleTiming.seconds(7L),
            ActionBattleTiming.seconds(5L), ActionBattleTiming.seconds(3L),
            ActionBattleTiming.seconds(1L)
    };

    private ActionBattleRockRules() {}

    public static long stockpileDuration(int completedCycles) {
        return STOCKPILE_DURATIONS[Math.max(0, Math.min(STOCKPILE_DURATIONS.length - 1, completedCycles))];
    }

    public static int reflectionDamage(int incomingDamage) {
        return incomingDamage <= 0 ? 0 : Math.max(1, (int) Math.round(incomingDamage * 0.5D));
    }
}
