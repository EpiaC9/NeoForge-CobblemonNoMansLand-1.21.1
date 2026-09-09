package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattlePsycUpController {
    private static final ActionBattlePsycUpController GLOBAL = new ActionBattlePsycUpController();
    private final Map<UUID, BattleState> battles = new HashMap<>();

    public static ActionBattlePsycUpController global() { return GLOBAL; }

    public static ApplyResult onSuccessfulEnemyMoveResolved(PokemonEntity attacker, PokemonEntity target,
                                                             Move move, boolean success) {
        if (attacker == null || target == null || move == null || attacker.level().isClientSide) {
            return ApplyResult.INVALID;
        }
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        boolean sameBattle = session != null && session.battleId().equals(
                ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()));
        String moveType = ActionBattleEffectiveMoveTypeResolver.resolve(attacker, move);
        if (!ActionBattlePsycUpMoveRules.qualifies(moveType, FightOrFlightAdapter.moveTargetCategory(move),
                FightOrFlightAdapter.movePower(move), success && sameBattle)) return ApplyResult.INVALID;
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                session, target, attacker.level().getGameTime())) return ApplyResult.IMMUNE;
        return GLOBAL.applyMark(session.battleId(), attacker.getPokemon().getUuid(), target.getPokemon().getUuid(),
                ActionBattleTypeMechanicIdentity.hasMechanicBenefit(attacker, "psychic"),
                ActionBattleTypeMechanicIdentity.hasActualType(target.getPokemon(), "dark"),
                attacker.level().getGameTime());
    }

    public ApplyResult applyMark(UUID battleId, UUID casterPokemonId, UUID markedPokemonId,
                                 boolean casterPsychicTyped, long currentTick) {
        return applyMark(battleId, casterPokemonId, markedPokemonId,
                casterPsychicTyped, false, currentTick);
    }

    public ApplyResult applyMark(UUID battleId, UUID casterPokemonId, UUID markedPokemonId,
                                 boolean casterPsychicTyped, boolean targetDarkTyped,
                                 long currentTick) {
        if (targetDarkTyped) return ApplyResult.IMMUNE;
        if (battleId == null || casterPokemonId == null || markedPokemonId == null || currentTick < 0L
                || casterPokemonId.equals(markedPokemonId)) return ApplyResult.INVALID;
        tickBattle(battleId, currentTick);
        BattleState battle = battles.computeIfAbsent(battleId, ignored -> new BattleState());
        ActionBattlePsycUpState active = battle.marksByTarget.get(markedPokemonId);
        if (active != null && active.active(currentTick)) return ApplyResult.IGNORED_ACTIVE;
        History history = battle.historiesByTarget.computeIfAbsent(markedPokemonId, ignored -> new History());
        long duration = ActionBattlePsycUpRules.durationTicks(history.completedCycles);
        battle.marksByTarget.put(markedPokemonId, new ActionBattlePsycUpState(casterPokemonId, markedPokemonId,
                casterPsychicTyped, currentTick, ActionBattleTiming.safeAdd(currentTick, duration)));
        history.resetEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattlePsycUpRules.HISTORY_RESET_TICKS);
        return ApplyResult.APPLIED;
    }

    public Optional<View> view(UUID battleId, UUID markedPokemonId, long currentTick) {
        if (battleId == null || markedPokemonId == null || currentTick < 0L) return Optional.empty();
        tickBattle(battleId, currentTick);
        BattleState battle = battles.get(battleId);
        if (battle == null) return Optional.empty();
        ActionBattlePsycUpState state = battle.marksByTarget.get(markedPokemonId);
        History history = battle.historiesByTarget.get(markedPokemonId);
        return state == null ? Optional.empty() : Optional.of(new View(state.casterPokemonId(), state.markedPokemonId(),
                state.casterPsychicTyped(), state.startTick(), state.endTick(), state.remainingTicks(currentTick),
                history != null ? history.resetEndTick : -1L));
    }

    public Optional<ActionBattlePsycUpState> activeLink(UUID battleId, UUID markedPokemonId, long currentTick) {
        if (battleId == null || markedPokemonId == null || currentTick < 0L) return Optional.empty();
        tickBattle(battleId, currentTick);
        BattleState battle = battles.get(battleId);
        return battle == null ? Optional.empty() : Optional.ofNullable(battle.marksByTarget.get(markedPokemonId));
    }

    public long nextDurationTicks(UUID battleId, UUID markedPokemonId, long currentTick) {
        if (battleId == null || markedPokemonId == null || currentTick < 0L) {
            return ActionBattlePsycUpRules.durationTicks(0);
        }
        tickBattle(battleId, currentTick);
        BattleState battle = battles.get(battleId);
        History history = battle != null ? battle.historiesByTarget.get(markedPokemonId) : null;
        return ActionBattlePsycUpRules.durationTicks(history != null ? history.completedCycles : 0);
    }

    public void tickBattle(UUID battleId, long currentTick) {
        if (battleId == null || currentTick < 0L) return;
        BattleState battle = battles.get(battleId);
        if (battle == null) return;
        Iterator<Map.Entry<UUID, ActionBattlePsycUpState>> marks = battle.marksByTarget.entrySet().iterator();
        while (marks.hasNext()) {
            Map.Entry<UUID, ActionBattlePsycUpState> entry = marks.next();
            if (entry.getValue().active(currentTick)) continue;
            History history = battle.historiesByTarget.computeIfAbsent(entry.getKey(), ignored -> new History());
            history.completedCycles = ActionBattlePsycUpRules.nextCompletedCycles(history.completedCycles);
            marks.remove();
        }
        battle.historiesByTarget.entrySet().removeIf(entry -> {
            boolean active = battle.marksByTarget.containsKey(entry.getKey());
            return !active && entry.getValue().resetEndTick >= 0L && currentTick >= entry.getValue().resetEndTick;
        });
        if (battle.isEmpty()) battles.remove(battleId);
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonId) {
        BattleState battle = battleId != null && pokemonId != null ? battles.get(battleId) : null;
        if (battle == null) return;
        var removedTargets = new java.util.HashSet<UUID>();
        battle.marksByTarget.entrySet().removeIf(entry -> {
            boolean remove = entry.getKey().equals(pokemonId)
                    || entry.getValue().casterPokemonId().equals(pokemonId);
            if (remove) removedTargets.add(entry.getKey());
            return remove;
        });
        battle.historiesByTarget.remove(pokemonId);
        for (UUID removedTarget : removedTargets) battle.historiesByTarget.remove(removedTarget);
        if (battle.isEmpty()) battles.remove(battleId);
    }

    public void clearBattle(UUID battleId) { if (battleId != null) battles.remove(battleId); }
    public void clearAll() { battles.clear(); }

    public enum ApplyResult { APPLIED, IGNORED_ACTIVE, IMMUNE, INVALID }

    public record View(UUID casterPokemonId, UUID markedPokemonId, boolean casterPsychicTyped,
                       long startTick, long endTick, long remainingTicks, long historyResetEndTick) {}

    private static final class BattleState {
        private final Map<UUID, ActionBattlePsycUpState> marksByTarget = new HashMap<>();
        private final Map<UUID, History> historiesByTarget = new HashMap<>();
        private boolean isEmpty() { return marksByTarget.isEmpty() && historiesByTarget.isEmpty(); }
    }

    private static final class History {
        private int completedCycles;
        private long resetEndTick = -1L;
    }

    private static boolean hasPsychicType(PokemonEntity entity) {
        var pokemon = entity.getPokemon();
        return pokemon.getPrimaryType() != null && "psychic".equalsIgnoreCase(pokemon.getPrimaryType().getName())
                || pokemon.getSecondaryType() != null && "psychic".equalsIgnoreCase(pokemon.getSecondaryType().getName());
    }
}
