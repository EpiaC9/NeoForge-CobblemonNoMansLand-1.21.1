package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleSteelState {
    private Active active;
    private int completedApplications;
    private long inactiveSinceTick = -1L;

    public ApplyResult apply(Selection selection, boolean steelTyped, long currentTick) {
        if (selection == null || selection.branch() == null || currentTick < 0L) return ApplyResult.INVALID;
        tick(currentTick);
        if (active != null) return ApplyResult.IGNORED_ACTIVE;
        long duration = ActionBattleSteelRules.durationTicks(completedApplications);
        active = new Active(selection.branch(), selection.staticWeight(), steelTyped, currentTick,
                ActionBattleTiming.safeAdd(currentTick, duration));
        inactiveSinceTick = -1L;
        return ApplyResult.APPLIED;
    }

    public void tick(long currentTick) {
        if (currentTick < 0L) return;
        if (active != null && currentTick >= active.endTick()) {
            active = null;
            completedApplications = Math.min(3, completedApplications + 1);
            inactiveSinceTick = currentTick;
        }
        if (active == null && completedApplications > 0 && inactiveSinceTick >= 0L
                && currentTick - inactiveSinceTick >= ActionBattleSteelRules.HISTORY_RESET_TICKS) {
            completedApplications = 0;
            inactiveSinceTick = -1L;
        }
    }

    public Optional<View> view(long currentTick) {
        tick(currentTick);
        if (active == null) return Optional.empty();
        return Optional.of(new View(active.branch(), active.steelTyped(), active.staticWeight(),
                Math.max(0L, active.endTick() - currentTick),
                Math.max(1L, active.endTick() - active.startTick())));
    }

    public double effectiveWeight(long currentTick) {
        tick(currentTick);
        return active == null ? 0.0D
                : ActionBattleSteelWeight.modifiedWeight(active.staticWeight(), active.branch());
    }

    public boolean isActive(ActionBattleSteelRules.Branch branch, long currentTick) {
        tick(currentTick);
        return active != null && active.branch() == branch;
    }

    public void clear() {
        active = null;
        completedApplications = 0;
        inactiveSinceTick = -1L;
    }

    public boolean isEmpty(long currentTick) {
        tick(currentTick);
        return active == null && completedApplications == 0;
    }

    public int completedApplications() { return completedApplications; }

    public record Selection(ActionBattleSteelRules.Branch branch, double staticWeight) {
        public Selection { staticWeight = Double.isFinite(staticWeight) ? Math.max(0.0D, staticWeight) : 0.0D; }
    }
    private record Active(ActionBattleSteelRules.Branch branch, double staticWeight, boolean steelTyped,
                          long startTick, long endTick) {}
    public record View(ActionBattleSteelRules.Branch branch, boolean steelTyped, double staticWeight,
                       long remainingTicks, long totalTicks) {}
    public enum ApplyResult { APPLIED, IGNORED_ACTIVE, INVALID }
}
