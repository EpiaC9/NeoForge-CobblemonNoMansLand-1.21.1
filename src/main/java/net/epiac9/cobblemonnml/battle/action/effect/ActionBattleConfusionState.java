package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleConfusionState {
    private long expiresAtTick;

    public ApplyResult apply(long currentTick) {
        if (currentTick < 0L) return null;
        boolean active = isActive(currentTick);
        expiresAtTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleConfusionRules.DURATION_TICKS);
        return active ? ApplyResult.REFRESHED : ApplyResult.APPLIED;
    }

    public boolean isActive(long currentTick) { return currentTick >= 0L && currentTick < expiresAtTick; }
    public long remainingTicks(long currentTick) { return isActive(currentTick) ? Math.max(0L, expiresAtTick - currentTick) : 0L; }
    public void clear() { expiresAtTick = 0L; }
    public boolean isEmpty(long currentTick) { return !isActive(currentTick); }

    public enum ApplyResult { APPLIED, REFRESHED }
}
