package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

public final class ActionBattleElectricRules {
    public static final int MAX_CHARGE = 100;
    public static final int BASE_DEPLETION_PER_TICK = 1;
    public static final long CLEAN_RESET_TICKS = 360L;
    public static final long PARALYSIS_DURATION_TICKS = 180L;
    public static final int NORMAL_PARALYSIS_SPEED_STAGE = -1;
    public static final int ELECTRIC_PARALYSIS_SPEED_STAGE = 1;
    public static final int NORMAL_FLINCH_THRESHOLD = 100;
    public static final int ELECTRIC_FLINCH_THRESHOLD = 200;
    public static final double ELECTRIC_PARALYSIS_DAMAGE_MULTIPLIER = 1.20D;

    private ActionBattleElectricRules() {}

    public static int saturatingIncrement(int value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    public static int penetratedAmount(int suppliedAmount, double multiplier) {
        if (suppliedAmount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0D) return 0;
        return (int) Math.max(0L, Math.round(suppliedAmount * Math.min(1.0D, multiplier)));
    }
}