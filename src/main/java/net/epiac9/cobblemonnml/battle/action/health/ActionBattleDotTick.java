package net.epiac9.cobblemonnml.battle.action.health;

import java.util.UUID;

public record ActionBattleDotTick(UUID handleId, String effectId,
                                  UUID sourcePokemonUUID, UUID targetPokemonUUID,
                                  long startTick, long scheduledTick, long tickIndex,
                                  ActionBattleDotSpec spec) {
    public ActionBattleDotTick {
        if (handleId == null || effectId == null || effectId.isBlank() || targetPokemonUUID == null || spec == null) {
            throw new IllegalArgumentException("DoT tick metadata is incomplete.");
        }
        if (!effectId.equals(spec.effectId()) || startTick < 0L || scheduledTick < startTick || tickIndex <= 0L) {
            throw new IllegalArgumentException("DoT tick metadata is inconsistent.");
        }
    }

    public int requestedDamage(int currentHealth, int maxHealth) {
        return spec.requestedDamage(new ActionBattleDotDamageContext(
                sourcePokemonUUID, targetPokemonUUID, currentHealth, maxHealth,
                tickIndex, scheduledTick, startTick));
    }

    public ActionBattleDamageSource damageSource() {
        return ActionBattleDamageSource.dot(effectId);
    }

    public boolean canKo() { return true; }
    public boolean countsAsMoveHit() { return false; }
    public boolean canCrit() { return false; }
    public boolean triggersContact() { return false; }
    public boolean triggersTypeMechanics() { return false; }
    public boolean triggersMoveSecondaryEffects() { return false; }
    public boolean blockedByProtectAfterApplication() { return false; }
}
