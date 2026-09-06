package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleLeechSeedState {
    private final long endTick;

    public ActionBattleLeechSeedState(long startTick) {
        if (startTick < 0L) throw new IllegalArgumentException("Leech Seed start tick cannot be negative.");
        endTick = startTick + ActionBattleGrassRules.LEECH_SEED_DURATION_TICKS;
    }

    public boolean active(long currentTick) { return remainingTicks(currentTick) > 0L; }
    public long remainingTicks(long currentTick) { return Math.max(0L, endTick - currentTick); }
    public long endTick() { return endTick; }
}
