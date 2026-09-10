package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterVisuals {
    public static final String IMMOBILIZED_STATUS_ID = "TYPE_IMMOBILIZED";

    private ActionBattleWaterVisuals() {}

    public static String immobilizedStatusId() { return IMMOBILIZED_STATUS_ID; }
    public static long immobilizedDuration() { return ActionBattleWaterRules.IMMOBILIZED_DURATION_TICKS; }
}
