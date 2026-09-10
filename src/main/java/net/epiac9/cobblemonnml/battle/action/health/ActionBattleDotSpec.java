package net.epiac9.cobblemonnml.battle.action.health;

public record ActionBattleDotSpec(String effectId, long tickIntervalTicks, long durationTicks,
                                  DamageFormula damageFormula) {
    public static final long OWNER_MANAGED_DURATION = 0L;

    @FunctionalInterface
    public interface DamageFormula {
        int requestedDamage(ActionBattleDotDamageContext context);
    }

    public ActionBattleDotSpec {
        if (effectId == null || effectId.isBlank()) throw new IllegalArgumentException("DoT effect ID cannot be blank.");
        if (tickIntervalTicks <= 0L) throw new IllegalArgumentException("DoT interval must be positive.");
        if (durationTicks < 0L) throw new IllegalArgumentException("DoT duration cannot be negative.");
        if (damageFormula == null) throw new IllegalArgumentException("DoT damage formula cannot be null.");
    }

    public int requestedDamage(ActionBattleDotDamageContext context) {
        if (context == null) return 0;
        return Math.max(0, damageFormula.requestedDamage(context));
    }

    public boolean ownerManagedDuration() {
        return durationTicks == OWNER_MANAGED_DURATION;
    }
}
