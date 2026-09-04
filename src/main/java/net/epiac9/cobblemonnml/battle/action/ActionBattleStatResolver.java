package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;

import java.util.UUID;

public final class ActionBattleStatResolver {
    private ActionBattleStatResolver() {}

    public static int effectiveStage(UUID battleId, UUID pokemonUUID, ActionBattleStat stat, long currentTick) {
        int genericStages = ActionBattleEffectController.global().effectiveStage(battleId, pokemonUUID, stat, currentTick);
        int typeEffectStages = 0;
        UUID dungeonSessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (dungeonSessionId != null) {
            if (stat == ActionBattleStat.ATTACK) {
                typeEffectStages = ActionBattleTypeEffectController.global()
                        .fireAttackStages(dungeonSessionId, pokemonUUID, currentTick);
            } else if (stat == ActionBattleStat.DEFENSE) {
                typeEffectStages = ActionBattleTypeEffectController.global()
                        .iceDefenseStages(dungeonSessionId, pokemonUUID, currentTick);
            } else if (stat == ActionBattleStat.SPECIAL_DEFENSE) {
                typeEffectStages = ActionBattleTypeEffectController.global()
                        .fairySpecialDefenseStages(dungeonSessionId, pokemonUUID, currentTick);
            }
        }
        return combineStages(stat, genericStages, typeEffectStages);
    }

    public static int combineStages(ActionBattleStat stat, int genericStages, int typeEffectStages) {
        return stat != null ? ActionBattleStatRules.clampStage(stat, genericStages + typeEffectStages) : 0;
    }

    public static double accuracyProjectileMultiplier(UUID battleId, UUID pokemonUUID, long currentTick) {
        return ActionBattleStatRules.accuracyProjectileMultiplier(
                effectiveStage(battleId, pokemonUUID, ActionBattleStat.ACCURACY, currentTick));
    }
}
