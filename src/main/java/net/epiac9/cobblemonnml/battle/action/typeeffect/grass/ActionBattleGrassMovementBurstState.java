package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleGrassMovementBurstState {
    private long endTick;
    private long totalDurationTicks;

    public void apply(long currentTick) {
        if (active(currentTick)) {
            endTick += ActionBattleGrassRules.MOVEMENT_BURST_TICKS;
            totalDurationTicks += ActionBattleGrassRules.MOVEMENT_BURST_TICKS;
        } else {
            endTick = currentTick + ActionBattleGrassRules.MOVEMENT_BURST_TICKS;
            totalDurationTicks = ActionBattleGrassRules.MOVEMENT_BURST_TICKS;
        }
    }

    public boolean active(long currentTick) { return remainingTicks(currentTick) > 0L; }
    public long remainingTicks(long currentTick) { return Math.max(0L, endTick - currentTick); }
    public long totalDurationTicks() { return totalDurationTicks; }
}
