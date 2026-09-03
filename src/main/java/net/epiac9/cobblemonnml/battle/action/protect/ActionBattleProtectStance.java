package net.epiac9.cobblemonnml.battle.action.protect;

import java.util.UUID;

public record ActionBattleProtectStance(
        UUID battleId,
        UUID pokemonUUID,
        long startTick,
        long endTick,
        int deterioratingShieldLevel,
        float damageTakenMultiplier,
        float timedEffectDurationMultiplier
) {
    public boolean isActive(long currentTick) {
        return currentTick >= startTick && currentTick < endTick;
    }
}
