package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleBugController {
    private static final ActionBattleBugController GLOBAL = new ActionBattleBugController();
    private final Map<Key, ActionBattleBugState> states = new HashMap<>();
    private final Map<Key, ConstructPosition> constructs = new HashMap<>();

    public static ActionBattleBugController global() { return GLOBAL; }

    public ActionBattleBugState.TriggerResult trigger(UUID battleId, UUID pokemonId,
                                                       EnumSet<ActionBattleBugTrainingStat> highest,
                                                       boolean bugTyped, int maxHp, long tick) {
        if (battleId == null || pokemonId == null) return ActionBattleBugState.TriggerResult.NONE;
        return states.computeIfAbsent(new Key(battleId, pokemonId), ignored -> new ActionBattleBugState())
                .trigger(highest, bugTyped, maxHp, tick);
    }

    public Optional<ActionBattleBugState> state(UUID battleId, UUID pokemonId) {
        return Optional.ofNullable(battleId != null && pokemonId != null ? states.get(new Key(battleId, pokemonId)) : null);
    }

    public boolean locked(UUID battleId, UUID pokemonId, ActionBattleBugTrainingStat branch, long tick) {
        return state(battleId, pokemonId).map(value -> value.locked(branch, tick)).orElse(false);
    }

    public boolean clearPokemon(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return false;
        Key key = new Key(battleId, pokemonId);
        constructs.remove(key);
        return states.remove(key) != null;
    }
    public void clearBattle(UUID battleId) {
        if (battleId == null) return;
        states.keySet().removeIf(key -> key.battleId.equals(battleId));
        constructs.keySet().removeIf(key -> key.battleId.equals(battleId));
    }
    public void clearAll() { states.clear(); constructs.clear(); }

    public void setConstruct(UUID battleId, UUID pokemonId, int x, int y, int z) {
        if (battleId != null && pokemonId != null) constructs.put(
                new Key(battleId, pokemonId), new ConstructPosition(x, y, z));
    }

    public Optional<ConstructPosition> construct(UUID battleId, UUID pokemonId) {
        return Optional.ofNullable(battleId != null && pokemonId != null
                ? constructs.get(new Key(battleId, pokemonId)) : null);
    }

    public boolean isConstructAt(UUID battleId, UUID pokemonId, int x, int y, int z) {
        return construct(battleId, pokemonId).map(value -> value.equals(new ConstructPosition(x, y, z))).orElse(false);
    }

    public void clearConstruct(UUID battleId, UUID pokemonId, int x, int y, int z) {
        if (battleId == null || pokemonId == null) return;
        Key key = new Key(battleId, pokemonId);
        constructs.computeIfPresent(key, (ignored, current) -> current.equals(new ConstructPosition(x, y, z)) ? null : current);
    }

    public record ConstructPosition(int x, int y, int z) {}

    private record Key(UUID battleId, UUID pokemonId) {}
}
