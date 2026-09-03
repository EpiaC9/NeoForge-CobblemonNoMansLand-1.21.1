package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleIceState {
    private final int hitsRequired;
    private Phase phase = Phase.CHILL;
    private int currentHits;
    private long stackWindowStartTick = -1L;
    private long lifecycleEndTick;
    private long frostbiteEndTick = -1L;
    private int ownedDefenseStages;
    private boolean defenseContributionSuppressedByHaze;

    ActionBattleIceState(int hitsRequired, long currentTick) {
        if (hitsRequired < ActionBattleIceRules.BASE_HITS_REQUIRED) {
            throw new IllegalArgumentException("Ice hit requirement cannot be below the base requirement.");
        }
        if (currentTick < 0L) throw new IllegalArgumentException("Current tick cannot be negative.");
        this.hitsRequired = hitsRequired;
        lifecycleEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleIceRules.LIFECYCLE_TICKS);
    }

    boolean applyApplication(long currentTick, boolean iceTyped, boolean hazeActive) {
        if (currentTick < 0L || phase == Phase.FROSTBITE) return false;
        expireStackWindow(currentTick);
        if (currentHits == 0) stackWindowStartTick = currentTick;
        currentHits++;
        if (currentHits < hitsRequired) return true;
        currentHits = 0;
        stackWindowStartTick = -1L;
        if (phase == Phase.CHILL) {
            phase = Phase.FREEZE;
        } else {
            phase = Phase.FROSTBITE;
            lifecycleEndTick = -1L;
            frostbiteEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleIceRules.FROSTBITE_DURATION_TICKS);
        }
        establishDefenseContribution(iceTyped, hazeActive);
        return true;
    }

    boolean tick(long currentTick) {
        if (currentTick < 0L) return false;
        if (phase == Phase.FROSTBITE) return currentTick >= frostbiteEndTick;
        if (currentTick >= lifecycleEndTick) return true;
        expireStackWindow(currentTick);
        return false;
    }

    public void suppressDefenseContributionByHaze() {
        if (ownedDefenseStages == 0) return;
        ownedDefenseStages = 0;
        defenseContributionSuppressedByHaze = true;
    }

    public Phase phase() { return phase; }
    public int currentHits() { return currentHits; }
    public int hitsRequired() { return hitsRequired; }
    public long stackWindowStartTick() { return stackWindowStartTick; }
    public long lifecycleEndTick() { return lifecycleEndTick; }
    public long frostbiteEndTick() { return frostbiteEndTick; }
    public int ownedDefenseStages() { return ownedDefenseStages; }
    public boolean defenseContributionSuppressedByHaze() { return defenseContributionSuppressedByHaze; }
    public boolean isFrostbitten() { return phase == Phase.FROSTBITE; }

    public long frostbiteRemainingTicks(long currentTick) {
        return isFrostbitten() && currentTick >= 0L ? Math.max(0L, frostbiteEndTick - currentTick) : 0L;
    }

    private void expireStackWindow(long currentTick) {
        if (stackWindowStartTick < 0L) return;
        long endTick = ActionBattleTiming.safeAdd(stackWindowStartTick, ActionBattleIceRules.STACK_WINDOW_TICKS);
        if (currentTick < endTick) return;
        currentHits = 0;
        stackWindowStartTick = -1L;
    }

    private void establishDefenseContribution(boolean iceTyped, boolean hazeActive) {
        defenseContributionSuppressedByHaze = hazeActive;
        ownedDefenseStages = hazeActive ? 0 : ActionBattleIceRules.defenseStages(phase, iceTyped);
    }

    public enum Phase { CHILL, FREEZE, FROSTBITE }
}
