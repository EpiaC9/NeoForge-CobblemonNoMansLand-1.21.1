package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public final class ActionBattleBugOffense {
    private ActionBattleBugOffense() {}

    public static OffensePlan plan(boolean attackAvailable, boolean specialAttackAvailable,
                                   int actualMoveDamage, boolean bugTyped) {
        int damage = ActionBattleBugRules.secondaryDamage(actualMoveDamage, bugTyped);
        if (damage <= 0 || !attackAvailable && !specialAttackAvailable) return OffensePlan.NONE;
        if (specialAttackAvailable) return new OffensePlan(false, true, damage);
        return new OffensePlan(true, false, damage);
    }

    public record OffensePlan(boolean singleTarget, boolean area, int damage) {
        public static final OffensePlan NONE = new OffensePlan(false, false, 0);
    }
}
