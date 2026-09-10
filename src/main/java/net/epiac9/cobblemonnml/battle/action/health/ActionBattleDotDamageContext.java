package net.epiac9.cobblemonnml.battle.action.health;

import java.util.UUID;

public record ActionBattleDotDamageContext(UUID sourcePokemonUUID, UUID targetPokemonUUID,
                                           int currentHealth, int maxHealth,
                                           long tickIndex, long scheduledTick,
                                           long startTick) {
    public ActionBattleDotDamageContext {
        if (targetPokemonUUID == null) throw new IllegalArgumentException("DoT target cannot be null.");
        if (currentHealth < 0 || maxHealth <= 0 || currentHealth > maxHealth) {
            throw new IllegalArgumentException("DoT health snapshot is invalid.");
        }
        if (tickIndex <= 0L || scheduledTick < 0L || startTick < 0L || scheduledTick < startTick) {
            throw new IllegalArgumentException("DoT timing snapshot is invalid.");
        }
    }
}
