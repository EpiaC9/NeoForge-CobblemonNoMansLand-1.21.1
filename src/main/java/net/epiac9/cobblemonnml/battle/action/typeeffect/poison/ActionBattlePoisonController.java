package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattlePoisonController {
    private ActionBattlePoisonController() {}

    public static boolean onSuccessfulEnemyInteraction(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || target == null || !isQualifyingPoisonMove(move)) return false;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return false;
        return applyPoisonFromMove(target, battleId, target.level().getGameTime(), target.getRandom().nextDouble());
    }

    public static boolean applyPoisonFromMove(PokemonEntity target, UUID battleId, long currentTick, double penetrationRoll) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null || target == null || battleId == null || currentTick < 0L) return false;
        Pokemon pokemon = target.getPokemon();
        if (!canReceivePoison(hasType(pokemon, "steel"))) return false;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        boolean active = controller.poisonView(sessionId, pokemon.getUuid(), currentTick).isPresent();
        if (!active) {
            double chance = freshPenetrationChance(ActionBattleProtectController.global(), battleId,
                    pokemon.getUuid(), currentTick);
            if (!passesPenetration(chance, penetrationRoll)) return false;
            return controller.applyPoisonMove(sessionId, pokemon.getUuid(), currentTick,
                    hasType(pokemon, "poison"), ActionBattlePoisonRules.BASE_MOVE_GAIN);
        }
        int baseGain = controller.poisonMoveAccumulationGain(sessionId, pokemon.getUuid());
        int penetratedGain = penetratedDirectGain(ActionBattleProtectController.global(), battleId,
                pokemon.getUuid(), currentTick, baseGain);
        return penetratedGain > 0 && controller.applyPoisonMove(sessionId, pokemon.getUuid(), currentTick,
                hasType(pokemon, "poison"), penetratedGain);
    }

    public static float modifyDamage(PokemonEntity attacker, LivingEntity target, Move move, float damage) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null || attacker == null || !(target instanceof PokemonEntity pokemonTarget)
                || move == null || !(damage > 0.0F) || !isPoisonMove(move)) return damage;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        return (float) controller.modifyDamage(sessionId, pokemonTarget.getPokemon().getUuid(),
                false, false, true, damage, attacker.level().getGameTime());
    }

    public static boolean isQualifyingPoisonMove(Move move) {
        if (!isPoisonMove(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move)) return false;
        return FightOrFlightAdapter.isNativeDamageMove(move)
                || (FightOrFlightAdapter.movePower(move) == 0
                && ActionBattleFairyController.isEnemyTargetCategory(FightOrFlightAdapter.moveTargetCategory(move)));
    }

    public static boolean canReceivePoison(boolean steelTyped) { return !steelTyped; }

    public static boolean passesPenetration(double chance, double roll) {
        return Double.isFinite(chance) && Double.isFinite(roll) && chance > 0.0D && roll >= 0.0D
                && roll < Math.min(1.0D, chance);
    }

    public static int penetratedDirectGain(int baseGain, double multiplier) {
        if (baseGain <= 0 || !Double.isFinite(multiplier) || !(multiplier > 0.0D)) return 0;
        return Math.max(1, (int) Math.round(baseGain * multiplier));
    }

    public static double freshPenetrationChance(ActionBattleProtectController protect, UUID battleId,
                                                UUID pokemonUUID, long currentTick) {
        return protect != null ? protect.effectPenetrationChance(battleId, pokemonUUID, currentTick) : 1.0D;
    }

    public static int penetratedDirectGain(ActionBattleProtectController protect, UUID battleId, UUID pokemonUUID,
                                           long currentTick, int baseGain) {
        double multiplier = protect != null
                ? protect.effectPenetrationMultiplier(battleId, pokemonUUID, currentTick) : 1.0D;
        return penetratedDirectGain(baseGain, multiplier);
    }

    private static boolean isPoisonMove(Move move) {
        return move != null && move.getType() != null && "poison".equals(normalize(move.getType().getName()));
    }

    private static boolean hasType(Pokemon pokemon, String expected) {
        if (pokemon == null || expected == null) return false;
        String normalized = normalize(expected);
        if (pokemon.getPrimaryType() != null && normalized.equals(normalize(pokemon.getPrimaryType().getName()))) return true;
        return pokemon.getSecondaryType() != null && normalized.equals(normalize(pokemon.getSecondaryType().getName()));
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT).replace(" ", "") : "";
    }
}
