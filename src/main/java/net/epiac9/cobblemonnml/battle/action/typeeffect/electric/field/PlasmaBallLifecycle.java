package net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;

import java.util.UUID;

public record PlasmaBallLifecycle(UUID sessionId, UUID battleId, UUID ownerPokemonId,
                                  ActionBattleFieldObject.OwnerSide ownerSide, long sequence) {
    public PlasmaBallLifecycle {
        if (sessionId == null || battleId == null || ownerPokemonId == null || ownerSide == null) {
            throw new IllegalArgumentException("Plasma Ball ownership must be complete.");
        }
        sequence = Math.max(0L, sequence);
    }
}
