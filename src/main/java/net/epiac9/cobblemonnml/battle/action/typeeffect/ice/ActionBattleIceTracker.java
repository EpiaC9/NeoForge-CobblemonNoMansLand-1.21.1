package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleIceTracker {
    private ActionBattleIceState activeState;
    private int hitsRequired = ActionBattleIceRules.BASE_HITS_REQUIRED;
    private long resetEndTick = -1L;

    public boolean applyApplication(long currentTick, boolean iceTyped, boolean hazeActive) {
        if (currentTick < 0L) return false;
        tick(currentTick);
        if (activeState != null && activeState.isFrostbitten()) return false;
        if (activeState == null) {
            activeState = new ActionBattleIceState(hitsRequired, currentTick);
            resetEndTick = -1L;
        }
        return activeState.applyApplication(currentTick, iceTyped, hazeActive);
    }

    public boolean tick(long currentTick) {
        if (currentTick < 0L) return false;
        boolean changed = false;
        if (activeState != null && activeState.tick(currentTick)) {
            activeState = null;
            hitsRequired++;
            resetEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleIceRules.REAPPLICATION_RESET_TICKS);
            changed = true;
        }
        if (activeState == null && resetEndTick >= 0L && currentTick >= resetEndTick) {
            hitsRequired = ActionBattleIceRules.BASE_HITS_REQUIRED;
            resetEndTick = -1L;
            changed = true;
        }
        return changed;
    }

    public void suppressDefenseContributionByHaze() {
        if (activeState != null) activeState.suppressDefenseContributionByHaze();
    }

    public Optional<ActionBattleIceState> activeState() { return Optional.ofNullable(activeState); }
    public int hitsRequired() { return hitsRequired; }
    public int activeHitsRequired() { return activeState != null ? activeState.hitsRequired() : hitsRequired; }
    public long resetEndTick() { return resetEndTick; }
    public boolean isEmpty() {
        return activeState == null && hitsRequired == ActionBattleIceRules.BASE_HITS_REQUIRED && resetEndTick < 0L;
    }
}
