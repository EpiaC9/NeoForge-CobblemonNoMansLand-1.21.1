package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveTargetRules;

public final class ActionBattlePsycUpMoveRules {
    private ActionBattlePsycUpMoveRules() {}

    public static boolean qualifies(String moveType, String targetCategory, int movePower,
                                    boolean successfullyResolvedAgainstEnemy) {
        return successfullyResolvedAgainstEnemy
                && movePower == 0
                && moveType != null
                && moveType.equalsIgnoreCase("psychic")
                && targetCategory != null
                && !targetCategory.isBlank()
                && !ActionBattleMoveTargetRules.usesCasterInSingles(targetCategory);
    }
}
