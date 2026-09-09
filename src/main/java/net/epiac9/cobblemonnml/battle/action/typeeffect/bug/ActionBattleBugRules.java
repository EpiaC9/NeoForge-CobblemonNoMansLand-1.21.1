package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class ActionBattleBugRules {
    public static final long LOCKOUT_TICKS = 9L * 20L;
    public static final long CARAPACE_TICKS = 18L * 20L;
    public static final long DOT_INTERVAL_TICKS = 20L;
    public static final int DOT_TICK_COUNT = 6;
    public static final double CONSTRUCT_RADIUS = 6.0D;

    private ActionBattleBugRules() {}

    public static boolean qualifies(String moveType, int power, boolean validEnemyTarget) {
        return moveType != null && "bug".equalsIgnoreCase(moveType) && power > 0 && validEnemyTarget;
    }

    public static boolean effectZoneProtects(boolean allied, boolean zoneActive, double distanceSquared) {
        return allied && zoneActive && distanceSquared >= 0.0D
                && distanceSquared <= CONSTRUCT_RADIUS * CONSTRUCT_RADIUS;
    }

    public static EnumSet<ActionBattleBugTrainingStat> highest(int hp, int attack, int defense,
                                                                int specialAttack, int specialDefense,
                                                                int speed) {
        int[] scores = {hp, attack, defense, specialAttack, specialDefense, speed};
        for (int score : scores) if (score < 0) return EnumSet.noneOf(ActionBattleBugTrainingStat.class);
        int maximum = 0;
        for (int score : scores) maximum = Math.max(maximum, score);
        EnumSet<ActionBattleBugTrainingStat> result = EnumSet.noneOf(ActionBattleBugTrainingStat.class);
        ActionBattleBugTrainingStat[] stats = ActionBattleBugTrainingStat.values();
        for (int index = 0; index < scores.length; index++) if (scores[index] == maximum) result.add(stats[index]);
        return result;
    }

    public static int sheddingHp(int maxHp, boolean bugTyped) {
        return ceilPercent(maxHp, bugTyped ? 0.20D : 0.10D);
    }

    public static int secondaryDamage(int actualMoveDamage, boolean bugTyped) {
        return ceilPercent(actualMoveDamage, bugTyped ? 0.70D : 0.50D);
    }

    public static int delayedDotTotal(int absorbed, boolean bugTyped) {
        return ceilPercent(absorbed, bugTyped ? 0.50D : 0.70D);
    }

    public static List<Integer> delayedDotTicks(int total) {
        int safe = Math.max(0, total);
        int quotient = safe / DOT_TICK_COUNT;
        int remainder = safe % DOT_TICK_COUNT;
        List<Integer> ticks = new ArrayList<>(DOT_TICK_COUNT);
        for (int index = 0; index < DOT_TICK_COUNT; index++) ticks.add(quotient + (index < remainder ? 1 : 0));
        return List.copyOf(ticks);
    }

    public static double dashDistance(boolean bugTyped) { return bugTyped ? 2.0D : 1.0D; }
    public static int penaltyStages(boolean bugTyped) { return bugTyped ? -1 : -2; }
    public static double slowdownMultiplier(boolean bugTyped) { return bugTyped ? 0.80D : 0.70D; }

    private static int ceilPercent(int amount, double fraction) {
        return amount <= 0 ? 0 : (int) Math.ceil(amount * fraction);
    }
}
