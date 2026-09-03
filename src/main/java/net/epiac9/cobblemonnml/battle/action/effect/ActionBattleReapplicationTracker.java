package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleReapplicationTracker {
    private static final int REDUCED_APPLICATION = 1;
    private static final int MINIMUM_APPLICATION = 2;

    private int completedApplications;
    private long inactiveSinceTick = -1L;

    public long durationForApplication(long baseDurationTicks, long currentTick) {
        resetIfReady(currentTick);
        if (baseDurationTicks <= 0L) return 0L;
        inactiveSinceTick = -1L;
        return switch (completedApplications) {
            case 0 -> baseDurationTicks;
            case REDUCED_APPLICATION -> Math.max(1L, Math.round(baseDurationTicks * (2.0D / 3.0D)));
            default -> Math.max(1L, Math.round(baseDurationTicks / 3.0D));
        };
    }

    public void onEffectEnded(long currentTick) {
        if (currentTick < 0L) return;
        completedApplications = Math.min(MINIMUM_APPLICATION, completedApplications + 1);
        inactiveSinceTick = currentTick;
    }

    public boolean isTracking(long currentTick) {
        resetIfReady(currentTick);
        return completedApplications > 0;
    }

    private void clear() {
        completedApplications = 0;
        inactiveSinceTick = -1L;
    }

    private void resetIfReady(long currentTick) {
        if (completedApplications == 0 || inactiveSinceTick < 0L || currentTick < inactiveSinceTick) return;
        if (currentTick - inactiveSinceTick >= ActionBattleTiming.UNIVERSAL_RESET_WINDOW_TICKS) clear();
    }
}
