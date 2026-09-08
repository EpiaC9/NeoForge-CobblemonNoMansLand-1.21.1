package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleFightingController {
    private static final ActionBattleFightingController GLOBAL = new ActionBattleFightingController();
    private final Map<UUID, Map<UUID, ActionBattleFightingState>> statesBySession = new HashMap<>();

    public static ActionBattleFightingController global() { return GLOBAL; }

    public void onMoveUsed(UUID sessionId, UUID pokemonId, String moveId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        if (state == null) return;
        state.onMoveUsed(moveId, currentTick);
        removeIfEmpty(sessionId, pokemonId, state, currentTick);
    }

    public ActionBattleFightingState.HitResult onSuccessfulHit(
            UUID sessionId, UUID pokemonId, String moveId, boolean fightingMove,
            int moveSlot, long currentTick) {
        if (!valid(sessionId, pokemonId) || currentTick < 0L) {
            return ActionBattleFightingState.HitResult.IGNORED;
        }
        ActionBattleFightingState state = state(sessionId, pokemonId);
        ActionBattleFightingState.HitResult result = state.onSuccessfulHit(
                moveId, fightingMove, moveSlot, currentTick);
        removeIfEmpty(sessionId, pokemonId, state, currentTick);
        return result;
    }

    public Optional<ActionBattleFightingState.View> view(
            UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        if (state == null || currentTick < 0L) return Optional.empty();
        ActionBattleFightingState.View view = state.view(currentTick);
        if (state.isEmpty(currentTick)) {
            remove(sessionId, pokemonId);
            return Optional.empty();
        }
        return Optional.of(view);
    }

    public boolean canUseAbility(UUID sessionId, UUID pokemonId, String moveId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        return state == null || state.canUseAbility(moveId, currentTick);
    }

    public long sharedCooldownTicks(UUID sessionId, UUID pokemonId, String moveId,
                                    boolean fightingHolder, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        return state == null ? ActionBattleFightingRules.NORMAL_OUTRAGE_SHARED_COOLDOWN_TICKS
                : state.sharedCooldownTicks(moveId, fightingHolder, currentTick);
    }

    public double outgoingDamageMultiplier(UUID sessionId, UUID pokemonId, String moveId,
                                           boolean fightingHolder, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        return state == null ? 1.0D : state.outgoingDamageMultiplier(moveId, fightingHolder, currentTick);
    }

    public double normalLocomotionMultiplier(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        return state == null ? 1.0D : state.normalLocomotionMultiplier(currentTick);
    }

    public void armActivationCooldownSuppression(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        if (state != null) state.armActivationCooldownSuppression(currentTick);
    }

    public boolean consumeActivationCooldownSuppression(UUID sessionId, UUID pokemonId,
                                                        String moveId, int moveSlot, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        return state != null && state.consumeActivationCooldownSuppression(
                moveId, moveSlot, currentTick);
    }

    public void onPokemonUnavailable(UUID sessionId, UUID pokemonId, long currentTick) {
        ActionBattleFightingState state = existing(sessionId, pokemonId);
        if (state == null) return;
        state.clearOutrageEarly(currentTick);
        removeIfEmpty(sessionId, pokemonId, state, currentTick);
    }

    public void onBattleEnded(UUID sessionId, long currentTick) {
        tickSession(sessionId, currentTick);
    }

    public void tickSession(UUID sessionId, long currentTick) {
        Map<UUID, ActionBattleFightingState> states = sessionId != null
                ? statesBySession.get(sessionId) : null;
        if (states == null || currentTick < 0L) return;
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty(currentTick));
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }

    public void clearSession(UUID sessionId) {
        if (sessionId != null) statesBySession.remove(sessionId);
    }

    public void clearPokemon(UUID sessionId, UUID pokemonId) {
        if (valid(sessionId, pokemonId)) remove(sessionId, pokemonId);
    }

    public void clearAll() { statesBySession.clear(); }

    public int trackedPokemonCount(UUID sessionId) {
        Map<UUID, ActionBattleFightingState> states = sessionId != null
                ? statesBySession.get(sessionId) : null;
        return states != null ? states.size() : 0;
    }

    private ActionBattleFightingState state(UUID sessionId, UUID pokemonId) {
        return statesBySession.computeIfAbsent(sessionId, ignored -> new HashMap<>())
                .computeIfAbsent(pokemonId, ignored -> new ActionBattleFightingState());
    }

    private ActionBattleFightingState existing(UUID sessionId, UUID pokemonId) {
        if (!valid(sessionId, pokemonId)) return null;
        Map<UUID, ActionBattleFightingState> states = statesBySession.get(sessionId);
        return states != null ? states.get(pokemonId) : null;
    }

    private void removeIfEmpty(UUID sessionId, UUID pokemonId,
                               ActionBattleFightingState state, long currentTick) {
        if (state != null && state.isEmpty(currentTick)) remove(sessionId, pokemonId);
    }

    private void remove(UUID sessionId, UUID pokemonId) {
        Map<UUID, ActionBattleFightingState> states = statesBySession.get(sessionId);
        if (states == null) return;
        states.remove(pokemonId);
        if (states.isEmpty()) statesBySession.remove(sessionId);
    }

    private static boolean valid(UUID sessionId, UUID pokemonId) {
        return sessionId != null && pokemonId != null;
    }
}
