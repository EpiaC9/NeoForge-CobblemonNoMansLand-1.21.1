package net.epiac9.cobblemonnml.battle.action.channel;

import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ActionBattleChannelController {
    private static final ActionBattleChannelController GLOBAL = new ActionBattleChannelController();
    private final Map<UUID, ActiveChannel> activeByCaster = new HashMap<>();

    public static ActionBattleChannelController global() { return GLOBAL; }

    public boolean start(UUID battleId, UUID casterPokemonUUID, UUID targetPokemonUUID, String moveId, ActionBattleChannelPreset preset,
                         ActionBattlePosition initialTargetPosition, int initialCasterHealth,
                         Consumer<ActionBattleChannelState> completion,
                         BiConsumer<ActionBattleChannelState, ActionBattleChannelCancelReason> cancellation) {
        if (battleId == null || casterPokemonUUID == null || moveId == null || moveId.isBlank() || preset == null || completion == null || cancellation == null) return false;
        if (activeByCaster.containsKey(casterPokemonUUID)) return false;
        ActionBattleChannelState state = new ActionBattleChannelState(battleId, casterPokemonUUID, targetPokemonUUID, moveId, preset, initialTargetPosition, initialCasterHealth);
        activeByCaster.put(casterPokemonUUID, new ActiveChannel(state, completion, cancellation));
        return true;
    }

    public void tick(UUID battleId, TargetTracker tracker) {
        if (battleId == null) return;
        for (UUID caster : castersForBattle(battleId)) {
            ActiveChannel active = activeByCaster.get(caster);
            if (active == null) continue;
            ActionBattleChannelState state = active.state();
            state.advance();
            if (state.elapsedTicks() >= state.preset().durationTicks()) {
                state.markCompleted();
                activeByCaster.remove(caster);
                active.completion().accept(state);
                continue;
            }
            if (state.preset().trackTargetPosition() && tracker != null) {
                TargetUpdate update = tracker.update(state);
                if (update == null || !update.reachable()) state.queueCancel(ActionBattleChannelCancelReason.TARGET_UNREACHABLE);
                else state.updateLastTargetablePosition(update.position());
            }
            ActionBattleChannelCancelReason reason = state.consumeQueuedCancel();
            if (reason != null) cancelNow(caster, reason);
        }
    }

    public void observeHealth(UUID casterPokemonUUID, int currentHealth) {
        ActiveChannel active = activeByCaster.get(casterPokemonUUID);
        if (active == null) return;
        ActionBattleChannelState state = active.state();
        if (currentHealth < state.lastObservedHealth() && state.preset().cancelOnDamage()) {
            interrupt(casterPokemonUUID, state, ActionBattleChannelCancelReason.DAMAGE);
            return;
        }
        state.setLastObservedHealth(currentHealth);
    }

    public void onDamage(UUID casterPokemonUUID) {
        ActiveChannel active = activeByCaster.get(casterPokemonUUID);
        if (active == null || !active.state().preset().cancelOnDamage()) return;
        interrupt(casterPokemonUUID, active.state(), ActionBattleChannelCancelReason.DAMAGE);
    }

    public void onCommand(UUID casterPokemonUUID) {
        ActiveChannel active = activeByCaster.get(casterPokemonUUID);
        if (active == null || !active.state().preset().cancelOnCommand()) return;
        interrupt(casterPokemonUUID, active.state(), ActionBattleChannelCancelReason.COMMAND);
    }

    public void queueCancel(UUID casterPokemonUUID, ActionBattleChannelCancelReason reason) {
        ActiveChannel active = activeByCaster.get(casterPokemonUUID);
        if (active != null && reason != null) active.state().queueCancel(reason);
    }

    public boolean cancel(UUID casterPokemonUUID, ActionBattleChannelCancelReason reason) {
        return cancelNow(casterPokemonUUID, reason);
    }

    private boolean cancelNow(UUID casterPokemonUUID, ActionBattleChannelCancelReason reason) {
        if (casterPokemonUUID == null || reason == null) return false;
        ActiveChannel active = activeByCaster.remove(casterPokemonUUID);
        if (active == null || active.state().completed()) return false;
        active.cancellation().accept(active.state(), reason);
        return true;
    }

    private void interrupt(UUID casterPokemonUUID, ActionBattleChannelState state, ActionBattleChannelCancelReason reason) {
        if (state.remainingTicks() <= 1) state.queueCancel(reason);
        else cancelNow(casterPokemonUUID, reason);
    }

    public void clearBattle(UUID battleId) {
        if (battleId == null) return;
        for (UUID caster : castersForBattle(battleId)) cancelNow(caster, ActionBattleChannelCancelReason.BATTLE_END);
    }

    public boolean isChanneling(UUID casterPokemonUUID) { return casterPokemonUUID != null && activeByCaster.containsKey(casterPokemonUUID); }
    public int remainingTicks(UUID casterPokemonUUID) { ActiveChannel active = activeByCaster.get(casterPokemonUUID); return active != null ? active.state().remainingTicks() : 0; }
    public Optional<ActionBattleChannelState> state(UUID casterPokemonUUID) { ActiveChannel active = activeByCaster.get(casterPokemonUUID); return active != null ? Optional.of(active.state()) : Optional.empty(); }
    public List<ActionBattleChannelState> statesForBattle(UUID battleId) {
        if (battleId == null) return List.of();
        List<ActionBattleChannelState> result = new ArrayList<>();
        for (UUID caster : castersForBattle(battleId)) {
            ActiveChannel active = activeByCaster.get(caster);
            if (active != null) result.add(active.state());
        }
        return List.copyOf(result);
    }

    private List<UUID> castersForBattle(UUID battleId) {
        if (battleId == null) return List.of();
        List<UUID> casters = new ArrayList<>();
        for (Map.Entry<UUID, ActiveChannel> entry : activeByCaster.entrySet()) {
            if (battleId.equals(entry.getValue().state().battleId())) casters.add(entry.getKey());
        }
        return casters;
    }

    public record TargetUpdate(boolean reachable, ActionBattlePosition position) {}
    @FunctionalInterface public interface TargetTracker { TargetUpdate update(ActionBattleChannelState state); }
    private record ActiveChannel(ActionBattleChannelState state, Consumer<ActionBattleChannelState> completion,
                                 BiConsumer<ActionBattleChannelState, ActionBattleChannelCancelReason> cancellation) {}
}
