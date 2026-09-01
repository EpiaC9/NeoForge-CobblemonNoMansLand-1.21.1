package net.epiac9.cobblemonnml.battle.action.control;

import com.cobblemon.mod.common.api.moves.Move;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleControlController {
    private static final ActionBattleControlController GLOBAL = new ActionBattleControlController();
    private final Map<UUID, Map<UUID, PokemonControlState>> statesByBattle = new HashMap<>();

    private ActionBattleControlController() {}
    public static ActionBattleControlController global() { return GLOBAL; }

    public ActionBattleControlState.ApplyResult applyTaunt(UUID battleId, UUID pokemonUUID, long currentTick) {
        return apply(battleId, pokemonUUID, ActionBattleControlEffect.taunt(), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyDisable(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState pokemonState = existingOrCreate(battleId, pokemonUUID);
        if (pokemonState == null || pokemonState.lastCommittedMoveId == null) return ActionBattleControlState.ApplyResult.REJECTED_INVALID;
        return pokemonState.control.apply(ActionBattleControlEffect.disable(pokemonState.lastCommittedMoveId), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyEncore(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState pokemonState = existingOrCreate(battleId, pokemonUUID);
        if (pokemonState == null || pokemonState.lastCommittedMoveId == null) return ActionBattleControlState.ApplyResult.REJECTED_INVALID;
        return pokemonState.control.apply(ActionBattleControlEffect.encore(pokemonState.lastCommittedMoveId), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyHealBlock(UUID battleId, UUID pokemonUUID, long currentTick) {
        return apply(battleId, pokemonUUID, ActionBattleControlEffect.healBlock(), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyTorment(UUID battleId, UUID pokemonUUID, UUID sourcePokemonUUID, long currentTick) {
        return apply(battleId, pokemonUUID, ActionBattleControlEffect.torment(sourcePokemonUUID), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyImprison(UUID battleId, UUID pokemonUUID, UUID sourcePokemonUUID, Set<String> blockedMoveIds, long currentTick) {
        if (blockedMoveIds == null || blockedMoveIds.isEmpty()) return ActionBattleControlState.ApplyResult.REJECTED_INVALID;
        return apply(battleId, pokemonUUID, ActionBattleControlEffect.imprison(sourcePokemonUUID, blockedMoveIds), currentTick);
    }

    public ActionBattleControlState.ApplyResult applyTrapped(UUID battleId, UUID pokemonUUID, UUID sourcePokemonUUID, long currentTick) {
        return apply(battleId, pokemonUUID, ActionBattleControlEffect.trapped(sourcePokemonUUID), currentTick);
    }

    public ActionBattleControlState.ApplyResult apply(UUID battleId, UUID pokemonUUID, ActionBattleControlEffect effect, long currentTick) {
        PokemonControlState pokemonState = existingOrCreate(battleId, pokemonUUID);
        if (pokemonState == null) return ActionBattleControlState.ApplyResult.REJECTED_INVALID;
        return pokemonState.control.apply(effect, currentTick);
    }

    public void recordSuccessfulMove(UUID battleId, UUID pokemonUUID, Move move) {
        recordSuccessfulMove(battleId, pokemonUUID, move != null ? move.getName() : null);
    }

    public void recordSuccessfulMove(UUID battleId, UUID pokemonUUID, String moveId) {
        PokemonControlState state = existingOrCreate(battleId, pokemonUUID);
        String normalized = ActionBattleControlRules.normalize(moveId);
        if (state != null && normalized != null) state.lastCommittedMoveId = normalized;
    }

    public String lastCommittedMoveId(UUID battleId, UUID pokemonUUID) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null ? state.lastCommittedMoveId : null;
    }

    public boolean canUseMove(UUID battleId, UUID pokemonUUID, Move move, long currentTick) {
        if (move == null) return false;
        PokemonControlState state = existing(battleId, pokemonUUID);
        if (state == null) return true;
        ActionBattleControlEffect effect = state.control.activeEffect(currentTick);
        boolean damaging = FightOrFlightAdapter.isNativeDamageMove(move) || FightOrFlightAdapter.movePower(move) > 0;
        return ActionBattleControlRules.canUseMove(effect, move.getName(), damaging, state.lastCommittedMoveId);
    }

    public boolean blocksHealing(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null && ActionBattleControlRules.blocksHealing(state.control.activeEffect(currentTick));
    }

    public boolean blocksSwap(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null && ActionBattleControlRules.blocksSwap(state.control.activeEffect(currentTick));
    }

    public ActionBattleControlEffect activeEffect(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null ? state.control.activeEffect(currentTick) : null;
    }

    public long activeRemainingTicks(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null ? state.control.remainingTicks(currentTick) : 0L;
    }

    public long activeDurationTicks(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null ? state.control.activeDurationTicks(currentTick) : 0L;
    }

    public long graceRemainingTicks(UUID battleId, UUID pokemonUUID, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null ? state.control.graceRemainingTicks(currentTick) : 0L;
    }

    public boolean endActive(UUID battleId, UUID pokemonUUID, ActionBattleControlState.EndReason reason, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        return state != null && state.control.end(reason, currentTick);
    }

    public boolean endConditional(UUID battleId, UUID pokemonUUID, ActionBattleControlType expectedType, long currentTick) {
        PokemonControlState state = existing(battleId, pokemonUUID);
        if (state == null || state.control.activeType(currentTick) != expectedType || expectedType == null || expectedType.timed()) return false;
        return state.control.end(ActionBattleControlState.EndReason.CONDITION_ENDED, currentTick);
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonUUID, boolean fainted, long currentTick) {
        if (battleId == null || pokemonUUID == null || currentTick < 0L) return;
        Map<UUID, PokemonControlState> battleStates = statesByBattle.get(battleId);
        if (battleStates == null) return;
        PokemonControlState ownState = battleStates.get(pokemonUUID);
        if (ownState != null && ownState.control.activeEffect(currentTick) != null) {
            ownState.control.end(fainted ? ActionBattleControlState.EndReason.FAINTED : ActionBattleControlState.EndReason.RECALLED, currentTick);
        }
        for (Map.Entry<UUID, PokemonControlState> entry : battleStates.entrySet()) {
            if (entry.getKey().equals(pokemonUUID)) continue;
            ActionBattleControlEffect active = entry.getValue().control.activeEffect(currentTick);
            if (active == null || !pokemonUUID.equals(active.sourcePokemonUUID())) continue;
            if (active.type() == ActionBattleControlType.IMPRISON || active.type() == ActionBattleControlType.TRAPPED) {
                entry.getValue().control.end(ActionBattleControlState.EndReason.SOURCE_ENDED, currentTick);
            }
        }
    }

    public void tickBattle(UUID battleId, long currentTick) {
        Map<UUID, PokemonControlState> states = battleId != null ? statesByBattle.get(battleId) : null;
        if (states == null || currentTick < 0L) return;
        for (PokemonControlState state : states.values()) state.control.tick(currentTick);
    }

    public void clearBattle(UUID battleId) { if (battleId != null) statesByBattle.remove(battleId); }

    private PokemonControlState existingOrCreate(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) return null;
        return statesByBattle.computeIfAbsent(battleId, ignored -> new HashMap<>()).computeIfAbsent(pokemonUUID, ignored -> new PokemonControlState());
    }

    private PokemonControlState existing(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) return null;
        Map<UUID, PokemonControlState> battleStates = statesByBattle.get(battleId);
        return battleStates != null ? battleStates.get(pokemonUUID) : null;
    }

    private static final class PokemonControlState {
        private final ActionBattleControlState control = new ActionBattleControlState();
        private String lastCommittedMoveId;
    }
}
