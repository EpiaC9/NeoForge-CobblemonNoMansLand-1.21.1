package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import java.util.UUID;

public record ActionBattleGhostCast(UUID castId, UUID battleId, UUID casterPokemonUUID,
                                    boolean ghostCaster, boolean armed) {
    public ActionBattleGhostCast {
        if (castId == null || battleId == null || casterPokemonUUID == null) {
            throw new IllegalArgumentException("Ghost cast identifiers cannot be null.");
        }
    }
}
