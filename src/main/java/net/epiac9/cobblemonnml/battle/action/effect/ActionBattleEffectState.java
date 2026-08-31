package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleEffectState {
    public static final long BASE_STATUS_DURATION_TICKS = 120L;
    static final long BASE_DOT_INTERVAL_TICKS = 20L;
    private final UUID battleId;
    private final UUID pokemonUUID;
    private final Map<ContributionKey, ActionBattleStatContribution> statContributions = new HashMap<>();
    private long hazeEndTick = 0L;
    private long cindersEndTick = 0L;
    private long burnEndTick = 0L;
    private long nextBurnDotTick = 0L;

    ActionBattleEffectState(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) throw new IllegalArgumentException("Battle and Pokemon IDs cannot be null.");
        this.battleId = battleId;
        this.pokemonUUID = pokemonUUID;
    }

    boolean applyStatContribution(ActionBattleStat stat, int stages, long currentTick, long durationTicks) {
        if (stat == null || stages == 0 || stages < -6 || stages > 6 || currentTick < 0L || durationTicks <= 0L) return false;
        long endTick = safeAdd(currentTick, durationTicks);
        ContributionKey key = new ContributionKey(stat, stages);
        statContributions.put(key, new ActionBattleStatContribution(stat, stages, endTick));
        return true;
    }

    int effectiveStage(ActionBattleStat stat, long currentTick) {
        if (stat == null || currentTick < 0L) return 0;
        pruneNonDot(currentTick);
        if (hasHaze(currentTick)) return 0;
        int total = 0;
        for (ActionBattleStatContribution contribution : statContributions.values()) {
            if (contribution.stat() == stat && contribution.isActive(currentTick)) total += contribution.stages();
        }
        if (stat == ActionBattleStat.ATTACK && hasStatus(ActionBattleStatus.BURN, currentTick)) total -= 1;
        return Math.max(-6, Math.min(6, total));
    }

    boolean applyHaze(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks <= 0L) return false;
        hazeEndTick = safeAdd(currentTick, durationTicks);
        return true;
    }

    boolean hasHaze(long currentTick) {
        return currentTick >= 0L && currentTick < hazeEndTick;
    }

    ActionBattleStatusApplication applyBurnCapableHit(long currentTick) {
        if (currentTick < 0L) return null;
        pruneNonDot(currentTick);
        if (hasStatus(ActionBattleStatus.BURN, currentTick)) {
            burnEndTick = safeAdd(currentTick, BASE_STATUS_DURATION_TICKS);
            nextBurnDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.BURN_REFRESHED;
        }
        if (hasStatus(ActionBattleStatus.CINDERS, currentTick)) {
            cindersEndTick = 0L;
            burnEndTick = safeAdd(currentTick, BASE_STATUS_DURATION_TICKS);
            nextBurnDotTick = safeAdd(currentTick, BASE_DOT_INTERVAL_TICKS);
            return ActionBattleStatusApplication.BURN_APPLIED;
        }
        cindersEndTick = safeAdd(currentTick, BASE_STATUS_DURATION_TICKS);
        return ActionBattleStatusApplication.CINDERS_APPLIED;
    }

    boolean hasStatus(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return false;
        return switch (status) {
            case CINDERS -> currentTick < cindersEndTick;
            case BURN -> currentTick < burnEndTick;
        };
    }

    long statusRemainingTicks(ActionBattleStatus status, long currentTick) {
        if (status == null || currentTick < 0L) return 0L;
        long endTick = switch (status) {
            case CINDERS -> cindersEndTick;
            case BURN -> burnEndTick;
        };
        return Math.max(0L, endTick - currentTick);
    }

    ActionBattleDotEvent tick(long currentTick) {
        if (currentTick < 0L) return null;
        pruneNonDot(currentTick);
        ActionBattleDotEvent event = null;
        if (burnEndTick > 0L && nextBurnDotTick > 0L && currentTick >= nextBurnDotTick && nextBurnDotTick <= burnEndTick) {
            event = new ActionBattleDotEvent(pokemonUUID, ActionBattleStatus.BURN, 0.03D, true);
            nextBurnDotTick = safeAdd(nextBurnDotTick, BASE_DOT_INTERVAL_TICKS);
        }
        if (burnEndTick > 0L && currentTick >= burnEndTick) {
            burnEndTick = 0L;
            nextBurnDotTick = 0L;
        }
        return event;
    }

    void clearStatuses() {
        cindersEndTick = 0L;
        burnEndTick = 0L;
        nextBurnDotTick = 0L;
    }

    boolean prune(long currentTick) {
        if (currentTick < 0L) return false;
        pruneNonDot(currentTick);
        if (burnEndTick > 0L && currentTick > burnEndTick) {
            burnEndTick = 0L;
            nextBurnDotTick = 0L;
        }
        return isEmpty();
    }

    private void pruneNonDot(long currentTick) {
        statContributions.entrySet().removeIf(entry -> !entry.getValue().isActive(currentTick));
        if (hazeEndTick > 0L && currentTick >= hazeEndTick) hazeEndTick = 0L;
        if (cindersEndTick > 0L && currentTick >= cindersEndTick) cindersEndTick = 0L;
    }

    private boolean isEmpty() {
        return statContributions.isEmpty() && hazeEndTick == 0L && cindersEndTick == 0L && burnEndTick == 0L;
    }

    UUID battleId() { return battleId; }
    UUID pokemonUUID() { return pokemonUUID; }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private record ContributionKey(ActionBattleStat stat, int stages) {}
}
