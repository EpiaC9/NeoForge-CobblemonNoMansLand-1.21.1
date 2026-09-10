package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyState;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyTracker;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleSleepState;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleTimedStatusState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleEffectState {
    private final UUID battleId;
    private final UUID pokemonUUID;
    private final Map<UUID, ActionBattleStatContribution> statContributions = new HashMap<>();
    private final Map<ActionBattleStatus, ActionBattleTimedStatusState> timedStatuses =
            new EnumMap<>(ActionBattleStatus.class);
    private boolean hazeProtected;
    private ActionBattleSleepState sleep;
    private ActionBattleDrowsyTracker drowsy;
    private ActionBattleConfusionState confusion;
    private ActionBattleEvasionState evasion;

    ActionBattleEffectState(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) throw new IllegalArgumentException("Battle and Pokemon IDs cannot be null.");
        this.battleId = battleId;
        this.pokemonUUID = pokemonUUID;
    }

    int applyBoundedStatContribution(ActionBattleStat stat, int stages, long currentTick,
                                     long durationTicks, ActionBattleStatSource source) {
        if (hazeProtected || stat == null || stages == 0 || currentTick < 0L
                || durationTicks <= 0L || source == null) return 0;
        int current = effectiveStage(stat, currentTick);
        int accepted = ActionBattleStatRules.clampStage(stat, current + stages) - current;
        if (accepted == 0) return 0;
        UUID id = UUID.randomUUID();
        statContributions.put(id, new ActionBattleStatContribution(id, stat, accepted, currentTick,
                ActionBattleTiming.safeAdd(currentTick, durationTicks), source));
        return accepted;
    }

    int effectiveStage(ActionBattleStat stat, long currentTick) {
        if (stat == null || currentTick < 0L) return 0;
        pruneStatContributions(currentTick);
        if (hazeProtected) return 0;
        int total = 0;
        for (ActionBattleStatContribution contribution : statContributions.values()) {
            if (contribution.stat() == stat && contribution.isActive(currentTick)) total += contribution.stages();
        }
        return ActionBattleStatRules.clampStage(stat, total);
    }

    void clearTemporaryStatChanges() { statContributions.clear(); }
    void clearStatContributionsFromSource(ActionBattleStatSource source) {
        if (source != null) statContributions.entrySet().removeIf(entry -> entry.getValue().source() == source);
    }
    void setHazeProtected(boolean protectedByHaze) { hazeProtected = protectedByHaze; }
    boolean hasHaze(long currentTick) { return currentTick >= 0L && hazeProtected; }

    boolean beginSleep(long currentTick, long durationTicks) {
        if (sleep == null) sleep = new ActionBattleSleepState();
        return sleep.beginSleep(currentTick, durationTicks);
    }

    boolean wakeSleep(long currentTick) { return sleep != null && sleep.wake(currentTick); }
    ActionBattleSleepState.NaturalWakeResult tickSleepState(long currentTick) { return sleep != null ? sleep.tick(currentTick) : ActionBattleSleepState.NaturalWakeResult.NONE; }

    boolean applyDrowsy(long currentTick, ActionBattleDrowsyTracker.CompletionRoute route) {
        if (drowsy == null) drowsy = new ActionBattleDrowsyTracker();
        boolean applied = drowsy.apply(currentTick, route) == ActionBattleDrowsyTracker.ApplyResult.APPLIED;
        if (drowsy.isEmpty()) drowsy = null;
        return applied;
    }

    boolean completeDrowsy(long currentTick, int completionDurationTicks,
                            ActionBattleDrowsyTracker.CompletionRoute route) {
        return drowsy != null && drowsy.completeNaturally(currentTick, completionDurationTicks, route);
    }

    java.util.Optional<ActionBattleDrowsyState> drowsyState() {
        return drowsy != null ? drowsy.activeDrowsy() : java.util.Optional.empty();
    }

    java.util.Optional<ActionBattleDrowsyTracker.CompletionState> drowsyCompletion() {
        return drowsy != null ? drowsy.completion() : java.util.Optional.empty();
    }

    int nextDrowsyDurationTicks() {
        return drowsy != null ? drowsy.nextDrowsyDurationTicks()
                : net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyRules.BASE_DURATION_TICKS;
    }

    ActionBattleDrowsyTracker.CompletionRoute pendingDrowsyCompletionRoute() {
        return drowsy != null ? drowsy.pendingCompletionRoute() : ActionBattleDrowsyTracker.CompletionRoute.SLEEP;
    }

    boolean cancelDrowsyOnRecall(long currentTick) {
        return drowsy != null && drowsy.cancelOnRecall(currentTick);
    }

    ActionBattleStatusApplication applyStatus(ActionBattleStatus status, long currentTick, long durationTicks) {
        if (status == null || !status.directTimedStatus() || currentTick < 0L || durationTicks <= 0L) {
            return ActionBattleStatusApplication.REJECTED_INVALID;
        }
        ActionBattleTimedStatusState active = timedStatuses.get(status);
        if (active != null && active.active(currentTick)) return ActionBattleStatusApplication.IGNORED_ACTIVE;
        timedStatuses.put(status, new ActionBattleTimedStatusState(currentTick, durationTicks));
        return ActionBattleStatusApplication.APPLIED;
    }

    ActionBattleStatusApplication applyEvasion(long currentTick) {
        if (currentTick < 0L) return null;
        if (evasion == null) evasion = new ActionBattleEvasionState();
        ActionBattleEvasionState.ApplyResult result = evasion.apply(currentTick);
        return result == ActionBattleEvasionState.ApplyResult.APPLIED ? ActionBattleStatusApplication.EVASION_APPLIED : ActionBattleStatusApplication.EVASION_IGNORED_ACTIVE;
    }

    ActionBattleStatusApplication applyConfusion(long currentTick) {
        if (currentTick < 0L) return null;
        if (confusion == null) confusion = new ActionBattleConfusionState();
        ActionBattleConfusionState.ApplyResult result = confusion.apply(currentTick);
        return result == ActionBattleConfusionState.ApplyResult.APPLIED ? ActionBattleStatusApplication.CONFUSION_APPLIED : ActionBattleStatusApplication.CONFUSION_IGNORED_ACTIVE;
    }

    boolean hasStatus(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return false;
        return switch (status) {
            case BURN, FREEZE, POISON, TOXIC, PARALYSIS -> {
                ActionBattleTimedStatusState state = timedStatuses.get(status);
                yield state != null && state.active(currentTick);
            }
            case SLEEP -> sleep != null && sleep.isSleeping(currentTick);
            case CONFUSION -> confusion != null && confusion.isActive(currentTick);
            case EVASION -> evasion != null && evasion.isActive(currentTick);
        };
    }

    long statusRemainingTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        return switch (status) {
            case BURN, FREEZE, POISON, TOXIC, PARALYSIS -> {
                ActionBattleTimedStatusState state = timedStatuses.get(status);
                yield state != null ? state.remainingTicks(currentTick) : 0L;
            }
            case SLEEP -> sleep != null ? sleep.sleepRemainingTicks(currentTick) : 0L;
            case CONFUSION -> confusion != null ? confusion.remainingTicks(currentTick) : 0L;
            case EVASION -> evasion != null ? evasion.remainingTicks(currentTick) : 0L;
        };
    }


    long statusDurationTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        return switch (status) {
            case BURN, FREEZE, POISON, TOXIC, PARALYSIS -> {
                ActionBattleTimedStatusState state = timedStatuses.get(status);
                yield state != null && state.active(currentTick) ? state.durationTicks() : 0L;
            }
            case SLEEP -> sleep != null ? sleep.sleepDurationTicks(currentTick) : 0L;
            case CONFUSION -> confusion != null ? confusion.durationTicks(currentTick) : 0L;
            case EVASION -> evasion != null ? evasion.durationTicks(currentTick) : 0L;
        };
    }

    void tick(long currentTick) {
        if (currentTick < 0L) return;
        pruneStatContributions(currentTick);
        if (drowsy != null) drowsy.tick(currentTick, sleep != null && sleep.isSleeping(currentTick));
    }

    void clearStatuses(long currentTick) {
        timedStatuses.clear();
        if (sleep != null) {
            sleep.cleanse(currentTick);
            if (sleep.isEmpty(currentTick)) sleep = null;
        }
        if (confusion != null) confusion.clear(currentTick);
        if (evasion != null) evasion.clear(currentTick);
    }

    void onPokemonRecalled(long currentTick) {
        hazeProtected = false;
        statContributions.clear();
        if (drowsy != null) drowsy.cancelOnRecall(currentTick);
        if (confusion != null) confusion.clear(currentTick);
        if (evasion != null) evasion.clear(currentTick);
    }

    boolean prune(long currentTick) {
        if (currentTick < 0L) return false;
        pruneStatContributions(currentTick);
        timedStatuses.entrySet().removeIf(entry -> !entry.getValue().active(currentTick));
        if (sleep != null && sleep.isEmpty(currentTick)) sleep = null;
        if (drowsy != null && drowsy.isEmpty()) drowsy = null;
        if (confusion != null && confusion.isEmpty(currentTick)) confusion = null;
        if (evasion != null && evasion.isEmpty(currentTick)) evasion = null;
        return isEmpty();
    }

    private void pruneStatContributions(long currentTick) {
        statContributions.entrySet().removeIf(entry -> !entry.getValue().isActive(currentTick));
    }

    private boolean isEmpty() {
        return statContributions.isEmpty() && timedStatuses.isEmpty() && !hazeProtected
                && sleep == null && drowsy == null && confusion == null && evasion == null;
    }

    UUID battleId() { return battleId; }
    UUID pokemonUUID() { return pokemonUUID; }
}
