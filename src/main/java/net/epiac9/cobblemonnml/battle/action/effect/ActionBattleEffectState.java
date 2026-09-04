package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleEffectState {
    private final UUID battleId;
    private final UUID pokemonUUID;
    private final Map<ContributionKey, ActionBattleStatContribution> statContributions = new HashMap<>();
    private boolean hazeProtected;
    private ActionBattleSleepState sleep;
    private ActionBattleConfusionState confusion;
    private ActionBattleEvasionState evasion;

    ActionBattleEffectState(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) throw new IllegalArgumentException("Battle and Pokemon IDs cannot be null.");
        this.battleId = battleId;
        this.pokemonUUID = pokemonUUID;
    }

    boolean applyStatContribution(ActionBattleStat stat, int stages, long currentTick, long durationTicks) {
        if (hazeProtected) return false;
        if (stat == null || stages == 0 || Math.abs(stages) > ActionBattleStatRules.maxStage(stat) || currentTick < 0L || durationTicks <= 0L) return false;
        statContributions.put(new ContributionKey(stat, stages), new ActionBattleStatContribution(stat, stages, ActionBattleTiming.safeAdd(currentTick, durationTicks)));
        return true;
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
    void setHazeProtected(boolean protectedByHaze) { hazeProtected = protectedByHaze; }
    boolean hasHaze(long currentTick) { return currentTick >= 0L && hazeProtected; }

    boolean beginSleep(long currentTick, long durationTicks) {
        if (sleep == null) sleep = new ActionBattleSleepState();
        return sleep.beginSleep(currentTick, durationTicks);
    }

    boolean wakeSleep(long currentTick) { return sleep != null && sleep.wake(currentTick); }
    ActionBattleSleepState.NaturalWakeResult tickSleepState(long currentTick) { return sleep != null ? sleep.tick(currentTick) : ActionBattleSleepState.NaturalWakeResult.NONE; }

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
            case SLEEP -> sleep != null && sleep.isSleeping(currentTick);
            case CONFUSION -> confusion != null && confusion.isActive(currentTick);
            case EVASION -> evasion != null && evasion.isActive(currentTick);
        };
    }

    long statusRemainingTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        return switch (status) {
            case SLEEP -> sleep != null ? sleep.sleepRemainingTicks(currentTick) : 0L;
            case CONFUSION -> confusion != null ? confusion.remainingTicks(currentTick) : 0L;
            case EVASION -> evasion != null ? evasion.remainingTicks(currentTick) : 0L;
        };
    }


    long statusDurationTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        return switch (status) {
            case SLEEP -> sleep != null ? sleep.sleepDurationTicks(currentTick) : 0L;
            case CONFUSION -> confusion != null ? confusion.durationTicks(currentTick) : 0L;
            case EVASION -> evasion != null ? evasion.durationTicks(currentTick) : 0L;
        };
    }

    List<ActionBattleDotEvent> tick(long currentTick) {
        if (currentTick >= 0L) pruneStatContributions(currentTick);
        return List.of();
    }

    void clearStatuses(long currentTick) {
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
        if (confusion != null) confusion.clear(currentTick);
        if (evasion != null) evasion.clear(currentTick);
    }

    boolean prune(long currentTick) {
        if (currentTick < 0L) return false;
        pruneStatContributions(currentTick);
        if (sleep != null && sleep.isEmpty(currentTick)) sleep = null;
        if (confusion != null && confusion.isEmpty(currentTick)) confusion = null;
        if (evasion != null && evasion.isEmpty(currentTick)) evasion = null;
        return isEmpty();
    }

    private void pruneStatContributions(long currentTick) {
        statContributions.entrySet().removeIf(entry -> !entry.getValue().isActive(currentTick));
    }

    private boolean isEmpty() {
        return statContributions.isEmpty() && !hazeProtected && sleep == null && confusion == null && evasion == null;
    }

    UUID battleId() { return battleId; }
    UUID pokemonUUID() { return pokemonUUID; }

    private record ContributionKey(ActionBattleStat stat, int stages) {}
}
