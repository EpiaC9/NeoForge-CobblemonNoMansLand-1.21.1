package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleEffectState {
    public static final long BASE_STATUS_DURATION_TICKS = 120L;
    static final long BASE_DOT_INTERVAL_TICKS = 20L;
    private final UUID battleId;
    private final UUID pokemonUUID;
    private final Map<ContributionKey, ActionBattleStatContribution> statContributions = new HashMap<>();
    private final ActionBattleTwoStageDotState burn = new ActionBattleTwoStageDotState(BASE_STATUS_DURATION_TICKS, BASE_DOT_INTERVAL_TICKS);
    private final ActionBattleTwoStageDotState freeze = new ActionBattleTwoStageDotState(BASE_STATUS_DURATION_TICKS, BASE_DOT_INTERVAL_TICKS);
    private boolean hazeProtected;
    private ActionBattlePoisonToxicState poisonToxic;
    private ActionBattleParalysisState paralysis;
    private ActionBattleSleepState sleep;
    private ActionBattleConfusionState confusion;

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
        pruneNonDot(currentTick);
        if (hazeProtected) return 0;
        int total = 0;
        for (ActionBattleStatContribution contribution : statContributions.values()) {
            if (contribution.stat() == stat && contribution.isActive(currentTick)) total += contribution.stages();
        }
        if (stat == ActionBattleStat.ATTACK && hasStatus(ActionBattleStatus.BURN, currentTick)) total -= 1;
        if (hasStatus(ActionBattleStatus.FROSTBITE, currentTick)) {
            if (stat == ActionBattleStat.DEFENSE) total -= 1;
            if (stat == ActionBattleStat.SPECIAL_DEFENSE) total -= 1;
        }
        if (stat == ActionBattleStat.SPECIAL_ATTACK && hasStatus(ActionBattleStatus.POISON, currentTick)) total -= 1;
        if (stat == ActionBattleStat.SPEED && hasStatus(ActionBattleStatus.PARALYSIS, currentTick)) total -= 1;
        return ActionBattleStatRules.clampStage(stat, total);
    }

    void clearTemporaryStatChanges() { statContributions.clear(); }
    void setHazeProtected(boolean protectedByHaze) { hazeProtected = protectedByHaze; }
    boolean hasHaze(long currentTick) { return currentTick >= 0L && hazeProtected; }

    ActionBattleStatusApplication applyBurnCapableHit(long currentTick) {
        return applyBurnCapableHit(currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyBurnCapableHit(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        pruneNonDot(currentTick);
        ActionBattleTwoStageDotState.ApplyResult result = burn.apply(currentTick, durationMultiplier);
        if (result == null) return null;
        return switch (result) {
            case BUILDUP_APPLIED -> ActionBattleStatusApplication.CINDERS_APPLIED;
            case DOT_APPLIED -> ActionBattleStatusApplication.BURN_APPLIED;
            case DOT_REFRESHED -> ActionBattleStatusApplication.BURN_REFRESHED;
        };
    }

    ActionBattleStatusApplication applyFreezeCapableHit(long currentTick) {
        return applyFreezeCapableHit(currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyFreezeCapableHit(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        pruneNonDot(currentTick);
        ActionBattleTwoStageDotState.ApplyResult result = freeze.apply(currentTick, durationMultiplier);
        if (result == null) return null;
        return switch (result) {
            case BUILDUP_APPLIED -> ActionBattleStatusApplication.FREEZE_APPLIED;
            case DOT_APPLIED -> ActionBattleStatusApplication.FROSTBITE_APPLIED;
            case DOT_REFRESHED -> ActionBattleStatusApplication.FROSTBITE_REFRESHED;
        };
    }

    ActionBattleStatusApplication applyPoison(int strength, long currentTick) {
        return applyPoison(strength, currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyPoison(int strength, long currentTick, float durationMultiplier) {
        if (currentTick < 0L || (strength != 1 && strength != 2) || !(durationMultiplier > 0.0F)) return null;
        if (poisonToxic == null) poisonToxic = new ActionBattlePoisonToxicState();
        return poisonToxic.apply(strength, currentTick, durationMultiplier);
    }


    ActionBattleStatusApplication applyParalysis(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        if (paralysis == null) paralysis = new ActionBattleParalysisState();
        ActionBattleParalysisState.ApplyResult result = paralysis.apply(currentTick, durationMultiplier);
        if (result == null) return null;
        return result == ActionBattleParalysisState.ApplyResult.APPLIED
                ? ActionBattleStatusApplication.PARALYSIS_APPLIED
                : ActionBattleStatusApplication.PARALYSIS_REFRESHED;
    }


    ActionBattleStatusApplication applyDrowsiness(long currentTick) {
        if (currentTick < 0L) return null;
        if (sleep == null) sleep = new ActionBattleSleepState();
        return switch (sleep.applyDrowsiness(currentTick)) {
            case APPLIED -> ActionBattleStatusApplication.DROWSINESS_APPLIED;
            case SHORTENED, READY_FOR_SLEEP -> ActionBattleStatusApplication.DROWSINESS_SHORTENED;
            case BLOCKED_BY_SLEEP, BLOCKED_BY_GRACE -> ActionBattleStatusApplication.DROWSINESS_BLOCKED;
            case REJECTED -> null;
        };
    }

    boolean shouldBeginSleep(long currentTick) { return sleep != null && sleep.shouldBeginSleep(currentTick); }
    boolean beginSleep(long currentTick, long durationTicks) {
        if (sleep == null) sleep = new ActionBattleSleepState();
        return sleep.beginSleep(currentTick, durationTicks);
    }
    boolean wakeSleep(long currentTick) { return sleep != null && sleep.wake(currentTick); }
    ActionBattleSleepState.NaturalWakeResult tickSleepState(long currentTick) { return sleep != null ? sleep.tick(currentTick) : ActionBattleSleepState.NaturalWakeResult.NONE; }

    ActionBattleStatusApplication applyConfusion(long currentTick) {
        if (currentTick < 0L) return null;
        if (confusion == null) confusion = new ActionBattleConfusionState();
        ActionBattleConfusionState.ApplyResult result = confusion.apply(currentTick);
        return result == ActionBattleConfusionState.ApplyResult.APPLIED ? ActionBattleStatusApplication.CONFUSION_APPLIED : ActionBattleStatusApplication.CONFUSION_REFRESHED;
    }

    float paralysisCheckChance(long currentTick) {
        return paralysis != null && paralysis.isActive(currentTick) ? paralysis.checkChance() : 0.0F;
    }

    float advanceParalysisChecks(int count, long currentTick) {
        if (paralysis == null || !paralysis.isActive(currentTick) || count <= 0) return 0.0F;
        paralysis.advanceChecks(count);
        return paralysis.checkChance();
    }

    void resetParalysisBuildup(long currentTick) {
        if (paralysis != null && paralysis.isActive(currentTick)) paralysis.resetBuildup();
    }

    boolean hasStatus(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return false;
        return switch (status) {
            case CINDERS -> burn.hasBuildup(currentTick);
            case BURN -> burn.hasDot(currentTick);
            case FREEZE -> freeze.hasBuildup(currentTick);
            case FROSTBITE -> freeze.hasDot(currentTick);
            case POISON, TOXIC_1, TOXIC_2, TOXIC_3 -> poisonStatusMatches(status, currentTick);
            case PARALYSIS -> paralysis != null && paralysis.isActive(currentTick);
            case DROWSINESS -> sleep != null && sleep.isDrowsy(currentTick);
            case SLEEP -> sleep != null && sleep.isSleeping(currentTick);
            case DROWSINESS_GRACE -> sleep != null && sleep.hasDrowsinessGrace(currentTick);
            case CONFUSION -> confusion != null && confusion.isActive(currentTick);
        };
    }

    long statusRemainingTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        return switch (status) {
            case CINDERS -> burn.buildupRemainingTicks(currentTick);
            case BURN -> burn.dotRemainingTicks(currentTick);
            case FREEZE -> freeze.buildupRemainingTicks(currentTick);
            case FROSTBITE -> freeze.dotRemainingTicks(currentTick);
            case POISON, TOXIC_1, TOXIC_2, TOXIC_3 -> poisonStatusMatches(status, currentTick) && poisonToxic != null ? poisonToxic.remainingTicks(currentTick) : 0L;
            case PARALYSIS -> paralysis != null ? paralysis.remainingTicks(currentTick) : 0L;
            case DROWSINESS -> sleep != null ? sleep.drowsinessRemainingTicks(currentTick) : 0L;
            case SLEEP -> sleep != null ? sleep.sleepRemainingTicks(currentTick) : 0L;
            case DROWSINESS_GRACE -> sleep != null ? sleep.graceRemainingTicks(currentTick) : 0L;
            case CONFUSION -> confusion != null ? confusion.remainingTicks(currentTick) : 0L;
        };
    }

    long poisonToxicRemainingTicks(long currentTick) {
        return poisonToxic != null ? poisonToxic.remainingTicks(currentTick) : 0L;
    }

    int poisonToxicReapplicationCount(long currentTick) {
        if (poisonToxic == null || poisonToxic.isExpired(currentTick)) return 0;
        return poisonToxic.reapplicationCount();
    }

    long poisonToxicNextDotTick(long currentTick) {
        if (poisonToxic == null || poisonToxic.isExpired(currentTick)) return 0L;
        return poisonToxic.nextDotTick();
    }

    List<ActionBattleDotEvent> tick(long currentTick) {
        if (currentTick < 0L) return List.of();
        pruneNonDot(currentTick);
        List<ActionBattleDotEvent> events = new ArrayList<>(3);
        if (burn.pollDot(currentTick)) events.add(new ActionBattleDotEvent(pokemonUUID, ActionBattleStatus.BURN, 0.03D, true));
        if (freeze.pollDot(currentTick)) events.add(new ActionBattleDotEvent(pokemonUUID, ActionBattleStatus.FROSTBITE, 0.03D, true));
        if (poisonToxic != null) {
            ActionBattlePoisonToxicState.Stage stageBefore = poisonToxic.stage();
            ActionBattlePoisonToxicState.DotTick dot = poisonToxic.pollDot(currentTick);
            if (dot != null && stageBefore != null) {
                events.add(new ActionBattleDotEvent(pokemonUUID, statusForPoisonStage(stageBefore), dot.currentHpFraction(), dot.canKo()));
            }
            if (poisonToxic.isExpired(currentTick)) poisonToxic = null;
        }
        burn.prune(currentTick);
        freeze.prune(currentTick);
        return events;
    }

    void clearStatuses(long currentTick) {
        burn.clear();
        freeze.clear();
        if (poisonToxic != null) poisonToxic.clear();
        poisonToxic = null;
        if (paralysis != null) paralysis.clear();
        paralysis = null;
        if (sleep != null) {
            sleep.cleanse(currentTick);
            if (sleep.isEmpty(currentTick)) sleep = null;
        }
        if (confusion != null) confusion.clear();
        confusion = null;
    }

    void onPokemonRecalled(long currentTick) {
        burn.clear();
        freeze.clear();
        hazeProtected = false;
        statContributions.clear();
        if (paralysis != null) paralysis.clear();
        paralysis = null;
        if (sleep != null) sleep.clearAll();
        sleep = null;
        if (confusion != null) confusion.clear();
        confusion = null;
        if (poisonToxic != null) {
            if (poisonToxic.isExpired(currentTick)) poisonToxic = null;
            else poisonToxic.collapseToPoisonOnRecall(currentTick);
        }
    }

    boolean prune(long currentTick) {
        if (currentTick < 0L) return false;
        pruneNonDot(currentTick);
        burn.prune(currentTick);
        freeze.prune(currentTick);
        if (poisonToxic != null && poisonToxic.isExpired(currentTick)) poisonToxic = null;
        if (paralysis != null && paralysis.isEmpty(currentTick)) paralysis = null;
        if (sleep != null && sleep.isEmpty(currentTick)) sleep = null;
        if (confusion != null && confusion.isEmpty(currentTick)) confusion = null;
        return isEmpty();
    }

    private void pruneNonDot(long currentTick) {
        statContributions.entrySet().removeIf(entry -> !entry.getValue().isActive(currentTick));
        burn.pruneBuildup(currentTick);
        freeze.pruneBuildup(currentTick);
    }

    private boolean poisonStatusMatches(ActionBattleStatus status, long currentTick) {
        if (poisonToxic == null || poisonToxic.isExpired(currentTick)) return false;
        return statusForPoisonStage(poisonToxic.stage()) == status;
    }

    private static ActionBattleStatus statusForPoisonStage(ActionBattlePoisonToxicState.Stage stage) {
        return switch (stage) {
            case POISON -> ActionBattleStatus.POISON;
            case TOXIC_1 -> ActionBattleStatus.TOXIC_1;
            case TOXIC_2 -> ActionBattleStatus.TOXIC_2;
            case TOXIC_3 -> ActionBattleStatus.TOXIC_3;
        };
    }

    private boolean isEmpty() {
        return statContributions.isEmpty() && !hazeProtected && burn.isEmpty() && freeze.isEmpty() && poisonToxic == null && paralysis == null && sleep == null && confusion == null;
    }

    UUID battleId() { return battleId; }
    UUID pokemonUUID() { return pokemonUUID; }

    private record ContributionKey(ActionBattleStat stat, int stages) {}
}
