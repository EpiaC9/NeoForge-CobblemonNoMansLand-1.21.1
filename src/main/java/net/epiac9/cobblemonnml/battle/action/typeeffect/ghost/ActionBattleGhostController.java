package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleGhostController {
    private final Map<Key, ActionBattleGhostCurseState> states = new HashMap<>();

    public void apply(UUID battleUUID, UUID casterPokemonUUID, UUID targetPokemonUUID,
                      ActionBattleGhostCurseType type, long currentTick) {
        states.computeIfAbsent(new Key(battleUUID, targetPokemonUUID), ignored -> new ActionBattleGhostCurseState())
                .apply(casterPokemonUUID, type, currentTick);
    }

    public boolean consume(UUID battleUUID, UUID targetPokemonUUID,
                           ActionBattleGhostCurseType type, long currentTick) {
        Key key = new Key(battleUUID, targetPokemonUUID);
        ActionBattleGhostCurseState state = states.get(key);
        if (state == null) return false;
        boolean consumed = state.consume(type, currentTick);
        if (state.isEmpty()) states.remove(key);
        return consumed;
    }

    public void clearStatCurses(UUID battleUUID, UUID targetPokemonUUID, long currentTick) {
        consume(battleUUID, targetPokemonUUID, ActionBattleGhostCurseType.FRAILTY, currentTick);
        consume(battleUUID, targetPokemonUUID, ActionBattleGhostCurseType.WEAKNESS, currentTick);
    }

    public Optional<ActionBattleGhostCurseState.View> view(UUID battleUUID, UUID targetPokemonUUID,
                                                            ActionBattleGhostCurseType type, long currentTick) {
        ActionBattleGhostCurseState state = states.get(new Key(battleUUID, targetPokemonUUID));
        return state == null ? Optional.empty() : state.view(type, currentTick);
    }

    public List<ActionBattleGhostCurseState.View> views(UUID battleUUID, UUID targetPokemonUUID,
                                                         long currentTick) {
        ActionBattleGhostCurseState state = states.get(new Key(battleUUID, targetPokemonUUID));
        return state == null ? List.of() : state.views(currentTick);
    }

    public List<ActionBattleGhostCurseState.TickEvent> tick(UUID battleUUID, UUID targetPokemonUUID,
                                                             long currentTick) {
        Key key = new Key(battleUUID, targetPokemonUUID);
        ActionBattleGhostCurseState state = states.get(key);
        if (state == null) return List.of();
        List<ActionBattleGhostCurseState.TickEvent> events = state.tick(currentTick);
        if (state.isEmpty()) states.remove(key);
        return events;
    }

    public List<UUID> trackedTargets(UUID battleUUID) {
        if (battleUUID == null) return List.of();
        return states.keySet().stream().filter(key -> key.battleUUID().equals(battleUUID))
                .map(Key::targetPokemonUUID).distinct().toList();
    }

    public List<UUID> hauntedTargets(UUID battleUUID, UUID casterPokemonUUID, long currentTick) {
        if (battleUUID == null || casterPokemonUUID == null) return List.of();
        return states.entrySet().stream()
                .filter(entry -> entry.getKey().battleUUID().equals(battleUUID))
                .filter(entry -> entry.getValue().view(
                        ActionBattleGhostCurseType.HAUNTING, currentTick)
                        .map(view -> view.casterPokemonUUID().equals(casterPokemonUUID)).orElse(false))
                .map(entry -> entry.getKey().targetPokemonUUID()).distinct().toList();
    }

    public void onPokemonUnavailable(UUID battleUUID, UUID pokemonUUID) {
        states.entrySet().removeIf(entry -> entry.getKey().battleUUID().equals(battleUUID)
                && entry.getKey().targetPokemonUUID().equals(pokemonUUID));
        for (var entry : List.copyOf(states.entrySet())) {
            if (!entry.getKey().battleUUID().equals(battleUUID)) continue;
            entry.getValue().removeOwnedBy(pokemonUUID);
            if (entry.getValue().isEmpty()) states.remove(entry.getKey());
        }
    }

    public void clearBattle(UUID battleUUID) {
        states.keySet().removeIf(key -> key.battleUUID().equals(battleUUID));
    }

    public void clearAll() {
        states.clear();
    }

    private record Key(UUID battleUUID, UUID targetPokemonUUID) {}
}
