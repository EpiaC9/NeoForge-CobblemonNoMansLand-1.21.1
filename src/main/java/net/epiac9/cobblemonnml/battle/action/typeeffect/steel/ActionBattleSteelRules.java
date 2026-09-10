package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;

import java.util.Map;

public final class ActionBattleSteelRules {
    public static final long BASE_DURATION_TICKS = ActionBattleTiming.seconds(9L);
    public static final long HISTORY_RESET_TICKS = ActionBattleTiming.seconds(18L);
    public static final double MAGNET_RISE_PROJECTILE_SPEED = 3.0D;
    public static final long MAGNET_RISE_INTERRUPT_TICKS = 10L;
    public static final double WEIGHTED_KNOCKBACK_BLOCKS = 3.0D;
    public static final double WEIGHTED_COLLISION_DAMAGE_MULTIPLIER = 1.5D;
    public static final long WEIGHTED_COLLISION_INTERRUPT_TICKS = 30L;
    private static final long[] DURATIONS = {180L, 120L, 60L, 20L};

    private ActionBattleSteelRules() {}

    public static boolean qualifies(String targetCategory) {
        String target = normalize(targetCategory);
        return "self".equals(target) || "user".equals(target);
    }

    public static long durationTicks(int completedApplications) {
        return DURATIONS[Math.clamp(completedApplications, 0, DURATIONS.length - 1)];
    }

    public static Map<ActionBattleStat, Integer> statPlan(Branch branch, boolean steelTyped) {
        if (branch == null) return Map.of();
        int amount = 2;
        return branch == Branch.MAGNET_RISE
                ? Map.of(ActionBattleStat.ATTACK, amount, ActionBattleStat.SPECIAL_ATTACK, amount,
                ActionBattleStat.DEFENSE, -amount, ActionBattleStat.SPECIAL_DEFENSE, -amount)
                : Map.of(ActionBattleStat.DEFENSE, amount, ActionBattleStat.SPECIAL_DEFENSE, amount,
                ActionBattleStat.ATTACK, -amount, ActionBattleStat.SPECIAL_ATTACK, -amount);
    }

    public static int collisionDamage(int resolvedTriggerDamage) {
        return resolvedTriggerDamage <= 0 ? 0
                : Math.max(1, (int) Math.ceil(resolvedTriggerDamage * WEIGHTED_COLLISION_DAMAGE_MULTIPLIER));
    }

    public static double projectileSpeed(double baseSpeed, boolean magnetRiseActive,
                                         boolean targetedDamagingProjectile) {
        return magnetRiseActive && targetedDamagingProjectile
                ? MAGNET_RISE_PROJECTILE_SPEED : baseSpeed;
    }

    public static boolean weightedMeleeQualifies(boolean weightedActive, boolean targeted,
                                                  boolean damagingMelee) {
        return weightedActive && targeted && damagingMelee;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
    }

    public enum Branch { MAGNET_RISE, WEIGHTED }
}
