package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterRules {
    public static final long BUBBLE_LIFETIME_TICKS = 360L;
    public static final long AQUA_SHIELD_DURATION_TICKS = 180L;
    public static final long IMMOBILIZED_DURATION_TICKS = 40L;
    public static final long MOVEMENT_PENALTY_TICKS = 40L;
    public static final int MAX_BUBBLES_PER_OWNER = 6;

    private ActionBattleWaterRules() {}

    public static int healAmount(int maxHealth) {
        return Math.max(1, (int) Math.floor(Math.max(1, maxHealth) * 0.10D));
    }
}
