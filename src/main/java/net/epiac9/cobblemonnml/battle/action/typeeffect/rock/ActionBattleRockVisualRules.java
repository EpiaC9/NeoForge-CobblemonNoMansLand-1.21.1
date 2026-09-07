package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

public final class ActionBattleRockVisualRules {
    public static final String STOCKPILE_STATUS_ID = "TYPE_ROCK_STOCKPILE";
    public static final String ENDURANCE_STATUS_ID = "TYPE_ROCK_ENDURANCE";
    private static final long AURA_INTERVAL_TICKS = 10L;
    private static final int ENDURANCE_CONSUME_COUNT = 18;
    private static final int ROCK_ENDURANCE_CONSUME_COUNT = 26;

    private ActionBattleRockVisualRules() {}

    public static boolean shouldEmitProcBurst(ActionBattleRockController.ProcResult result) {
        return result != null && result.occurred();
    }

    public static boolean auraDue(long currentTick) {
        return currentTick >= 0L && currentTick % AURA_INTERVAL_TICKS == 0L;
    }

    public static int consumeBurstCount(boolean rockReflection) {
        return rockReflection ? ROCK_ENDURANCE_CONSUME_COUNT : ENDURANCE_CONSUME_COUNT;
    }
}
