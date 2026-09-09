package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import java.util.EnumSet;
import java.util.UUID;

public record ActionBattleBugCast(UUID castId, UUID battleId, UUID userPokemonId,
                                  UUID intendedTargetPokemonId, boolean bugTyped,
                                  EnumSet<ActionBattleBugTrainingStat> activated,
                                  String moveName, boolean ranged) {
    public ActionBattleBugCast {
        activated = activated == null ? EnumSet.noneOf(ActionBattleBugTrainingStat.class) : activated.clone();
        moveName = moveName == null ? "" : moveName;
    }
}
