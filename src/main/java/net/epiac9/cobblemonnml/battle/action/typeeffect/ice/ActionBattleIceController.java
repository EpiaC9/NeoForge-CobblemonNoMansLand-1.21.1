package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ActionBattleIceController {
    private ActionBattleIceController() {}

    public static void onSuccessfulMoveHit(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || target == null || move == null || !FightOrFlightAdapter.isNativeDamageMove(move)
                || !isIceMove(move)) return;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        applyIceApplication(target, target.level().getGameTime());
    }

    public static boolean applyIceApplication(PokemonEntity target, long currentTick) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || target == null || currentTick < 0L) return false;
        Pokemon pokemon = target.getPokemon();
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        double chance = penetrationChance(ActionBattleProtectController.global(), battleId, pokemon.getUuid(), currentTick);
        if (!passesPenetration(chance, ThreadLocalRandom.current().nextDouble())) return false;
        boolean hazeActive = battleId != null
                && ActionBattleEffectController.global().hasHaze(battleId, pokemon.getUuid(), currentTick);
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        return controller.applyIceApplication(sessionId, pokemon.getUuid(), currentTick, hasType(pokemon, "ice"), hazeActive);
    }

    public static float modifyDamage(PokemonEntity attacker, LivingEntity target, Move move, float damage) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || attacker == null || !(target instanceof PokemonEntity pokemonTarget) || move == null
                || !(damage > 0.0F) || !isIceMove(move)) return damage;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        return (float) controller.modifyDamage(sessionId, pokemonTarget.getPokemon().getUuid(), false, true, damage,
                attacker.level().getGameTime());
    }

    public static boolean passesPenetration(double chance, double roll) {
        return Double.isFinite(chance) && Double.isFinite(roll) && chance > 0.0D && roll >= 0.0D
                && roll < Math.min(1.0D, chance);
    }

    public static double penetrationChance(ActionBattleProtectController protect, UUID battleId, UUID pokemonUUID,
                                           long currentTick) {
        return protect != null ? protect.effectPenetrationChance(battleId, pokemonUUID, currentTick) : 1.0D;
    }

    private static UUID activeSessionId() {
        return DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
    }

    private static boolean isIceMove(Move move) {
        return move.getType() != null && "ice".equals(normalize(move.getType().getName()));
    }

    private static boolean hasType(Pokemon pokemon, String expected) {
        if (pokemon == null) return false;
        if (pokemon.getPrimaryType() != null && expected.equals(normalize(pokemon.getPrimaryType().getName()))) return true;
        return pokemon.getSecondaryType() != null && expected.equals(normalize(pokemon.getSecondaryType().getName()));
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }
}
