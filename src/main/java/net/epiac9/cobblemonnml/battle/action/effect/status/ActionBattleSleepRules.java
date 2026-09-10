package net.epiac9.cobblemonnml.battle.action.effect.status;

public final class ActionBattleSleepRules {
    public static final long MIN_DURATION_TICKS = 60L;
    public static final long MAX_DURATION_TICKS = 180L;

    private ActionBattleSleepRules() {}

    public static int durationTicksFromRoll(int roll) {
        if (roll < 0 || roll > 6) {
            throw new IllegalArgumentException("Sleep duration roll must be between 0 and 6.");
        }
        return (3 + roll) * 20;
    }
}
