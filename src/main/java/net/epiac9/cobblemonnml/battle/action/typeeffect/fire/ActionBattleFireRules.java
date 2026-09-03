package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleFireRules {
    public static final double NORMAL_PRESSURE = 20.0D;
    public static final double CINDERS_THRESHOLD = 50.0D;
    public static final double BURN_THRESHOLD = 100.0D;
    public static final long PRESSURE_DECAY_DELAY_TICKS = ActionBattleTiming.UNIVERSAL_RESET_WINDOW_TICKS;
    public static final long PRESSURE_DECAY_STEP_TICKS = 20L;
    public static final double PRESSURE_DECAY_AMOUNT = 5.0D;
    public static final long BURN_DURATION_TICKS = 180L;
    public static final double BURN_FIRE_DAMAGE_MULTIPLIER = 1.20D;
    public static final int CINDERS_FIRE_ATTACK_STAGE = 1;
    public static final int BURN_FIRE_ATTACK_STAGE = 2;

    private ActionBattleFireRules() {}

    public static TargetInteraction targetInteraction(boolean fireTyped, boolean waterTyped) {
        if (fireTyped) return TargetInteraction.FIRE_POSITIVE;
        return waterTyped ? TargetInteraction.IMMUNE : TargetInteraction.HARMFUL;
    }

    public static double clampPressure(double pressure) {
        if (!Double.isFinite(pressure) || pressure <= 0.0D) return 0.0D;
        return Math.min(BURN_THRESHOLD, pressure);
    }

    public static double modifyIncomingDamage(double damage, boolean fireMove, boolean burnedTarget) {
        if (!(damage > 0.0D) || !fireMove || !burnedTarget) return damage;
        return damage * BURN_FIRE_DAMAGE_MULTIPLIER;
    }

    public enum TargetInteraction { FIRE_POSITIVE, IMMUNE, HARMFUL }
}
