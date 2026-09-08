package net.epiac9.cobblemonnml.battle.action;

public final class ActionBattleTargetingRules {
    private ActionBattleTargetingRules() {}

    public static boolean canPerceive(double hitboxGapSquared, double awareness) {
        return ActionBattleRangeRules.withinHitboxRangeSquared(hitboxGapSquared, awareness);
    }

    public static boolean maySelectMove(boolean enemyVisible, boolean enemyTargeted) {
        return !enemyTargeted || enemyVisible;
    }

    public static boolean shouldPursue(boolean enemyVisible, boolean enemyTargeted) {
        return enemyVisible && enemyTargeted;
    }
}
