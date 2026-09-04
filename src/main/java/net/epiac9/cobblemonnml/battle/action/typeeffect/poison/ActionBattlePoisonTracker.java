package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattlePoisonTracker {
    private ActionBattlePoisonState activeState;
    private int moveAccumulationGain = ActionBattlePoisonRules.BASE_MOVE_GAIN;
    private long cleanResetEndTick = -1L;
    private boolean poisonTyped;

    public boolean applyMove(long currentTick, boolean receiverPoisonTyped, int penetratedDirectGain) {
        if (currentTick < 0L || penetratedDirectGain <= 0) return false;
        if (activeState != null) return activeState.applyDirectGain(penetratedDirectGain, currentTick);
        poisonTyped = receiverPoisonTyped;
        activeState = new ActionBattlePoisonState(receiverPoisonTyped);
        cleanResetEndTick = -1L;
        return activeState.applyDirectGain(1, currentTick);
    }

    public ActionBattlePoisonState.TickResult tick(long currentTick) {
        if (currentTick < 0L) return ActionBattlePoisonState.TickResult.NONE;
        if (activeState != null) {
            ActionBattlePoisonState.TickResult result = activeState.tick(currentTick);
            if (result == ActionBattlePoisonState.TickResult.COMPLETED_NATURALLY) {
                activeState = null;
                moveAccumulationGain = Math.max(1, moveAccumulationGain - 1);
                cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattlePoisonRules.CLEAN_RESET_TICKS);
            }
            return result;
        }
        if (cleanResetEndTick >= 0L && currentTick >= cleanResetEndTick) {
            moveAccumulationGain = ActionBattlePoisonRules.BASE_MOVE_GAIN;
            cleanResetEndTick = -1L;
            return ActionBattlePoisonState.TickResult.CHANGED;
        }
        return ActionBattlePoisonState.TickResult.NONE;
    }

    public void suppressSpecialAttackByHaze() {
        if (activeState != null) activeState.suppressSpecialAttackByHaze();
    }

    public Optional<ActionBattlePoisonState> activeState() { return Optional.ofNullable(activeState); }
    public int moveAccumulationGain() { return moveAccumulationGain; }
    public long cleanResetEndTick() { return cleanResetEndTick; }
    public boolean poisonTyped() { return poisonTyped; }
    public boolean isEmpty() {
        return activeState == null
                && moveAccumulationGain == ActionBattlePoisonRules.BASE_MOVE_GAIN
                && cleanResetEndTick < 0L;
    }
}
