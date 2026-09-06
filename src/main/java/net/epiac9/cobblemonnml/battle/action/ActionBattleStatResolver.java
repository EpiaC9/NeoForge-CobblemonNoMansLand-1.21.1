package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;

import java.util.UUID;

public final class ActionBattleStatResolver {
    private ActionBattleStatResolver() {}

    public static int effectiveStage(UUID battleId, UUID pokemonUUID, ActionBattleStat stat, long currentTick) {
        return ActionBattleEffectController.global().effectiveStage(battleId, pokemonUUID, stat, currentTick);
    }

    public static int combineStages(ActionBattleStat stat, int genericStages, int typeEffectStages) {
        return stat != null ? ActionBattleStatRules.clampStage(stat, genericStages + typeEffectStages) : 0;
    }

    public static double accuracyProjectileMultiplier(UUID battleId, UUID pokemonUUID, long currentTick) {
        return ActionBattleStatRules.accuracyProjectileMultiplier(
                effectiveStage(battleId, pokemonUUID, ActionBattleStat.ACCURACY, currentTick));
    }
}
