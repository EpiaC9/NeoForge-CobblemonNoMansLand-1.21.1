package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattleFireController {
    private ActionBattleFireController() {}

    public static void onSuccessfulMoveHit(PokemonEntity attacker, PokemonEntity target, Move move, double pressureAmount) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || attacker == null || target == null || move == null || !(pressureAmount > 0.0D)
                || !FightOrFlightAdapter.isNativeDamageMove(move) || !isFireMove(attacker, move)) return;
        Pokemon targetPokemon = target.getPokemon();
        boolean fireTyped = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(target, "fire");
        boolean waterTyped = ActionBattleTypeMechanicIdentity.hasActualType(targetPokemon, "water");
        if (ActionBattleFireRules.targetInteraction(fireTyped, waterTyped) == ActionBattleFireRules.TargetInteraction.IMMUNE) return;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        long currentTick = target.level().getGameTime();
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID()), target, currentTick)) return;
        double appliedPressure = penetratedPressure(ActionBattleProtectController.global(), battleId,
                targetPokemon.getUuid(), currentTick, pressureAmount);
        if (!(appliedPressure > 0.0D)) return;
        boolean hazeActive = ActionBattleEffectController.global().hasHaze(battleId, targetPokemon.getUuid(), currentTick);
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        controller.applyFirePressure(sessionId, targetPokemon.getUuid(), appliedPressure, currentTick, fireTyped, hazeActive);
    }

    public static float modifyDamage(PokemonEntity attacker, LivingEntity target, Move move, float damage) {
        UUID sessionId = activeSessionId();
        if (sessionId == null || attacker == null || !(target instanceof PokemonEntity pokemonTarget) || move == null
                || !(damage > 0.0F) || !isFireMove(attacker, move)) return damage;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        return (float) controller.modifyDamage(sessionId, pokemonTarget.getPokemon().getUuid(), true, damage,
                attacker.level().getGameTime());
    }

    public static double penetratedPressure(ActionBattleProtectController protect, UUID battleId, UUID pokemonUUID,
                                            long currentTick, double pressure) {
        if (protect == null || !(pressure > 0.0D)) return Math.max(0.0D, pressure);
        return pressure * protect.effectPenetrationMultiplier(battleId, pokemonUUID, currentTick);
    }

    private static UUID activeSessionId() {
        return DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
    }

    private static boolean isFireMove(PokemonEntity attacker, Move move) {
        return "fire".equals(normalize(ActionBattleEffectiveMoveTypeResolver.resolve(attacker, move)));
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }
}
