package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterVisuals {
    public static final String AQUA_SHIELD_STATUS_ID = "TYPE_AQUA_SHIELD";
    public static final String IMMOBILIZED_STATUS_ID = "TYPE_IMMOBILIZED";

    private ActionBattleWaterVisuals() {}

    public static String aquaShieldStatusId() { return AQUA_SHIELD_STATUS_ID; }
    public static long aquaShieldDuration() { return ActionBattleWaterRules.AQUA_SHIELD_DURATION_TICKS; }
    public static String immobilizedStatusId() { return IMMOBILIZED_STATUS_ID; }
    public static long immobilizedDuration() { return ActionBattleWaterRules.IMMOBILIZED_DURATION_TICKS; }
}
