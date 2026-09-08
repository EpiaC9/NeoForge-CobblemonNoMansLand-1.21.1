package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleDarkController {
    private static final ActionBattleDarkController GLOBAL = new ActionBattleDarkController();
    private final Map<UUID, Map<UUID, ActionBattleDarkState>> statesBySession = new HashMap<>();

    public static ActionBattleDarkController global() { return GLOBAL; }

    public ActionBattleDarkState.HitResult applyHit(UUID sessionId, UUID pokemonId,
                                                     int awarenessReduction, int obscurityIncrease,
                                                     long currentTick) {
        if (!valid(sessionId, pokemonId, currentTick) || awarenessReduction <= 0) {
            return ActionBattleDarkState.HitResult.IGNORED;
        }
        return state(sessionId, pokemonId).applyHit(awarenessReduction, obscurityIncrease, currentTick);
    }

    public void tickPokemon(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDarkState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return;
        state.tick(currentTick);
        prune(sessionId, pokemonId, state);
    }

    public Optional<ActionBattleDarkState.View> view(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDarkState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L || state.isEmpty()) return Optional.empty();
        return Optional.of(state.view(currentTick));
    }

    public double currentAwareness(UUID sessionId, UUID pokemonId, long currentTick) {
        return view(sessionId, pokemonId, currentTick)
                .map(ActionBattleDarkState.View::currentAwareness)
                .orElse(ActionBattleDarkRules.NORMAL_AWARENESS);
    }

    public void onPokemonUnavailable(UUID sessionId, UUID pokemonId, boolean fainted,
                                     long currentTick) {
        ActionBattleDarkState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return;
        if (fainted) state.onFaint();
        else state.onSwapOrRecall(currentTick);
        prune(sessionId, pokemonId, state);
    }

    public void clearPokemon(UUID sessionId, UUID pokemonId) {
        Map<UUID, ActionBattleDarkState> states = sessionId != null ? statesBySession.get(sessionId) : null;
        if (states == null || pokemonId == null) return;
        states.remove(pokemonId);
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }

    public void clearSession(UUID sessionId) { if (sessionId != null) statesBySession.remove(sessionId); }
    public void clearAll() { statesBySession.clear(); }

    private ActionBattleDarkState state(UUID sessionId, UUID pokemonId) {
        return statesBySession.computeIfAbsent(sessionId, ignored -> new HashMap<>())
                .computeIfAbsent(pokemonId, ignored -> new ActionBattleDarkState());
    }

    private ActionBattleDarkState existing(UUID sessionId, UUID pokemonId) {
        Map<UUID, ActionBattleDarkState> states = sessionId != null ? statesBySession.get(sessionId) : null;
        return states != null && pokemonId != null ? states.get(pokemonId) : null;
    }

    private void prune(UUID sessionId, UUID pokemonId, ActionBattleDarkState state) {
        if (state == null || !state.isEmpty()) return;
        clearPokemon(sessionId, pokemonId);
    }

    private static boolean valid(UUID sessionId, UUID pokemonId, long currentTick) {
        return sessionId != null && pokemonId != null && currentTick >= 0L;
    }
}
