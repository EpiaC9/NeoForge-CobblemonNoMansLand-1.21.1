package net.epiac9.cobblemonnml.battle.action.protect;

public final class ActionBattleDeterioratingShieldState {
    public static final int MAX_LEVEL = 9;
    private int level;
    private long remainingTicks;

    public int level() { return level; }
    public long remainingTicks() { return remainingTicks; }
    public boolean isActive() { return level > 0 && remainingTicks > 0L; }

    public int increaseLevel() {
        level = Math.min(MAX_LEVEL, level + 1);
        remainingTicks = level * 200L;
        return level;
    }

    public void reduceForNonProtectMove() {
        if (!isActive()) return;
        decay(100L);
    }

    public void tick(boolean recalled) {
        if (!isActive()) return;
        decay(recalled ? 2L : 1L);
    }

    public float damageTakenMultiplier() {
        return isActive() ? Math.min(0.9F, level * 0.1F) : 1.0F;
    }

    public float timedEffectDurationMultiplier() {
        if (!isActive()) return 1.0F;
        return switch (level) {
            case 6 -> 0.2F;
            case 7 -> 0.4F;
            case 8 -> 0.8F;
            case 9 -> 1.0F;
            default -> 0.0F;
        };
    }

    public boolean allowsDrowsiness() { return isActive() && level == 9; }

    public void clear() {
        level = 0;
        remainingTicks = 0L;
    }

    private void normalize() {
        if (remainingTicks <= 0L) clear();
    }

    private void decay(long ticks) {
        remainingTicks -= ticks;
        normalize();
    }
}
