package net.epiac9.cobblemonnml.battle.action.effect;

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
    private boolean hazeProtected;
    private long cindersEndTick;
    private long burnEndTick;
    private long nextBurnDotTick;
    private long freezeEndTick;
    private long frostbiteEndTick;
    private long nextFrostbiteDotTick;
    private ActionBattlePoisonToxicState poisonToxic;

    ActionBattleEffectState(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) throw new IllegalArgumentException("Battle and Pokemon IDs cannot be null.");
        this.battleId = battleId;
        this.pokemonUUID = pokemonUUID;
    }

    boolean applyStatContribution(ActionBattleStat stat, int stages, long currentTick, long durationTicks) {
        if (hazeProtected) return false;
        if (stat == null || stages == 0 || stages < -6 || stages > 6 || currentTick < 0L || durationTicks <= 0L) return false;
        statContributions.put(new ContributionKey(stat, stages), new ActionBattleStatContribution(stat, stages, safeAdd(currentTick, durationTicks)));
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
        return Math.max(-6, Math.min(6, total));
    }

    void clearTemporaryStatChanges() {
        statContributions.clear();
    }

    void setHazeProtected(boolean protectedByHaze) {
        hazeProtected = protectedByHaze;
    }

    boolean hasHaze(long currentTick) {
        return currentTick >= 0L && hazeProtected;
    }

    ActionBattleStatusApplication applyBurnCapableHit(long currentTick) {
        return applyBurnCapableHit(currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyBurnCapableHit(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        pruneNonDot(currentTick);
        long scaledDuration = scaledTicks(BASE_STATUS_DURATION_TICKS, durationMultiplier);
        if (hasStatus(ActionBattleStatus.BURN, currentTick)) {
            burnEndTick = Math.max(burnEndTick, safeAdd(currentTick, scaledDuration));
            nextBurnDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.BURN_REFRESHED;
        }
        if (hasStatus(ActionBattleStatus.CINDERS, currentTick)) {
            cindersEndTick = 0L;
            burnEndTick = safeAdd(currentTick, scaledDuration);
            nextBurnDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.BURN_APPLIED;
        }
        cindersEndTick = safeAdd(currentTick, scaledDuration);
        return ActionBattleStatusApplication.CINDERS_APPLIED;
    }

    ActionBattleStatusApplication applyFreezeCapableHit(long currentTick) {
        return applyFreezeCapableHit(currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyFreezeCapableHit(long currentTick, float durationMultiplier) {
        if (currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        pruneNonDot(currentTick);
        long scaledDuration = scaledTicks(BASE_STATUS_DURATION_TICKS, durationMultiplier);
        if (hasStatus(ActionBattleStatus.FROSTBITE, currentTick)) {
            frostbiteEndTick = Math.max(frostbiteEndTick, safeAdd(currentTick, scaledDuration));
            nextFrostbiteDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.FROSTBITE_REFRESHED;
        }
        if (hasStatus(ActionBattleStatus.FREEZE, currentTick)) {
            freezeEndTick = 0L;
            frostbiteEndTick = safeAdd(currentTick, scaledDuration);
            nextFrostbiteDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.FROSTBITE_APPLIED;
        }
        freezeEndTick = safeAdd(currentTick, scaledDuration);
        return ActionBattleStatusApplication.FREEZE_APPLIED;
    }

    ActionBattleStatusApplication applyPoison(int strength, long currentTick) {
        return applyPoison(strength, currentTick, 1.0F);
    }

    ActionBattleStatusApplication applyPoison(int strength, long currentTick, float durationMultiplier) {
        if (currentTick < 0L || (strength != 1 && strength != 2) || !(durationMultiplier > 0.0F)) return null;
        if (poisonToxic == null) poisonToxic = new ActionBattlePoisonToxicState();
        return poisonToxic.apply(strength, currentTick, durationMultiplier);
    }

    boolean hasStatus(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return false;
        return switch (status) {
            case CINDERS -> currentTick < cindersEndTick;
            case BURN -> currentTick < burnEndTick;
            case FREEZE -> currentTick < freezeEndTick;
            case FROSTBITE -> currentTick < frostbiteEndTick;
            case POISON, TOXIC_1, TOXIC_2, TOXIC_3 -> poisonStatusMatches(status, currentTick);
        };
    }

    long statusRemainingTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        if (status == ActionBattleStatus.POISON || status == ActionBattleStatus.TOXIC_1 || status == ActionBattleStatus.TOXIC_2 || status == ActionBattleStatus.TOXIC_3) {
            return poisonStatusMatches(status, currentTick) && poisonToxic != null ? poisonToxic.remainingTicks(currentTick) : 0L;
        }
        long endTick = switch (status) {
            case CINDERS -> cindersEndTick;
            case BURN -> burnEndTick;
            case FREEZE -> freezeEndTick;
            case FROSTBITE -> frostbiteEndTick;
            default -> 0L;
        };
        return Math.max(0L, endTick - currentTick);
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
        if (burnEndTick > 0L && nextBurnDotTick > 0L && currentTick >= nextBurnDotTick && nextBurnDotTick <= burnEndTick) {
            events.add(new ActionBattleDotEvent(pokemonUUID, ActionBattleStatus.BURN, 0.03D, true));
            nextBurnDotTick = safeAdd(nextBurnDotTick, BASE_DOT_INTERVAL_TICKS);
        }
        if (frostbiteEndTick > 0L && nextFrostbiteDotTick > 0L && currentTick >= nextFrostbiteDotTick && nextFrostbiteDotTick <= frostbiteEndTick) {
            events.add(new ActionBattleDotEvent(pokemonUUID, ActionBattleStatus.FROSTBITE, 0.03D, true));
            nextFrostbiteDotTick = safeAdd(nextFrostbiteDotTick, BASE_DOT_INTERVAL_TICKS);
        }
        if (poisonToxic != null) {
            ActionBattlePoisonToxicState.Stage stageBefore = poisonToxic.stage();
            ActionBattlePoisonToxicState.DotTick dot = poisonToxic.pollDot(currentTick);
            if (dot != null && stageBefore != null) {
                events.add(new ActionBattleDotEvent(pokemonUUID, statusForPoisonStage(stageBefore), dot.currentHpFraction(), dot.canKo()));
            }
            if (poisonToxic.isExpired(currentTick)) poisonToxic = null;
        }
        if (burnEndTick > 0L && currentTick >= burnEndTick) {
            burnEndTick = 0L;
            nextBurnDotTick = 0L;
        }
        if (frostbiteEndTick > 0L && currentTick >= frostbiteEndTick) {
            frostbiteEndTick = 0L;
            nextFrostbiteDotTick = 0L;
        }
        return events;
    }

    void clearStatuses() {
        cindersEndTick = 0L;
        burnEndTick = 0L;
        nextBurnDotTick = 0L;
        freezeEndTick = 0L;
        frostbiteEndTick = 0L;
        nextFrostbiteDotTick = 0L;
        if (poisonToxic != null) poisonToxic.clear();
        poisonToxic = null;
    }

    void onPokemonRecalled(long currentTick) {
        cindersEndTick = 0L;
        burnEndTick = 0L;
        nextBurnDotTick = 0L;
        freezeEndTick = 0L;
        frostbiteEndTick = 0L;
        nextFrostbiteDotTick = 0L;
        hazeProtected = false;
        statContributions.clear();
        if (poisonToxic != null) {
            if (poisonToxic.isExpired(currentTick)) poisonToxic = null;
            else poisonToxic.collapseToPoisonOnRecall(currentTick);
        }
    }

    boolean prune(long currentTick) {
        if (currentTick < 0L) return false;
        pruneNonDot(currentTick);
        if (burnEndTick > 0L && currentTick > burnEndTick) {
            burnEndTick = 0L;
            nextBurnDotTick = 0L;
        }
        if (frostbiteEndTick > 0L && currentTick > frostbiteEndTick) {
            frostbiteEndTick = 0L;
            nextFrostbiteDotTick = 0L;
        }
        if (poisonToxic != null && poisonToxic.isExpired(currentTick)) poisonToxic = null;
        return isEmpty();
    }

    private void pruneNonDot(long currentTick) {
        statContributions.entrySet().removeIf(entry -> !entry.getValue().isActive(currentTick));
        if (cindersEndTick > 0L && currentTick >= cindersEndTick) cindersEndTick = 0L;
        if (freezeEndTick > 0L && currentTick >= freezeEndTick) freezeEndTick = 0L;
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
        return statContributions.isEmpty() && !hazeProtected && cindersEndTick == 0L && burnEndTick == 0L && freezeEndTick == 0L && frostbiteEndTick == 0L && poisonToxic == null;
    }

    UUID battleId() { return battleId; }
    UUID pokemonUUID() { return pokemonUUID; }

    private static long scaledTicks(long ticks, float multiplier) {
        return Math.max(1L, Math.round(ticks * multiplier));
    }

    private static long safeAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private record ContributionKey(ActionBattleStat stat, int stages) {}
}
