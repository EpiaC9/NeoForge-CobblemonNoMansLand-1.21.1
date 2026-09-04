package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleDrowsyTracker {
    private ActionBattleDrowsyState activeDrowsy;
    private CompletionState completion;
    private int nextDrowsyDurationTicks = ActionBattleFairyRules.BASE_DROWSY_DURATION_TICKS;
    private long cleanResetEndTick = -1L;
    private CompletionRoute pendingCompletionRoute = CompletionRoute.SLEEP;

    public ApplyResult apply(long currentTick) {
        return apply(currentTick, CompletionRoute.SLEEP);
    }

    public ApplyResult apply(long currentTick, CompletionRoute route) {
        if (currentTick < 0L) return ApplyResult.INVALID;
        if (route == null) return ApplyResult.INVALID;
        if (activeDrowsy != null) return ApplyResult.IGNORED_ACTIVE;
        if (completion != null) return ApplyResult.IGNORED_COMPLETION;
        activeDrowsy = new ActionBattleDrowsyState(currentTick, nextDrowsyDurationTicks);
        pendingCompletionRoute = route;
        completion = null;
        cleanResetEndTick = -1L;
        return ApplyResult.APPLIED;
    }

    public boolean completeNaturally(long currentTick, int completionDurationTicks, CompletionRoute route) {
        if (activeDrowsy == null || currentTick < activeDrowsy.endTick()
                || completionDurationTicks <= 0 || route == null) return false;
        activeDrowsy = null;
        nextDrowsyDurationTicks = increaseWithoutOverflow(nextDrowsyDurationTicks);
        completion = new CompletionState(route, currentTick,
                ActionBattleTiming.safeAdd(currentTick, completionDurationTicks), false);
        cleanResetEndTick = -1L;
        return true;
    }

    public boolean cancelOnRecall(long currentTick) {
        if (currentTick < 0L || activeDrowsy == null) return false;
        activeDrowsy = null;
        if (completion == null) cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick,
                ActionBattleFairyRules.CLEAN_RESET_DURATION_TICKS);
        return true;
    }

    public boolean tick(long currentTick, boolean sleepCompletionActive) {
        if (currentTick < 0L) return false;
        boolean changed = false;
        if (completion != null && completionHasEnded(currentTick, sleepCompletionActive)) {
            completion = null;
            cleanResetEndTick = ActionBattleTiming.safeAdd(currentTick,
                    ActionBattleFairyRules.CLEAN_RESET_DURATION_TICKS);
            changed = true;
        }
        if (activeDrowsy == null && completion == null && cleanResetEndTick >= 0L
                && currentTick >= cleanResetEndTick) {
            nextDrowsyDurationTicks = ActionBattleFairyRules.BASE_DROWSY_DURATION_TICKS;
            cleanResetEndTick = -1L;
            changed = true;
        }
        return changed;
    }

    public void suppressFairySpecialDefenseByHaze() {
        if (completion == null || completion.route() != CompletionRoute.FAIRY_SPDEF
                || completion.fairyStatSuppressedByHaze()) return;
        completion = new CompletionState(completion.route(), completion.startTick(), completion.endTick(), true);
    }

    public Optional<ActionBattleDrowsyState> activeDrowsy() { return Optional.ofNullable(activeDrowsy); }
    public Optional<CompletionState> completion() { return Optional.ofNullable(completion); }
    public int nextDrowsyDurationTicks() { return nextDrowsyDurationTicks; }
    public long cleanResetEndTick() { return cleanResetEndTick; }
    public CompletionRoute pendingCompletionRoute() { return pendingCompletionRoute; }

    public int ownedSpecialDefenseStages(long currentTick) {
        if (completion == null || completion.route() != CompletionRoute.FAIRY_SPDEF
                || completion.fairyStatSuppressedByHaze() || currentTick < completion.startTick()
                || currentTick >= completion.endTick()) return 0;
        return ActionBattleFairyRules.FAIRY_COMPLETION_SPDEF_STAGE;
    }

    public boolean isEmpty() {
        return activeDrowsy == null && completion == null
                && nextDrowsyDurationTicks == ActionBattleFairyRules.BASE_DROWSY_DURATION_TICKS
                && cleanResetEndTick < 0L;
    }

    private boolean completionHasEnded(long currentTick, boolean sleepCompletionActive) {
        if (completion.route() == CompletionRoute.SLEEP) return !sleepCompletionActive;
        return currentTick >= completion.endTick();
    }

    private static int increaseWithoutOverflow(int durationTicks) {
        int increment = ActionBattleFairyRules.DROWSY_DURATION_INCREMENT_TICKS;
        return durationTicks > Integer.MAX_VALUE - increment ? Integer.MAX_VALUE : durationTicks + increment;
    }

    public enum ApplyResult { APPLIED, IGNORED_ACTIVE, IGNORED_COMPLETION, INVALID }
    public enum CompletionRoute { DRAGON_UPROAR, FAIRY_SPDEF, SLEEP }

    public record CompletionState(
            CompletionRoute route,
            long startTick,
            long endTick,
            boolean fairyStatSuppressedByHaze
    ) {}
}
