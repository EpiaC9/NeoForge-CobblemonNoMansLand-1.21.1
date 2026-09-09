package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.dimension.DungeonSession;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleFightingRuntime {
    private ActionBattleFightingRuntime() {}

    public static void onMoveCommitted(PokemonEntity caster, Move move) {
        ActionBattleSession session = session(caster);
        if (session == null || move == null) return;
        ActionBattleFightingController.global().onMoveUsed(session.dungeonSessionId(),
                caster.getPokemon().getUuid(), move.getName(), caster.level().getGameTime());
    }

    public static ActionBattleFightingState.HitResult onSuccessfulHit(
            PokemonEntity attacker, Move move, boolean successful, boolean blocked) {
        ActionBattleSession session = session(attacker);
        if (session == null || move == null || !successful || blocked) {
            return ActionBattleFightingState.HitResult.IGNORED;
        }
        int slot = moveSlot(attacker.getPokemon(), move);
        ActionBattleFightingState.HitResult result = ActionBattleFightingController.global().onSuccessfulHit(
                session.dungeonSessionId(), attacker.getPokemon().getUuid(), move.getName(),
                isFightingMove(move), slot, attacker.level().getGameTime());
        if (result == ActionBattleFightingState.HitResult.ACTIVATED) {
            boolean cooldownAlreadyRunning = session.isPokemonAbilitySlotOnCooldown(
                    attacker.getPokemon().getUuid(), slot, attacker.level().getGameTime());
            if (!cooldownAlreadyRunning) {
                ActionBattleFightingController.global().armActivationCooldownSuppression(
                        session.dungeonSessionId(), attacker.getPokemon().getUuid(),
                        attacker.level().getGameTime());
            }
            session.clearPokemonSharedAbilityCooldown(attacker.getPokemon().getUuid());
            session.clearPokemonPersonalMoveCooldown(attacker.getPokemon().getUuid(), slot);
            applyOutrageStat(session, attacker, move, attacker.level().getGameTime());
        }
        if (result == ActionBattleFightingState.HitResult.BUILT
                || result == ActionBattleFightingState.HitResult.ACTIVATED) {
            ActionBattleFightingVisuals.emitBuildup(attacker,
                    result == ActionBattleFightingState.HitResult.ACTIVATED);
        }
        return result;
    }

    public static boolean canUseAbility(ActionBattleSession session, Pokemon pokemon,
                                        Move move, long currentTick) {
        return session != null && pokemon != null && move != null
                && ActionBattleFightingController.global().canUseAbility(
                session.dungeonSessionId(), pokemon.getUuid(), move.getName(), currentTick);
    }

    public static long sharedCooldownTicks(ActionBattleSession session, PokemonEntity caster,
                                           int moveSlot, long currentTick) {
        if (session == null || caster == null) return ActionBattleFightingRules.NORMAL_OUTRAGE_SHARED_COOLDOWN_TICKS;
        Move move = moveSlot >= 0 ? caster.getPokemon().getMoveSet().get(moveSlot) : null;
        return ActionBattleFightingController.global().sharedCooldownTicks(
                session.dungeonSessionId(), caster.getPokemon().getUuid(),
                move != null ? move.getName() : "", isFightingHolder(caster.getPokemon()), currentTick);
    }

    public static boolean consumeActivationCooldownSuppression(
            ActionBattleSession session, PokemonEntity caster, int moveSlot, long currentTick) {
        if (session == null || caster == null || moveSlot < 0) return false;
        Move move = caster.getPokemon().getMoveSet().get(moveSlot);
        return move != null && ActionBattleFightingController.global()
                .consumeActivationCooldownSuppression(session.dungeonSessionId(),
                        caster.getPokemon().getUuid(), move.getName(), moveSlot, currentTick);
    }

    public static double outgoingDamageMultiplier(PokemonEntity attacker, Move move, long currentTick) {
        ActionBattleSession session = session(attacker);
        if (session == null || move == null) return 1.0D;
        return ActionBattleFightingController.global().outgoingDamageMultiplier(
                session.dungeonSessionId(), attacker.getPokemon().getUuid(), move.getName(),
                isFightingHolder(attacker.getPokemon()), currentTick);
    }

    public static double normalLocomotionMultiplier(ActionBattleSession session,
                                                     UUID pokemonId, long currentTick) {
        return session == null ? 1.0D : ActionBattleFightingController.global()
                .normalLocomotionMultiplier(session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static void onPokemonUnavailable(ActionBattleSession session, UUID pokemonId, long currentTick) {
        if (session != null) {
            clearOutrageStat(session, pokemonId, currentTick);
            ActionBattleFightingController.global().onPokemonUnavailable(
                    session.dungeonSessionId(), pokemonId, currentTick);
        }
    }

    public static Optional<ActionBattleFightingState.View> view(
            UUID sessionId, UUID pokemonId, long currentTick) {
        return ActionBattleFightingController.global().view(sessionId, pokemonId, currentTick);
    }

    public static void tickPokemon(ActionBattleSession session, PokemonEntity pokemon, long currentTick) {
        if (session == null || pokemon == null) return;
        Optional<ActionBattleFightingState.View> view = view(
                session.dungeonSessionId(), pokemon.getPokemon().getUuid(), currentTick);
        if (view.isEmpty() || !view.orElseThrow().outrageActive()) {
            clearOutrageStat(session, pokemon.getPokemon().getUuid(), currentTick);
        }
        view.ifPresent(value -> ActionBattleFightingVisuals.tick(pokemon, value, currentTick));
    }

    private static void applyOutrageStat(ActionBattleSession session, PokemonEntity attacker,
                                         Move move, long currentTick) {
        ActionBattleFightingRules.OutrageStatPlan plan = ActionBattleFightingRules.outrageStatPlan(
                FightOrFlightAdapter.movePower(move) > 0, FightOrFlightAdapter.isSpecialDamageCategory(move),
                isFightingHolder(attacker.getPokemon()));
        if (plan.stat() == ActionBattleFightingRules.OutrageStat.NONE) return;
        ActionBattleStat stat = plan.stat() == ActionBattleFightingRules.OutrageStat.SPECIAL_ATTACK
                ? ActionBattleStat.SPECIAL_ATTACK : ActionBattleStat.ATTACK;
        ActionBattleEffectController.global().applyBoundedStatContribution(
                session.battleId(), attacker.getPokemon().getUuid(), stat, plan.stages(), currentTick,
                ActionBattleFightingRules.OUTRAGE_DURATION_TICKS, ActionBattleStatSource.FIGHTING_OUTRAGE);
    }

    private static void clearOutrageStat(ActionBattleSession session, UUID pokemonId, long currentTick) {
        ActionBattleEffectController.global().clearStatContributionsFromSource(
                session.battleId(), pokemonId, ActionBattleStatSource.FIGHTING_OUTRAGE, currentTick);
    }

    public static boolean isFightingMove(Move move) {
        return move != null && move.getType() != null
                && "fighting".equalsIgnoreCase(move.getType().getName());
    }

    public static boolean isFightingHolder(Pokemon pokemon) {
        return ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemon, "fighting");
    }

    private static ActionBattleSession session(PokemonEntity entity) {
        if (entity == null || entity.isRemoved() || entity.level().isClientSide
                || !DungeonSession.isActive()) return null;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(entity.getUUID());
        return session != null && session.dungeonSessionId().equals(DungeonSession.getSessionId()) ? session : null;
    }

    private static int moveSlot(Pokemon pokemon, Move move) {
        if (pokemon == null || move == null) return -1;
        String wanted = ActionBattleFightingRules.normalizeMoveId(move.getName());
        for (int slot = 0; slot < 4; slot++) {
            Move candidate = pokemon.getMoveSet().get(slot);
            if (candidate == move || candidate != null && wanted.equals(
                    ActionBattleFightingRules.normalizeMoveId(candidate.getName()))) return slot;
        }
        return -1;
    }
}
