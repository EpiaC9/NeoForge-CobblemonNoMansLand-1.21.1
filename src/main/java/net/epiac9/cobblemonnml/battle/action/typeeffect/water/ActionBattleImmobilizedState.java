package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleImmobilizedState {
    private final long startTick;
    private final long endTick;

    ActionBattleImmobilizedState(long startTick) {
        if (startTick < 0L) throw new IllegalArgumentException("Immobilized start tick cannot be negative.");
        this.startTick = startTick;
        this.endTick = startTick + ActionBattleWaterRules.IMMOBILIZED_DURATION_TICKS;
    }

    public long startTick() { return startTick; }
    public long endTick() { return endTick; }
    public boolean active(long currentTick) { return currentTick >= startTick && currentTick < endTick; }
    public long remainingTicks(long currentTick) { return Math.max(0L, endTick - currentTick); }
}
