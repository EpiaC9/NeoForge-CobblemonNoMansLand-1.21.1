package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleEffectController {
    private static final ActionBattleEffectController GLOBAL = new ActionBattleEffectController();
    private final Map<UUID, Map<UUID, ActionBattleEffectState>> statesByBattle = new HashMap<>();

    public static ActionBattleEffectController global() { return GLOBAL; }

    public boolean applyStatContribution(UUID battleId, UUID pokemonUUID, ActionBattleStat stat, int stages, long currentTick, long durationTicks) {
        if (!validIds(battleId, pokemonUUID)) return false;
        return state(battleId, pokemonUUID).applyStatContribution(stat, stages, currentTick, durationTicks);
    }

    public int effectiveStage(UUID battleId, UUID pokemonUUID, ActionBattleStat stat, long currentTick) {
        ActionBattleEffectState state = existingState(battleId, pokemonUUID);
        if (state == null) return 0;
        int stage = state.effectiveStage(stat, currentTick);
        removeIfEmpty(state, currentTick);
        return stage;
    }

    public boolean applyHaze(UUID battleId, UUID pokemonUUID, long currentTick, long durationTicks) {
        if (!validIds(battleId, pokemonUUID)) return false;
        return state(battleId, pokemonUUID).applyHaze(currentTick, durationTicks);
    }

    public boolean hasHaze(UUID battleId, UUID pokemonUUID, long currentTick) {
        ActionBattleEffectState state = existingState(battleId, pokemonUUID);
        if (state == null) return false;
        boolean active = state.hasHaze(currentTick);
        removeIfEmpty(state, currentTick);
        return active;
    }

    public ActionBattleStatusApplication applyBurnCapableHit(UUID battleId, UUID pokemonUUID, long currentTick) {
        if (!validIds(battleId, pokemonUUID) || currentTick < 0L) return null;
        return state(battleId, pokemonUUID).applyBurnCapableHit(currentTick);
    }

    public boolean hasStatus(UUID battleId, UUID pokemonUUID, ActionBattleStatus status, long currentTick) {
        ActionBattleEffectState state = existingState(battleId, pokemonUUID);
        if (state == null) return false;
        boolean active = state.hasStatus(status, currentTick);
        removeIfEmpty(state, currentTick);
        return active;
    }

    public long statusRemainingTicks(UUID battleId, UUID pokemonUUID, ActionBattleStatus status, long currentTick) {
        ActionBattleEffectState state = existingState(battleId, pokemonUUID);
        if (state == null) return 0L;
        long remaining = state.statusRemainingTicks(status, currentTick);
        removeIfEmpty(state, currentTick);
        return remaining;
    }

    public long statusDurationTicks(ActionBattleStatus status) {
        return status == ActionBattleStatus.CINDERS || status == ActionBattleStatus.BURN
                ? ActionBattleEffectState.BASE_STATUS_DURATION_TICKS : 0L;
    }

    public void clearStatuses(UUID battleId, UUID pokemonUUID, long currentTick) {
        ActionBattleEffectState state = existingState(battleId, pokemonUUID);
        if (state == null) return;
        state.clearStatuses();
        removeIfEmpty(state, currentTick);
    }

    public List<ActionBattleDotEvent> tickBattle(UUID battleId, long currentTick) {
        if (battleId == null || currentTick < 0L) return List.of();
        Map<UUID, ActionBattleEffectState> battleStates = statesByBattle.get(battleId);
        if (battleStates == null) return List.of();
        List<ActionBattleDotEvent> events = new ArrayList<>();
        for (ActionBattleEffectState state : battleStates.values()) {
            ActionBattleDotEvent event = state.tick(currentTick);
            if (event != null) events.add(event);
        }
        battleStates.entrySet().removeIf(entry -> entry.getValue().prune(currentTick));
        if (battleStates.isEmpty()) statesByBattle.remove(battleId);
        return events;
    }

    public void clearBattle(UUID battleId) {
        if (battleId != null) statesByBattle.remove(battleId);
    }

    public int trackedPokemonCount(UUID battleId) {
        Map<UUID, ActionBattleEffectState> battleStates = battleId != null ? statesByBattle.get(battleId) : null;
        return battleStates != null ? battleStates.size() : 0;
    }

    private ActionBattleEffectState state(UUID battleId, UUID pokemonUUID) {
        return statesByBattle.computeIfAbsent(battleId, ignored -> new HashMap<>())
                .computeIfAbsent(pokemonUUID, ignored -> new ActionBattleEffectState(battleId, pokemonUUID));
    }

    private ActionBattleEffectState existingState(UUID battleId, UUID pokemonUUID) {
        if (!validIds(battleId, pokemonUUID)) return null;
        Map<UUID, ActionBattleEffectState> battleStates = statesByBattle.get(battleId);
        return battleStates != null ? battleStates.get(pokemonUUID) : null;
    }

    private void removeIfEmpty(ActionBattleEffectState state, long currentTick) {
        if (state == null || !state.prune(currentTick)) return;
        Map<UUID, ActionBattleEffectState> battleStates = statesByBattle.get(state.battleId());
        if (battleStates == null) return;
        battleStates.remove(state.pokemonUUID());
        if (battleStates.isEmpty()) statesByBattle.remove(state.battleId());
    }

    private static boolean validIds(UUID battleId, UUID pokemonUUID) {
        return battleId != null && pokemonUUID != null;
    }
}
