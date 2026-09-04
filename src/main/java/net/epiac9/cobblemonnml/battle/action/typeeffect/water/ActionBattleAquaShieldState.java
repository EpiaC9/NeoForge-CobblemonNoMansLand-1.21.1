package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleAquaShieldState {
    private final long instanceId;
    private final long startTick;
    private final long endTick;
    private final boolean healEligible;

    ActionBattleAquaShieldState(long instanceId, long startTick, boolean healEligible) {
        if (instanceId <= 0L || startTick < 0L) throw new IllegalArgumentException("Invalid Aqua Shield identity or tick.");
        this.instanceId = instanceId;
        this.startTick = startTick;
        this.endTick = startTick + ActionBattleWaterRules.AQUA_SHIELD_DURATION_TICKS;
        this.healEligible = healEligible;
    }

    public long instanceId() { return instanceId; }
    public long startTick() { return startTick; }
    public long endTick() { return endTick; }
    public boolean healEligible() { return healEligible; }
    public boolean active(long currentTick) { return currentTick >= startTick && currentTick < endTick; }
    public long remainingTicks(long currentTick) { return Math.max(0L, endTick - currentTick); }
}
