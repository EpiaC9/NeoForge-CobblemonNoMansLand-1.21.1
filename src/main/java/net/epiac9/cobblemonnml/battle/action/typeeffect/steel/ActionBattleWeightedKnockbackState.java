package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

public final class ActionBattleWeightedKnockbackState {
    private final double intendedDistance;
    private final int resolvedTriggerDamage;
    private double travelled;
    private boolean resolved;

    public ActionBattleWeightedKnockbackState(double intendedDistance, int resolvedTriggerDamage) {
        this.intendedDistance = Math.max(0.0D, intendedDistance);
        this.resolvedTriggerDamage = Math.max(0, resolvedTriggerDamage);
    }

    public Result advance(double actualDistance, boolean solidBlocked) {
        if (resolved) return Result.NONE;
        travelled += Math.max(0.0D, actualDistance);
        if (solidBlocked && travelled + 0.001D < intendedDistance) {
            resolved = true;
            return Result.COLLISION;
        }
        if (travelled + 0.001D >= intendedDistance) {
            resolved = true;
            return Result.COMPLETE;
        }
        return Result.NONE;
    }

    public int followupDamage() { return ActionBattleSteelRules.collisionDamage(resolvedTriggerDamage); }
    public double remainingDistance() { return Math.max(0.0D, intendedDistance - travelled); }
    public enum Result { NONE, COMPLETE, COLLISION }
}
