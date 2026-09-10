package net.epiac9.cobblemonnml.battle.action.effect.control;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.UUID;

public record ActionBattleInfatuationState(UUID sourcePokemonId, long startTick, long endTick,
                                            double maximumApproachDistance) {
    public ActionBattleInfatuationState(UUID sourcePokemonId, long currentTick, double maximumApproachDistance) {
        this(sourcePokemonId, currentTick, ActionBattleTiming.safeAdd(currentTick,
                ActionBattleInfatuationRules.DURATION_TICKS), maximumApproachDistance);
    }

    public ActionBattleInfatuationState {
        if (sourcePokemonId == null || startTick < 0L || endTick <= startTick || maximumApproachDistance <= 0.0D) {
            throw new IllegalArgumentException("Invalid Infatuation state.");
        }
    }

    public boolean active(long currentTick) { return currentTick >= startTick && currentTick < endTick; }
    public long remainingTicks(long currentTick) { return active(currentTick) ? endTick - currentTick : 0L; }
}
