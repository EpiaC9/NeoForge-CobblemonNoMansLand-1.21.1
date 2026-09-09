package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import java.util.Locale;

public final class ActionBattleSteelWeight {
    public static final double WEIGHTED_THRESHOLD = 800.0D;

    private ActionBattleSteelWeight() {}

    public static ActionBattleSteelRules.Branch select(double staticWeight, Override itemOverride) {
        if (itemOverride == Override.DECREASE) return ActionBattleSteelRules.Branch.MAGNET_RISE;
        if (itemOverride == Override.INCREASE) return ActionBattleSteelRules.Branch.WEIGHTED;
        return sanitize(staticWeight) < WEIGHTED_THRESHOLD
                ? ActionBattleSteelRules.Branch.MAGNET_RISE : ActionBattleSteelRules.Branch.WEIGHTED;
    }

    public static double modifiedWeight(double staticWeight, ActionBattleSteelRules.Branch branch) {
        double weight = sanitize(staticWeight);
        if (branch == ActionBattleSteelRules.Branch.MAGNET_RISE) return weight * 0.5D;
        if (branch == ActionBattleSteelRules.Branch.WEIGHTED) return weight * 1.5D;
        return weight;
    }

    public static Override overrideForItem(String itemId) {
        String normalized = itemId == null ? "" : itemId.trim().toLowerCase(Locale.ROOT);
        if ("cobblemon:float_stone".equals(normalized)) return Override.DECREASE;
        if ("cobblemon:iron_ball".equals(normalized)) return Override.INCREASE;
        return Override.NONE;
    }

    private static double sanitize(double weight) {
        return Double.isFinite(weight) ? Math.max(0.0D, weight) : 0.0D;
    }

    public enum Override { NONE, DECREASE, INCREASE }
}
