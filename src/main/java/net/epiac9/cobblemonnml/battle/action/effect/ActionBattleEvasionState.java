package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

final class ActionBattleEvasionState {
    static final long DURATION_TICKS = 120L;
    private long expiresAtTick = -1L;

    ApplyResult apply(long currentTick) {
        if (currentTick < 0L) return null;
        boolean active = isActive(currentTick);
        expiresAtTick = ActionBattleTiming.safeAdd(currentTick, DURATION_TICKS);
        return active ? ApplyResult.REFRESHED : ApplyResult.APPLIED;
    }

    boolean isActive(long currentTick) { return currentTick >= 0L && expiresAtTick > currentTick; }
    long remainingTicks(long currentTick) { return isActive(currentTick) ? Math.max(0L, expiresAtTick - currentTick) : 0L; }
    boolean isEmpty(long currentTick) { return !isActive(currentTick); }
    void clear() { expiresAtTick = -1L; }

    enum ApplyResult { APPLIED, REFRESHED }
}
