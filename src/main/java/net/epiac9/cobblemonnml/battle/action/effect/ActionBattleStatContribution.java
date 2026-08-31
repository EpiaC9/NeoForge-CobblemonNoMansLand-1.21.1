package net.epiac9.cobblemonnml.battle.action.effect;

public record ActionBattleStatContribution(ActionBattleStat stat, int stages, long endTick) {
    public ActionBattleStatContribution {
        if (stat == null) throw new IllegalArgumentException("Stat cannot be null.");
        if (stages == 0 || stages < -6 || stages > 6) throw new IllegalArgumentException("Contribution stages must be between -6 and +6, excluding 0.");
        if (endTick < 0L) throw new IllegalArgumentException("Contribution end tick cannot be negative.");
    }

    public boolean isActive(long currentTick) {
        return currentTick >= 0L && currentTick < endTick;
    }

    public ActionBattleStatContribution refresh(long newEndTick) {
        return new ActionBattleStatContribution(stat, stages, newEndTick);
    }
}
