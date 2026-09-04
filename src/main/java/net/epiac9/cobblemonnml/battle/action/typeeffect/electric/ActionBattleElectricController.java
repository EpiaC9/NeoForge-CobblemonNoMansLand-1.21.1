package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattleElectricController {
    public enum InteractionResult {
        IGNORED, CHARGE_APPLIED, PARALYSIS_STARTED, PARALYSIS_ACCUMULATED, FLINCH_TRIGGERED
    }

    private ActionBattleElectricController() {}

    public static boolean applyExternalParalysis(ActionBattleTypeEffectController effects, UUID sessionId,
                                                   UUID pokemonUUID, long currentTick,
                                                   boolean electricTyped, boolean hazeActive) {
        return effects != null && effects.applyExternalElectricParalysis(sessionId, pokemonUUID,
                currentTick, electricTyped, hazeActive);
    }

    public static InteractionResult applySuccessfulInteraction(
            ActionBattleTypeEffectController effects, ActionBattleProtectController protect,
            UUID sessionId, UUID battleId, UUID pokemonUUID, long currentTick,
            boolean groundTyped, boolean rockTyped, boolean electricTyped, boolean hazeActive,
            boolean interactionSucceeded, int suppliedCharge, int suppliedFlinch) {
        if (effects == null || sessionId == null || battleId == null || pokemonUUID == null
                || currentTick < 0L || !interactionSucceeded || groundTyped || rockTyped) return InteractionResult.IGNORED;
        if (effects.electricParalysisView(sessionId, pokemonUUID, currentTick).isPresent()) {
            if (protect != null && protect.effectPenetrationMultiplier(battleId, pokemonUUID, currentTick) <= 0.0D) {
                return InteractionResult.IGNORED;
            }
            if (suppliedFlinch <= 0) return InteractionResult.IGNORED;
            return flinchResult(effects.addElectricParalysisFlinch(sessionId, pokemonUUID, suppliedFlinch, currentTick));
        }
        int amount = penetratedCharge(protect, battleId, pokemonUUID, currentTick, suppliedCharge);
        if (amount <= 0) return InteractionResult.IGNORED;
        return switch (effects.addElectricCharge(sessionId, pokemonUUID, amount, currentTick, electricTyped, hazeActive)) {
            case CHARGE_APPLIED -> InteractionResult.CHARGE_APPLIED;
            case PARALYSIS_STARTED -> InteractionResult.PARALYSIS_STARTED;
            case IGNORED -> InteractionResult.IGNORED;
        };
    }

    public static InteractionResult onSuccessfulEnemyInteraction(PokemonEntity attacker, PokemonEntity target, Move move,
                                                                  int suppliedCharge, int suppliedFlinch) {
        if (attacker == null || target == null || move == null || (suppliedCharge <= 0 && suppliedFlinch <= 0)
                || !isQualifyingEnemyInteraction(move) || !DungeonSession.isActive()) return InteractionResult.IGNORED;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))
                || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return InteractionResult.IGNORED;
        Pokemon pokemon = target.getPokemon();
        long currentTick = target.level().getGameTime();
        boolean groundTyped = hasType(pokemon, "ground");
        boolean rockTyped = hasType(pokemon, "rock");
        boolean electricTyped = hasType(pokemon, "electric");
        boolean hazeActive = ActionBattleEffectController.global().hasHaze(
                session.battleId(), pokemon.getUuid(), currentTick);
        ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
        effects.guardSession(session.dungeonSessionId());
        if (effects.electricParalysisView(session.dungeonSessionId(), pokemon.getUuid(), currentTick).isPresent()) {
            if (groundTyped || rockTyped) return InteractionResult.IGNORED;
            if (ActionBattleProtectController.global().effectPenetrationMultiplier(
                    session.battleId(), pokemon.getUuid(), currentTick) <= 0.0D) return InteractionResult.IGNORED;
            return interactionResult(ActionBattleParalysisController.global().addElectricHit(
                    effects, session, target, currentTick, suppliedFlinch, FightOrFlightAdapter.makesContact(move)));
        }
        InteractionResult result = applySuccessfulInteraction(effects, ActionBattleProtectController.global(),
                session.dungeonSessionId(), session.battleId(), pokemon.getUuid(), currentTick, groundTyped, rockTyped,
                electricTyped, hazeActive, true, suppliedCharge, suppliedFlinch);
        if (result == InteractionResult.CHARGE_APPLIED || result == InteractionResult.PARALYSIS_STARTED) {
            ActionBattleElectricVisuals.emitSubtleStatic(target);
        }
        return result;
    }

    public static InteractionResult onSuccessfulEnemyInteraction(PokemonEntity attacker, PokemonEntity target, Move move) {
        ActionBattleElectricContributionSource.Contributions contributions =
                ActionBattleElectricContributionSource.forMove(move != null ? move.getName() : null);
        return onSuccessfulEnemyInteraction(attacker, target, move,
                contributions.charge(), contributions.paralysisFlinch());
    }

    public static InteractionResult onSuccessfulMoveHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                         int suppliedCharge, int suppliedFlinch) {
        if (!FightOrFlightAdapter.isNativeDamageMove(move)) return InteractionResult.IGNORED;
        return onSuccessfulEnemyInteraction(attacker, target, move, suppliedCharge, suppliedFlinch);
    }

    public static InteractionResult onSuccessfulMoveHit(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (!FightOrFlightAdapter.isNativeDamageMove(move)) return InteractionResult.IGNORED;
        return onSuccessfulEnemyInteraction(attacker, target, move);
    }

    public static int penetratedCharge(ActionBattleProtectController protect, UUID battleId,
                                       UUID pokemonUUID, long currentTick, int suppliedCharge) {
        if (suppliedCharge <= 0) return 0;
        double multiplier = protect == null ? 1.0D
                : protect.effectPenetrationMultiplier(battleId, pokemonUUID, currentTick);
        return ActionBattleElectricRules.penetratedAmount(suppliedCharge, multiplier);
    }

    private static InteractionResult flinchResult(ActionBattleParalysisState.FlinchContributionResult result) {
        return result == ActionBattleParalysisState.FlinchContributionResult.FLINCH_TRIGGERED
                ? InteractionResult.FLINCH_TRIGGERED
                : result == ActionBattleParalysisState.FlinchContributionResult.ACCUMULATED
                ? InteractionResult.PARALYSIS_ACCUMULATED : InteractionResult.IGNORED;
    }

    static boolean hasTypeNames(String primary, String secondary, String expected) {
        String normalized = normalize(expected);
        return !normalized.isEmpty() && (normalized.equals(normalize(primary)) || normalized.equals(normalize(secondary)));
    }

    public static boolean isQualifyingEnemyInteraction(Move move) {
        return move != null && isQualifyingInteraction(
                move.getType() != null ? move.getType().getName() : null,
                FightOrFlightAdapter.isNativeDamageMove(move), FightOrFlightAdapter.movePower(move),
                FightOrFlightAdapter.moveTargetCategory(move));
    }

    static boolean isQualifyingInteraction(String moveType, boolean nativeDamageMove,
                                            int movePower, String targetCategory) {
        if (!"electric".equals(normalize(moveType))) return false;
        return nativeDamageMove || (movePower == 0
                && ActionBattleFairyController.isEnemyTargetCategory(targetCategory));
    }

    private static boolean hasType(Pokemon pokemon, String expected) {
        return pokemon != null && hasTypeNames(
                pokemon.getPrimaryType() != null ? pokemon.getPrimaryType().getName() : null,
                pokemon.getSecondaryType() != null ? pokemon.getSecondaryType().getName() : null, expected);
    }

    private static InteractionResult interactionResult(ActionBattleParalysisState.FlinchContributionResult result) {
        return flinchResult(result);
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }
}
