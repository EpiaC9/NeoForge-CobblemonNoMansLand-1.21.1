package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import java.util.Optional;

public final class ActionBattleWaterState {
    public record ImmobilizedView(long remainingTicks, long totalDurationTicks) {}
    private ActionBattleImmobilizedState immobilized;

    public boolean applyImmobilized(long currentTick) {
        requireTick(currentTick);
        immobilized = new ActionBattleImmobilizedState(currentTick);
        return true;
    }

    public boolean breakImmobilizedOnDamage(int actualDamage) {
        if (actualDamage <= 0 || immobilized == null) return false;
        immobilized = null;
        return true;
    }

    public void tick(long currentTick) {
        requireTick(currentTick);
        if (immobilized != null && !immobilized.active(currentTick)) immobilized = null;
    }

    public Optional<ImmobilizedView> immobilizedView(long currentTick) {
        tick(currentTick);
        return immobilized == null ? Optional.empty() : Optional.of(new ImmobilizedView(
                immobilized.remainingTicks(currentTick), ActionBattleWaterRules.IMMOBILIZED_DURATION_TICKS));
    }

    public void clearSilently() {
        immobilized = null;
    }

    public boolean isEmpty() { return immobilized == null; }

    private static void requireTick(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("Water state tick cannot be negative.");
    }
}
