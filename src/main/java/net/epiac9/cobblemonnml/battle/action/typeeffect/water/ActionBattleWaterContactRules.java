package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterContactRules {
    public enum ActivationResult { IGNORED, ENEMY_TRAPPED }

    private ActionBattleWaterContactRules() {}

    public static ActivationResult resolveContact(boolean allied, boolean waterTyped) {
        return allied ? ActivationResult.IGNORED : ActivationResult.ENEMY_TRAPPED;
    }

    public static boolean isQualifyingInteraction(String moveType, boolean damaging, int movePower,
                                                   String targetCategory) {
        return true;
    }
}
