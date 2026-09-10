package net.epiac9.cobblemonnml.battle.action.persistent;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ActionBattlePersistentController {
    private static final ActionBattlePersistentController GLOBAL = new ActionBattlePersistentController();
    private final Map<UUID, Map<UUID, ActionBattlePersistentState>> statesByBattle = new HashMap<>();

    private ActionBattlePersistentController() {}
    public static ActionBattlePersistentController global() { return GLOBAL; }

    public boolean applyPerishSong(UUID battleId, UUID targetPokemonUUID, UUID sourcePokemonUUID, long currentTick) {
        ActionBattlePersistentState state = state(battleId, targetPokemonUUID);
        return state != null && state.applyPerishSong(sourcePokemonUUID, currentTick);
    }

    public boolean applyNightmare(UUID battleId, UUID targetPokemonUUID, UUID sourcePokemonUUID, boolean sleeping, long currentTick) {
        if (!ActionBattlePersistentRules.canApplyNightmare(sleeping)) return false;
        ActionBattlePersistentState state = state(battleId, targetPokemonUUID);
        return state != null && state.applyNightmare(sourcePokemonUUID, currentTick);
    }

    public boolean applyBound(UUID battleId, UUID targetPokemonUUID, UUID sourcePokemonUUID, long currentTick) {
        return applyBound(battleId, targetPokemonUUID, sourcePokemonUUID, currentTick,
                ActionBattlePersistentRules.boundDurationTicks(ThreadLocalRandom.current().nextInt(3)));
    }

    public boolean applyBound(UUID battleId, UUID targetPokemonUUID, UUID sourcePokemonUUID,
                              long currentTick, long durationTicks) {
        if (!valid(battleId, targetPokemonUUID) || sourcePokemonUUID == null || currentTick < 0L) return false;
        ActionBattlePersistentState state = state(battleId, targetPokemonUUID);
        if (state == null || state.has(ActionBattlePersistentType.BOUND, currentTick)) return false;
        return state.applyBound(sourcePokemonUUID, currentTick, durationTicks);
    }

    public boolean has(UUID battleId, UUID pokemonUUID, ActionBattlePersistentType type, long currentTick) {
        ActionBattlePersistentState state = existing(battleId, pokemonUUID);
        return state != null && state.has(type, currentTick);
    }

    public long remainingTicks(UUID battleId, UUID pokemonUUID, ActionBattlePersistentType type, long currentTick) {
        ActionBattlePersistentState state = existing(battleId, pokemonUUID);
        return state != null ? state.remainingTicks(type, currentTick) : 0L;
    }

    public long durationTicks(UUID battleId, UUID pokemonUUID, ActionBattlePersistentType type) {
        ActionBattlePersistentState state = existing(battleId, pokemonUUID);
        return state != null ? state.durationTicks(type) : 0L;
    }

    public boolean clearEffect(UUID battleId, UUID pokemonUUID, ActionBattlePersistentType type, long currentTick) {
        ActionBattlePersistentState state = existing(battleId, pokemonUUID);
        if (state == null) return false;
        if (!state.clear(type)) return false;
        removeIfEmpty(battleId, pokemonUUID, state);
        return true;
    }

    public void onSleepEnded(UUID battleId, UUID pokemonUUID) {
        ActionBattlePersistentState state = existing(battleId, pokemonUUID);
        if (state == null) return;
        state.onSleepEnded();
        removeIfEmpty(battleId, pokemonUUID, state);
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonUUID, boolean fainted, long currentTick) {
        if (!valid(battleId, pokemonUUID)) return;
        Map<UUID, ActionBattlePersistentState> battleStates = statesByBattle.get(battleId);
        if (battleStates == null) return;
        ActionBattlePersistentState own = battleStates.get(pokemonUUID);
        if (own != null) {
            if (fainted) battleStates.remove(pokemonUUID);
            else {
                own.onPokemonRecalled(currentTick);
                removeIfEmpty(battleId, pokemonUUID, own);
            }
        }
        battleStates.entrySet().removeIf(entry -> !entry.getKey().equals(pokemonUUID)
            && entry.getValue().onSourceUnavailable(pokemonUUID) && entry.getValue().isEmpty());
        if (battleStates.isEmpty()) statesByBattle.remove(battleId);
    }

    public List<ActionBattlePersistentTick> tickBattle(UUID battleId, long currentTick) {
        Map<UUID, ActionBattlePersistentState> battleStates = battleId != null ? statesByBattle.get(battleId) : null;
        if (battleStates == null || currentTick < 0L) return List.of();
        List<ActionBattlePersistentTick> ticks = new ArrayList<>();
        List<UUID> empty = new ArrayList<>();
        for (Map.Entry<UUID, ActionBattlePersistentState> entry : battleStates.entrySet()) {
            for (ActionBattlePersistentEvent event : entry.getValue().tick(currentTick)) {
                ticks.add(new ActionBattlePersistentTick(entry.getKey(), event));
            }
            if (entry.getValue().isEmpty()) empty.add(entry.getKey());
        }
        for (UUID pokemonUUID : empty) battleStates.remove(pokemonUUID);
        if (battleStates.isEmpty()) statesByBattle.remove(battleId);
        return List.copyOf(ticks);
    }

    public void clearBattle(UUID battleId) { if (battleId != null) statesByBattle.remove(battleId); }

    private ActionBattlePersistentState state(UUID battleId, UUID pokemonUUID) {
        if (!valid(battleId, pokemonUUID)) return null;
        return statesByBattle.computeIfAbsent(battleId, ignored -> new HashMap<>()).computeIfAbsent(pokemonUUID, ignored -> new ActionBattlePersistentState());
    }

    private ActionBattlePersistentState existing(UUID battleId, UUID pokemonUUID) {
        if (!valid(battleId, pokemonUUID)) return null;
        Map<UUID, ActionBattlePersistentState> battleStates = statesByBattle.get(battleId);
        return battleStates != null ? battleStates.get(pokemonUUID) : null;
    }

    private void removeIfEmpty(UUID battleId, UUID pokemonUUID, ActionBattlePersistentState state) {
        if (state == null || !state.isEmpty()) return;
        Map<UUID, ActionBattlePersistentState> battleStates = statesByBattle.get(battleId);
        if (battleStates == null) return;
        battleStates.remove(pokemonUUID);
        if (battleStates.isEmpty()) statesByBattle.remove(battleId);
    }

    private static boolean valid(UUID battleId, UUID pokemonUUID) { return battleId != null && pokemonUUID != null; }
}
