package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

public final class ActionBattleGroundVisualRules {
    private ActionBattleGroundVisualRules() {}

    public static boolean isSupportedDepth(int depthPercent) {
        return depthPercent == 0 || depthPercent == 30 || depthPercent == 45
                || depthPercent == 60 || depthPercent == 90;
    }

    public static double offsetY(double entityHeight, int depthPercent) {
        if (!Double.isFinite(entityHeight) || entityHeight <= 0.0D
                || !isSupportedDepth(depthPercent) || depthPercent <= 0) return 0.0D;
        return -(entityHeight * depthPercent / 100.0D);
    }
}
