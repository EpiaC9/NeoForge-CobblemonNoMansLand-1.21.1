package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleDragonController {
    private static final ActionBattleDragonController GLOBAL = new ActionBattleDragonController();
    private final Map<UUID, Map<UUID, ActionBattleDragonState>> statesBySession = new HashMap<>();

    public static ActionBattleDragonController global() { return GLOBAL; }

    public ActionBattleDragonState.CommitResult onAbilityCommitted(UUID sessionId, UUID pokemonId,
                                                                    boolean dragonMove, boolean dragonHolder,
                                                                    long currentTick) {
        if (!valid(sessionId, pokemonId, currentTick)) return ActionBattleDragonState.CommitResult.IGNORED;
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null && !dragonMove) return ActionBattleDragonState.CommitResult.IGNORED;
        if (state == null) state = state(sessionId, pokemonId);
        state.setDragonHolder(dragonHolder);
        ActionBattleDragonState.CommitResult result = state.onAbilityCommitted(dragonMove, currentTick);
        prune(sessionId, pokemonId, state, currentTick);
        return result;
    }

    public boolean onDamageTaken(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return false;
        boolean sustained = state.onDamageTaken(currentTick);
        prune(sessionId, pokemonId, state, currentTick);
        return sustained;
    }

    public ActionBattleDragonState.TickResult tickPokemon(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return ActionBattleDragonState.TickResult.NONE;
        ActionBattleDragonState.TickResult result = state.tick(currentTick);
        prune(sessionId, pokemonId, state, currentTick);
        return result;
    }

    public Optional<ActionBattleDragonState.View> view(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return Optional.empty();
        ActionBattleDragonState.View view = state.view(currentTick);
        prune(sessionId, pokemonId, state, currentTick);
        return view.phase() == ActionBattleDragonState.Phase.IDLE ? Optional.empty() : Optional.of(view);
    }

    public ActionBattleDragonState.ExitResult exit(UUID sessionId, UUID pokemonId,
                                                    ActionBattleDragonState.ExitReason reason, long currentTick) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null) return ActionBattleDragonState.ExitResult.NONE;
        ActionBattleDragonState.ExitResult result = state.exit(reason, currentTick);
        prune(sessionId, pokemonId, state, currentTick);
        return result;
    }

    public void suppressStatsByHaze(UUID sessionId, UUID pokemonId) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state != null) state.suppressStatsByHaze();
    }

    public void finishSleepConsequence(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleDragonState state = existing(sessionId, pokemonId);
        if (state == null) return;
        state.finishSleepConsequence();
        prune(sessionId, pokemonId, state, currentTick);
    }

    public void onBattleEnded(UUID sessionId, long currentTick) {
        Map<UUID, ActionBattleDragonState> states = sessionId != null ? statesBySession.get(sessionId) : null;
        if (states == null || currentTick < 0L) return;
        for (ActionBattleDragonState state : states.values()) state.tick(currentTick);
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty(currentTick));
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }

    public void clearSession(UUID sessionId) { if (sessionId != null) statesBySession.remove(sessionId); }
    public void clearPokemon(UUID sessionId, UUID pokemonId) {
        Map<UUID, ActionBattleDragonState> states = sessionId != null ? statesBySession.get(sessionId) : null;
        if (states == null || pokemonId == null) return;
        states.remove(pokemonId);
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }
    public void clearAll() { statesBySession.clear(); }

    private ActionBattleDragonState state(UUID sessionId, UUID pokemonId) {
        return statesBySession.computeIfAbsent(sessionId, ignored -> new HashMap<>())
                .computeIfAbsent(pokemonId, ignored -> new ActionBattleDragonState());
    }

    private ActionBattleDragonState existing(UUID sessionId, UUID pokemonId) {
        Map<UUID, ActionBattleDragonState> states = sessionId != null ? statesBySession.get(sessionId) : null;
        return states != null && pokemonId != null ? states.get(pokemonId) : null;
    }

    private void prune(UUID sessionId, UUID pokemonId, ActionBattleDragonState state, long currentTick) {
        if (state == null || !state.isEmpty(currentTick)) return;
        Map<UUID, ActionBattleDragonState> states = statesBySession.get(sessionId);
        if (states == null) return;
        states.remove(pokemonId);
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }

    private static boolean valid(UUID sessionId, UUID pokemonId, long currentTick) {
        return sessionId != null && pokemonId != null && currentTick >= 0L;
    }
}
