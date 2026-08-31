package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

final class ActionBattleParalysisState {
    static final long BASE_DURATION_TICKS = ActionBattleTiming.seconds(6L);
    static final float CHECK_STEP_CHANCE = 0.015F;
    static final float MAX_CHECK_CHANCE = 0.20F;

    private long endTick;
    private int checkSteps;

    ApplyResult apply(long currentTick) { return apply(currentTick, 1.0F); }

    ApplyResult apply(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        boolean active = isActive(currentTick);
        if (active) advanceChecks(1);
        else checkSteps = 0;
        long duration = Math.max(1L, Math.round(BASE_DURATION_TICKS * durationMultiplier));
        endTick = ActionBattleTiming.safeAdd(currentTick, duration);
        return active ? ApplyResult.REFRESHED : ApplyResult.APPLIED;
    }

    boolean isActive(long currentTick) { return currentTick >= 0L && endTick > currentTick; }
    long remainingTicks(long currentTick) { return isActive(currentTick) ? endTick - currentTick : 0L; }

    void advanceChecks(int count) {
        if (count <= 0) return;
        int maxSteps = (int) Math.ceil(MAX_CHECK_CHANCE / CHECK_STEP_CHANCE);
        checkSteps = Math.min(maxSteps, checkSteps + count);
    }

    float checkChance() { return Math.min(MAX_CHECK_CHANCE, checkSteps * CHECK_STEP_CHANCE); }
    void resetBuildup() { checkSteps = 0; }
    void clear() { endTick = 0L; checkSteps = 0; }
    boolean isEmpty(long currentTick) { return !isActive(currentTick); }

    enum ApplyResult { APPLIED, REFRESHED }
}
