package net.epiac9.cobblemonnml.battle.action.effect.status;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleDrowsyTracker {
    private ActionBattleDrowsyState activeDrowsy;
    private CompletionState completion;
    private int nextDurationTicks = ActionBattleDrowsyRules.BASE_DURATION_TICKS;
    private long cleanResetEndTick = -1L;
    private CompletionRoute pendingCompletionRoute = CompletionRoute.SLEEP;

    public ApplyResult apply(long currentTick) {
        return apply(currentTick, CompletionRoute.SLEEP);
    }

    public ApplyResult apply(long currentTick, CompletionRoute route) {
        if (currentTick < 0L || route == null) return ApplyResult.INVALID;
        if (activeDrowsy != null) return ApplyResult.IGNORED_ACTIVE;
        if (completion != null) return ApplyResult.IGNORED_COMPLETION;
        activeDrowsy = new ActionBattleDrowsyState(currentTick, nextDurationTicks);
        pendingCompletionRoute = route;
        cleanResetEndTick = -1L;
        return ApplyResult.APPLIED;
    }

    public boolean completeNaturally(long currentTick, int completionDurationTicks, CompletionRoute route) {
        if (activeDrowsy == null || currentTick < activeDrowsy.endTick()
                || completionDurationTicks <= 0 || route == null) return false;
        activeDrowsy = null;
        nextDurationTicks = increaseWithoutOverflow(nextDurationTicks);
        long completionEndTick = route == CompletionRoute.SLEEP
                ? ActionBattleTiming.safeAdd(currentTick, completionDurationTicks) : currentTick;
        completion = new CompletionState(route, currentTick, completionEndTick);
        cleanResetEndTick = -1L;
        return true;
    }

    public boolean cancelOnRecall(long currentTick) {
        if (currentTick < 0L || activeDrowsy == null) return false;
        activeDrowsy = null;
        if (completion == null) {
            cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick,
                    ActionBattleDrowsyRules.CLEAN_RESET_DURATION_TICKS);
        }
        return true;
    }

    public void clearOnUnavailable() {
        activeDrowsy = null;
        completion = null;
        nextDurationTicks = ActionBattleDrowsyRules.BASE_DURATION_TICKS;
        cleanResetEndTick = -1L;
        pendingCompletionRoute = CompletionRoute.SLEEP;
    }

    public boolean tick(long currentTick, boolean sleepCompletionActive) {
        if (currentTick < 0L) return false;
        boolean changed = false;
        if (completion != null && completionHasEnded(currentTick, sleepCompletionActive)) {
            completion = null;
            cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick,
                    ActionBattleDrowsyRules.CLEAN_RESET_DURATION_TICKS);
            changed = true;
        }
        if (activeDrowsy == null && completion == null && cleanResetEndTick >= 0L
                && currentTick >= cleanResetEndTick) {
            nextDurationTicks = ActionBattleDrowsyRules.BASE_DURATION_TICKS;
            cleanResetEndTick = -1L;
            changed = true;
        }
        return changed;
    }

    public Optional<ActionBattleDrowsyState> activeDrowsy() { return Optional.ofNullable(activeDrowsy); }
    public Optional<CompletionState> completion() { return Optional.ofNullable(completion); }
    public int nextDrowsyDurationTicks() { return nextDurationTicks; }
    public long cleanResetEndTick() { return cleanResetEndTick; }
    public CompletionRoute pendingCompletionRoute() { return pendingCompletionRoute; }

    public boolean isEmpty() {
        return activeDrowsy == null && completion == null
                && nextDurationTicks == ActionBattleDrowsyRules.BASE_DURATION_TICKS
                && cleanResetEndTick < 0L;
    }

    private boolean completionHasEnded(long currentTick, boolean sleepCompletionActive) {
        return completion.route() == CompletionRoute.SLEEP
                ? !sleepCompletionActive : currentTick >= completion.endTick();
    }

    private static int increaseWithoutOverflow(int durationTicks) {
        int increment = ActionBattleDrowsyRules.DURATION_INCREMENT_TICKS;
        return durationTicks > Integer.MAX_VALUE - increment ? Integer.MAX_VALUE : durationTicks + increment;
    }

    public enum ApplyResult { APPLIED, IGNORED_ACTIVE, IGNORED_COMPLETION, INVALID }
    public enum CompletionRoute { DRAGON_UPROAR, SLEEP }
    public record CompletionState(CompletionRoute route, long startTick, long endTick) {}
}
