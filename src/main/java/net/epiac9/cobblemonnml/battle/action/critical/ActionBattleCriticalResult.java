package net.epiac9.cobblemonnml.battle.action.critical;

public record ActionBattleCriticalResult(int baseStage, int flyingBonus, int effectiveStage,
                                         double roll, boolean critical) {
    public ActionBattleCriticalResult {
        baseStage = Math.max(0, baseStage);
        flyingBonus = Math.max(0, flyingBonus);
        effectiveStage = ActionBattleCriticalRules.clampStage(effectiveStage);
        roll = Double.isFinite(roll) ? Math.max(0.0D, Math.min(1.0D, roll)) : 1.0D;
    }

    public static ActionBattleCriticalResult none() {
        return new ActionBattleCriticalResult(0, 0, 0, 1.0D, false);
    }
}
