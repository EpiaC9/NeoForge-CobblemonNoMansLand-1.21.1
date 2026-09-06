package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.UUID;

public record ActionBattleStatContribution(
        UUID id,
        ActionBattleStat stat,
        int stages,
        long appliedAtTick,
        long endTick,
        ActionBattleStatSource source
) {
    public ActionBattleStatContribution {
        if (id == null) throw new IllegalArgumentException("Contribution ID cannot be null.");
        if (stat == null) throw new IllegalArgumentException("Stat cannot be null.");
        if (stages == 0 || Math.abs(stages) > ActionBattleStatRules.maxStage(stat)) {
            throw new IllegalArgumentException("Contribution stages exceed the allowed range for " + stat + ".");
        }
        if (appliedAtTick < 0L || endTick < appliedAtTick) {
            throw new IllegalArgumentException("Contribution ticks are invalid.");
        }
        if (source == null) throw new IllegalArgumentException("Contribution source cannot be null.");
    }

    public boolean isActive(long currentTick) {
        return currentTick >= appliedAtTick && currentTick < endTick;
    }
}
