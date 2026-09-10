package net.epiac9.cobblemonnml.battle.action.health;

public final class ActionBattleHealingRules {
    public static final double HEAL_BLOCK_MULTIPLIER = 0.25D;

    private ActionBattleHealingRules() {}

    public static int adjust(int requested, boolean healBlocked) {
        if (requested <= 0) return 0;
        return healBlocked ? Math.max(1, (int) Math.ceil(requested * HEAL_BLOCK_MULTIPLIER)) : requested;
    }
}
