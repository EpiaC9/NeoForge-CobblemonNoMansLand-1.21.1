package net.epiac9.cobblemonnml.battle.action.effect.control;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleRampageController {
    private static final ActionBattleRampageController GLOBAL = new ActionBattleRampageController();
    private final Map<Key, ActionBattleRampageState> states = new HashMap<>();

    public static ActionBattleRampageController global() { return GLOBAL; }

    public boolean activate(UUID battleId, UUID pokemonId, String moveId, int moveSlot, long currentTick) {
        return valid(battleId, pokemonId) && states.computeIfAbsent(new Key(battleId, pokemonId), ignored ->
                new ActionBattleRampageState()).activate(moveId, moveSlot, currentTick);
    }

    public boolean canUseAbility(UUID battleId, UUID pokemonId, String moveId, long currentTick) {
        ActionBattleRampageState state = valid(battleId, pokemonId) ? states.get(new Key(battleId, pokemonId)) : null;
        return state == null || state.canUse(moveId, currentTick);
    }

    public double movementMultiplier(UUID battleId, UUID pokemonId, long currentTick) {
        ActionBattleRampageState state = valid(battleId, pokemonId) ? states.get(new Key(battleId, pokemonId)) : null;
        return state != null ? state.movementMultiplier(currentTick) : 1.0D;
    }

    public Optional<ActionBattleRampageState.View> view(UUID battleId, UUID pokemonId, long currentTick) {
        ActionBattleRampageState state = valid(battleId, pokemonId) ? states.get(new Key(battleId, pokemonId)) : null;
        if (state == null) return Optional.empty();
        ActionBattleRampageState.View view = state.view(currentTick);
        if (state.empty(currentTick)) states.remove(new Key(battleId, pokemonId));
        return Optional.of(view);
    }

    public void clearPokemon(UUID battleId, UUID pokemonId) { if (valid(battleId, pokemonId)) states.remove(new Key(battleId, pokemonId)); }
    public void clearBattle(UUID battleId) { if (battleId != null) states.keySet().removeIf(key -> key.battleId.equals(battleId)); }
    public void clearAll() { states.clear(); }
    private static boolean valid(UUID battleId, UUID pokemonId) { return battleId != null && pokemonId != null; }
    private record Key(UUID battleId, UUID pokemonId) {}
}
