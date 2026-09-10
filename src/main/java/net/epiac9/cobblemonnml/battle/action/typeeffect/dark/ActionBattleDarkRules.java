package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

import net.epiac9.cobblemonnml.battle.action.ActionBattleRangeRules;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleDarkRules {
    public static final long BLINDNESS_DURATION_TICKS = 360L;
    public static final long AWARENESS_RECOVERY_INTERVAL_TICKS = 5L;
    public static final long OBSCURITY_RECOVERY_INTERVAL_TICKS = 20L;
    public static final double MINIMUM_AWARENESS = 1.0D;
    public static final double NORMAL_AWARENESS = ActionBattleRangeRules.DEFAULT_AWARENESS_RANGE;
    public static final int MAX_OBSCURITY_STAGE = 4;

    private ActionBattleDarkRules() {}

    public static int awarenessReduction(boolean attackerDarkTyped, boolean damagingMove) {
        if (attackerDarkTyped) return damagingMove ? 2 : 3;
        return damagingMove ? 1 : 2;
    }

    public static int obscurityIncrease(boolean attackerDarkTyped) {
        return attackerDarkTyped ? 1 : 0;
    }

    public static HitPlan planHit(boolean connected, boolean attackerDarkTyped,
                                  boolean targetDarkTyped, boolean targetPsychicTyped,
                                  boolean damagingMove) {
        if (!connected || !attackerDarkTyped || targetDarkTyped || targetPsychicTyped) return HitPlan.NONE;
        return new HitPlan(true, awarenessReduction(attackerDarkTyped, damagingMove),
                obscurityIncrease(attackerDarkTyped));
    }

    static long add(long tick, long duration) {
        return ActionBattleTiming.safeAdd(tick, duration);
    }

    public record HitPlan(boolean qualifies, int awarenessReduction, int obscurityIncrease) {
        public static final HitPlan NONE = new HitPlan(false, 0, 0);
    }
}
