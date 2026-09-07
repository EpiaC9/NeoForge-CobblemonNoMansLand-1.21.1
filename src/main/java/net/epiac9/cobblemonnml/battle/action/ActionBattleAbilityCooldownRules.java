package net.epiac9.cobblemonnml.battle.action;

public final class ActionBattleAbilityCooldownRules {
    private ActionBattleAbilityCooldownRules() {}

    public static Plan normal() {
        return new Plan(ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS,
                ActionBattleTiming.PERSONAL_MOVE_BASE_COOLDOWN_TICKS);
    }

    public record Plan(long sharedTicks, long personalTicks) {
        public Plan {
            sharedTicks = Math.max(0L, sharedTicks);
            personalTicks = Math.max(0L, personalTicks);
        }
    }
}
