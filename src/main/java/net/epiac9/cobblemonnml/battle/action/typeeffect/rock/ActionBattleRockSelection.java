package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;

public record ActionBattleRockSelection(ActionBattleStat resistanceStat, ActionBattleStat damageStat) {
    public static ActionBattleRockSelection choose(double defense, int defenseStage,
                                                    double specialDefense, int specialDefenseStage,
                                                    double attack, int attackStage,
                                                    double specialAttack, int specialAttackStage,
                                                    boolean defenseTie, boolean attackTie) {
        double def = defense * ActionBattleStatRules.standardMultiplier(defenseStage);
        double spDef = specialDefense * ActionBattleStatRules.standardMultiplier(specialDefenseStage);
        double atk = attack * ActionBattleStatRules.standardMultiplier(attackStage);
        double spAtk = specialAttack * ActionBattleStatRules.standardMultiplier(specialAttackStage);
        ActionBattleStat resistance = def > spDef ? ActionBattleStat.DEFENSE
                : spDef > def ? ActionBattleStat.SPECIAL_DEFENSE
                : defenseTie ? ActionBattleStat.DEFENSE : ActionBattleStat.SPECIAL_DEFENSE;
        ActionBattleStat damage = atk > spAtk ? ActionBattleStat.ATTACK
                : spAtk > atk ? ActionBattleStat.SPECIAL_ATTACK
                : attackTie ? ActionBattleStat.ATTACK : ActionBattleStat.SPECIAL_ATTACK;
        return new ActionBattleRockSelection(resistance, damage);
    }
}
