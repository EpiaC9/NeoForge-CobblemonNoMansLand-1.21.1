package net.epiac9.cobblemonnml.battle.action.area;

public record ActionBattlePersistentAreaPreset(double horizontalRadius, double verticalHeight, int durationTicks, int pulseIntervalTicks, boolean pulseImmediately) {
    public ActionBattlePersistentAreaPreset {
        if (!(horizontalRadius > 0.0D)) throw new IllegalArgumentException("horizontalRadius must be > 0");
        if (!(verticalHeight > 0.0D)) throw new IllegalArgumentException("verticalHeight must be > 0");
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be > 0");
        if (pulseIntervalTicks <= 0) throw new IllegalArgumentException("pulseIntervalTicks must be > 0");
    }
}
