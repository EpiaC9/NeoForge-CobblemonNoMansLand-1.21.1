package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleRangeRules;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTargetingRules;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleDarkRuntime {
    private ActionBattleDarkRuntime() {}

    public static ActionBattleDarkState.HitResult onConnectedHit(
            PokemonEntity attacker, PokemonEntity target, Move move, boolean connected) {
        if (attacker == null || target == null || move == null || attacker.level().isClientSide) {
            return ActionBattleDarkState.HitResult.IGNORED;
        }
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(
                ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) {
            return ActionBattleDarkState.HitResult.IGNORED;
        }
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                session, target, attacker.level().getGameTime())) return ActionBattleDarkState.HitResult.IGNORED;
        boolean darkMove = move.getType() != null && "dark".equalsIgnoreCase(move.getType().getName());
        boolean attackerDark = ActionBattleFairyController.hasType(attacker.getPokemon(), "dark");
        boolean targetDark = ActionBattleFairyController.hasType(target.getPokemon(), "dark");
        boolean targetPsychic = ActionBattleFairyController.hasType(target.getPokemon(), "psychic");
        ActionBattleDarkRules.HitPlan plan = ActionBattleDarkRules.planHit(
                connected, darkMove, attackerDark, targetDark, targetPsychic,
                FightOrFlightAdapter.movePower(move) > 0);
        if (!plan.qualifies()) return ActionBattleDarkState.HitResult.IGNORED;
        long currentTick = attacker.level().getGameTime();
        return ActionBattleDarkController.global().applyHit(session.dungeonSessionId(),
                target.getPokemon().getUuid(), plan.awarenessReduction(), plan.obscurityIncrease(), currentTick);
    }

    public static void tickPokemon(ActionBattleSession session, PokemonEntity pokemon, long currentTick) {
        if (session == null || pokemon == null || pokemon.isRemoved() || currentTick < 0L) return;
        ActionBattleDarkController.global().tickPokemon(session.dungeonSessionId(),
                pokemon.getPokemon().getUuid(), currentTick);
        view(session, pokemon.getPokemon().getUuid(), currentTick)
                .ifPresent(state -> ActionBattleDarkVisuals.tick(pokemon, state, currentTick));
        if (pokemon.getTarget() instanceof PokemonEntity target
                && isOpposingActivePokemon(session, pokemon, target)
                && !canPerceive(session, pokemon, target, currentTick)) {
            pokemon.setTarget(null);
            pokemon.getNavigation().stop();
        }
    }

    public static void tickState(ActionBattleSession session, UUID pokemonId, long currentTick) {
        if (session == null || pokemonId == null || currentTick < 0L) return;
        ActionBattleDarkController.global().tickPokemon(session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static Optional<ActionBattleDarkState.View> view(
            ActionBattleSession session, UUID pokemonId, long currentTick) {
        return session == null ? Optional.empty() : ActionBattleDarkController.global()
                .view(session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static double currentAwareness(ActionBattleSession session, UUID pokemonId, long currentTick) {
        return session == null ? ActionBattleDarkRules.NORMAL_AWARENESS : ActionBattleDarkController.global()
                .currentAwareness(session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static boolean canPerceive(ActionBattleSession session, PokemonEntity observer,
                                      PokemonEntity target, long currentTick) {
        if (session == null || observer == null || target == null || observer.isRemoved() || target.isRemoved()) {
            return false;
        }
        double awareness = currentAwareness(session, observer.getPokemon().getUuid(), currentTick);
        return ActionBattleTargetingRules.canPerceive(ActionBattleRangeRules.hitboxGapSquared(
                observer.getBoundingBox(), target.getBoundingBox()), awareness);
    }

    public static void onPokemonUnavailable(ActionBattleSession session, UUID pokemonId,
                                            boolean fainted, long currentTick) {
        if (session == null || pokemonId == null || currentTick < 0L) return;
        ActionBattleDarkController.global().onPokemonUnavailable(session.dungeonSessionId(), pokemonId,
                fainted, currentTick);
    }

    public static void clearPokemon(UUID sessionId, UUID pokemonId) {
        ActionBattleDarkController.global().clearPokemon(sessionId, pokemonId);
    }
    public static void clearSession(UUID sessionId) { ActionBattleDarkController.global().clearSession(sessionId); }
    public static void clearAll() { ActionBattleDarkController.global().clearAll(); }

    private static boolean isOpposingActivePokemon(ActionBattleSession session, PokemonEntity observer,
                                                    PokemonEntity target) {
        UUID observerId = observer.getUUID();
        UUID targetId = target.getUUID();
        boolean observerPlayer = false;
        boolean targetPlayer = false;
        for (UUID playerUUID : session.playerUUIDs()) {
            UUID entityUUID = session.playerActiveEntityUUID(playerUUID);
            observerPlayer |= observerId.equals(entityUUID);
            targetPlayer |= targetId.equals(entityUUID);
        }
        return observerPlayer && targetId.equals(session.trainerActiveEntityUUID())
                || observerId.equals(session.trainerActiveEntityUUID()) && targetPlayer;
    }
}
