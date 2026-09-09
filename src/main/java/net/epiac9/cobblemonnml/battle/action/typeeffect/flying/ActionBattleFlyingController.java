package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleFlyingController {
    private static final ActionBattleFlyingController GLOBAL = new ActionBattleFlyingController();
    private final Map<Key, ActionBattleFlyingState> states = new HashMap<>();

    public static ActionBattleFlyingController global() {
        return GLOBAL;
    }

    public boolean tick(UUID battleId, UUID pokemonId, boolean flyingPokemon,
                        boolean visibleEnemy, long currentTick) {
        if (battleId == null || pokemonId == null) return false;
        return states.computeIfAbsent(new Key(battleId, pokemonId), ignored -> new ActionBattleFlyingState())
                .tick(flyingPokemon, visibleEnemy, currentTick);
    }

    public int momentum(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return 0;
        ActionBattleFlyingState state = states.get(new Key(battleId, pokemonId));
        return state != null ? state.level() : 0;
    }

    public double progress(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return 0.0D;
        ActionBattleFlyingState state = states.get(new Key(battleId, pokemonId));
        return state != null ? state.progress() : 0.0D;
    }

    public void clearPokemon(UUID battleId, UUID pokemonId) {
        if (battleId != null && pokemonId != null) states.remove(new Key(battleId, pokemonId));
    }

    public void clearBattle(UUID battleId) {
        if (battleId != null) states.keySet().removeIf(key -> battleId.equals(key.battleId));
    }

    public void clearAll() {
        states.clear();
    }

    private record Key(UUID battleId, UUID pokemonId) {}
}
