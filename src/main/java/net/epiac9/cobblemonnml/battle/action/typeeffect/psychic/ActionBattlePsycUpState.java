package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

import java.util.UUID;

public record ActionBattlePsycUpState(
        UUID casterPokemonId,
        UUID markedPokemonId,
        boolean casterPsychicTyped,
        long startTick,
        long endTick
) {
    public ActionBattlePsycUpState {
        if (casterPokemonId == null || markedPokemonId == null || startTick < 0L || endTick <= startTick) {
            throw new IllegalArgumentException("Invalid Psyc Up state.");
        }
    }

    public boolean active(long currentTick) {
        return currentTick >= startTick && currentTick < endTick;
    }

    public long remainingTicks(long currentTick) {
        return currentTick < 0L ? 0L : Math.max(0L, endTick - currentTick);
    }
}
