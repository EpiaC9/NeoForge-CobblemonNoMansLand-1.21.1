package net.epiac9.cobblemonnml.battle.action.critical;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.function.DoubleSupplier;

public final class ActionBattleCriticalResolver {
    private ActionBattleCriticalResolver() {}

    public static ActionBattleCriticalResult commit(PokemonEntity attacker, Move move,
                                                     int flyingMomentum, DoubleSupplier rolls) {
        int baseStage = ActionBattleCriticalStageSources.stage(attacker, move);
        int flyingBonus = ActionBattleFlyingRules.criticalStageBonus(
                ActionBattleTypeMechanicIdentity.hasMechanicBenefit(attacker, "flying"), flyingMomentum);
        ActionBattleCriticalResult result = resolve(baseStage, flyingBonus, rolls);
        if (attacker != null && move != null) {
            DebugLog.log("[CobblemonNML] ACTION critical committed. pokemon="
                    + attacker.getPokemon().getUuid() + ", move=" + move.getName()
                    + ", baseStage=" + result.baseStage() + ", flyingBonus=" + result.flyingBonus()
                    + ", stage=" + result.effectiveStage() + ", roll=" + result.roll()
                    + ", critical=" + result.critical());
        }
        return result;
    }

    public static ActionBattleCriticalResult resolve(int baseStage, int flyingBonus, DoubleSupplier rolls) {
        int normalizedBase = Math.max(0, baseStage);
        int normalizedBonus = Math.max(0, flyingBonus);
        int effective = ActionBattleCriticalRules.combineStages(normalizedBase, normalizedBonus);
        double roll = rolls != null ? rolls.getAsDouble() : 1.0D;
        return new ActionBattleCriticalResult(normalizedBase, normalizedBonus, effective, roll,
                ActionBattleCriticalRules.rollsCritical(effective, roll));
    }
}
