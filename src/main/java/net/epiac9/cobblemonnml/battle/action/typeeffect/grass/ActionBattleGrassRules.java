package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;

public final class ActionBattleGrassRules {
    public static final long SEED_ARM_TICKS = 180L;
    public static final long FLOWER_LIFETIME_TICKS = 360L;
    public static final long LEECH_SEED_DURATION_TICKS = 180L;
    public static final long MOVEMENT_BURST_TICKS = 10L;
    public static final double ALLY_EMPOWER = 1.10D;
    public static final double GRASS_ALLY_EMPOWER = 1.20D;
    public static final double MOVEMENT_MULTIPLIER = 1.20D;
    public static final int MAX_FIELD_OBJECTS = 9;

    private ActionBattleGrassRules() {}

    public static int leechHealAmount(int actualDamage, boolean grassDealer) {
        return ActionBattlePokemonHealth.ceilPercent(actualDamage, grassDealer ? 0.10D : 0.05D);
    }

    public static int reactivationDamage(int maximumHealth) {
        return ActionBattlePokemonHealth.ceilPercent(maximumHealth, 0.05D);
    }

    public static int waveHealAmount(int actualReactivationDamage) {
        return ActionBattlePokemonHealth.ceilPercent(actualReactivationDamage, 0.50D);
    }

    public static double applyCommittedEmpower(double damage, double multiplier) {
        return Math.max(0.0D, damage);
    }
}
