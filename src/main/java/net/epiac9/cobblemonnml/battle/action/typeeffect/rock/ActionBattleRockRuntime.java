package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleStatResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import java.util.UUID;

public final class ActionBattleRockRuntime {
    private ActionBattleRockRuntime() {}
    public static boolean onMoveCommitted(PokemonEntity caster, Move move) {
        if (caster == null || !ActionBattleRockMoveRules.qualifies(move)) return false;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(caster.getUUID());
        if (battleId == null) return false;
        Pokemon pokemon = caster.getPokemon();
        long tick = caster.level().getGameTime();
        ActionBattleRockSelection selection = ActionBattleRockSelection.choose(
                pokemon.getDefence(), stage(battleId, pokemon, ActionBattleStat.DEFENSE, tick),
                pokemon.getSpecialDefence(), stage(battleId, pokemon, ActionBattleStat.SPECIAL_DEFENSE, tick),
                pokemon.getAttack(), stage(battleId, pokemon, ActionBattleStat.ATTACK, tick),
                pokemon.getSpecialAttack(), stage(battleId, pokemon, ActionBattleStat.SPECIAL_ATTACK, tick),
                caster.getRandom().nextBoolean(), caster.getRandom().nextBoolean());
        return ActionBattleRockController.global().applyStockpile(battleId, pokemon.getUuid(), selection,
                ActionBattleFairyController.hasType(pokemon, "rock"), tick) == ActionBattleRockState.ApplyResult.APPLIED;
    }
    public static HitResult resolveDirectHit(PokemonEntity attacker, PokemonEntity target, int beforeHp,
                                             int incomingDamage, boolean hitSucceeded, boolean protectParticipated) {
        if (attacker == null || target == null || !hitSucceeded) return HitResult.NONE;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return HitResult.NONE;
        long tick = target.level().getGameTime();
        var endurance = ActionBattleRockController.global().resolveLethal(battleId, target.getPokemon().getUuid(),
                beforeHp, incomingDamage, protectParticipated, tick);
        if (endurance.consumed()) target.getPokemon().setCurrentHealth(1);
        int afterHp = target.getPokemon().getCurrentHealth();
        ActionBattleRockController.ProcResult proc = ActionBattleRockController.global().onDamage(
                battleId, target.getPokemon().getUuid(),
                Math.max(0, beforeHp - afterHp), afterHp, ActionBattleDamageOrigin.DIRECT_MOVE,
                endurance.consumed(), tick);
        if (ActionBattleRockVisualRules.shouldEmitProcBurst(proc)) ActionBattleRockVisuals.emitProcBurst(target);
        if (endurance.consumed()) {
            ActionBattleRockVisuals.emitEnduranceConsumed(target, endurance.reflectedDamage() > 0);
        }
        return new HitResult(endurance.consumed(), endurance.reflectedDamage());
    }
    public static void applyReflection(PokemonEntity attacker, HitResult result) {
        if (attacker == null || result == null || result.reflectedDamage() <= 0) return;
        int before = attacker.getPokemon().getCurrentHealth();
        int after = Math.max(0, before - result.reflectedDamage());
        attacker.getPokemon().setCurrentHealth(after);
        ActionBattleGhostRuntime.global().onDamageResolved(attacker, before);
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(
                battleId, attacker.getPokemon().getUuid(), before, after, ActionBattleDamageFeedbackCategory.REFLECTED);
    }
    private static int stage(UUID battle, Pokemon pokemon, ActionBattleStat stat, long tick) {
        return ActionBattleStatResolver.effectiveStage(battle, pokemon.getUuid(), stat, tick);
    }
    public record HitResult(boolean enduranceConsumed, int reflectedDamage) {
        public static final HitResult NONE = new HitResult(false, 0);
    }
}
