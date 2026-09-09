package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

public final class ActionBattleFlyingState {
    private static final double NORMAL_BUILD_PER_TICK = 1.0D / 60.0D;
    private static final double FLYING_BUILD_PER_TICK = 1.0D / 30.0D;
    private static final double DECAY_PER_TICK = 0.10D;

    private double progress;
    private long lastUpdatedTick = Long.MIN_VALUE;

    public boolean tick(boolean flyingPokemon, boolean visibleEnemy, long currentTick) {
        if (currentTick < 0L || currentTick == lastUpdatedTick) return false;
        lastUpdatedTick = currentTick;
        int before = level();
        if (visibleEnemy) {
            progress = Math.min(ActionBattleFlyingRules.MAX_MOMENTUM,
                    progress + (flyingPokemon ? FLYING_BUILD_PER_TICK : NORMAL_BUILD_PER_TICK));
        } else {
            progress = Math.max(0.0D, progress - DECAY_PER_TICK);
        }
        return level() != before;
    }

    public int level() {
        return ActionBattleFlyingRules.clampMomentum((int) Math.floor(progress + 1.0E-9D));
    }

    public double progress() {
        return progress;
    }
}
