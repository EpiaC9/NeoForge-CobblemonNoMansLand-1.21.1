package net.epiac9.cobblemonnml.battle.action.effect.control;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlRules;

public final class ActionBattleRampageState {
    public static final long ACTIVE_DURATION_TICKS = 180L;
    public static final long EXHAUSTED_DURATION_TICKS = 180L;
    public static final double EXHAUSTED_MOVEMENT_MULTIPLIER = 0.80D;
    private String lockedMoveId;
    private int lockedMoveSlot = -1;
    private long activeUntil;
    private long exhaustedUntil;

    public boolean activate(String moveId, int moveSlot, long currentTick) {
        String normalized = ActionBattleControlRules.normalize(moveId);
        if (normalized == null || moveSlot < 0 || currentTick < 0L || active(currentTick)) return false;
        lockedMoveId = normalized;
        lockedMoveSlot = moveSlot;
        activeUntil = ActionBattleTiming.safeAdd(currentTick, ACTIVE_DURATION_TICKS);
        exhaustedUntil = 0L;
        return true;
    }

    public boolean canUse(String moveId, long currentTick) {
        advance(currentTick);
        return !active(currentTick) || lockedMoveId.equals(ActionBattleControlRules.normalize(moveId));
    }

    public View view(long currentTick) {
        advance(currentTick);
        return new View(active(currentTick), lockedMoveId, lockedMoveSlot,
                Math.max(0L, activeUntil - currentTick), exhausted(currentTick),
                Math.max(0L, exhaustedUntil - currentTick));
    }

    public double movementMultiplier(long currentTick) {
        advance(currentTick);
        return exhausted(currentTick) ? EXHAUSTED_MOVEMENT_MULTIPLIER : 1.0D;
    }

    public boolean empty(long currentTick) {
        advance(currentTick);
        return !active(currentTick) && !exhausted(currentTick);
    }

    private void advance(long currentTick) {
        if (activeUntil > 0L && currentTick >= activeUntil) {
            long end = activeUntil;
            activeUntil = 0L;
            lockedMoveId = null;
            lockedMoveSlot = -1;
            exhaustedUntil = ActionBattleTiming.safeAdd(end, EXHAUSTED_DURATION_TICKS);
        }
        if (exhaustedUntil > 0L && currentTick >= exhaustedUntil) exhaustedUntil = 0L;
    }

    private boolean active(long tick) { return activeUntil > tick; }
    private boolean exhausted(long tick) { return exhaustedUntil > tick; }

    public record View(boolean active, String lockedMoveId, int lockedMoveSlot,
                       long activeRemainingTicks, boolean exhausted, long exhaustedRemainingTicks) {}
}
