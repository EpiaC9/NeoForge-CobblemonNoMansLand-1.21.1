package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalResolver;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalResult;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules;

import java.util.function.DoubleSupplier;

public record ActionBattleCommittedMove(int flyingMomentum, ActionBattleCriticalResult critical) {
    public ActionBattleCommittedMove {
        flyingMomentum = ActionBattleFlyingRules.clampMomentum(flyingMomentum);
        critical = critical != null ? critical : ActionBattleCriticalResult.none();
    }

    public static ActionBattleCommittedMove capture(PokemonEntity attacker, Move move,
                                                     int flyingMomentum, DoubleSupplier rolls) {
        return new ActionBattleCommittedMove(flyingMomentum,
                ActionBattleCriticalResolver.commit(attacker, move, flyingMomentum, rolls));
    }

    public static ActionBattleCommittedMove none() {
        return new ActionBattleCommittedMove(0, ActionBattleCriticalResult.none());
    }
}
