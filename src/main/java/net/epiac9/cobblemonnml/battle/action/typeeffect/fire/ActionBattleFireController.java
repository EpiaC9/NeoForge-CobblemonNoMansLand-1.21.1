package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

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

public final class ActionBattleFireController {
    private ActionBattleFireController() {}

    public static void onSuccessfulMoveHit(PokemonEntity attacker, PokemonEntity target, Move move, double pressureAmount) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || attacker == null || target == null || move == null || !(pressureAmount > 0.0D)
                || !FightOrFlightAdapter.isNativeDamageMove(move) || !isFireMove(move)) return;
        Pokemon targetPokemon = target.getPokemon();
        boolean fireTyped = hasType(targetPokemon, "fire");
        boolean waterTyped = hasType(targetPokemon, "water");
        if (ActionBattleFireRules.targetInteraction(fireTyped, waterTyped) == ActionBattleFireRules.TargetInteraction.IMMUNE) return;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        long currentTick = target.level().getGameTime();
        double penetration = ActionBattleProtectController.global()
                .effectPenetrationMultiplier(battleId, targetPokemon.getUuid(), currentTick);
        double appliedPressure = pressureAmount * penetration;
        if (!(appliedPressure > 0.0D)) return;
        boolean hazeActive = ActionBattleEffectController.global().hasHaze(battleId, targetPokemon.getUuid(), currentTick);
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        controller.applyFirePressure(sessionId, targetPokemon.getUuid(), appliedPressure, currentTick, fireTyped, hazeActive);
    }

    public static float modifyDamage(PokemonEntity attacker, LivingEntity target, Move move, float damage) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || attacker == null || !(target instanceof PokemonEntity pokemonTarget) || move == null
                || !(damage > 0.0F) || !isFireMove(move)) return damage;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        return (float) controller.modifyDamage(sessionId, pokemonTarget.getPokemon().getUuid(), true, damage,
                attacker.level().getGameTime());
    }

    private static UUID activeSessionId() {
        return DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
    }

    private static boolean isFireMove(Move move) {
        return move.getType() != null && "fire".equals(normalize(move.getType().getName()));
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
