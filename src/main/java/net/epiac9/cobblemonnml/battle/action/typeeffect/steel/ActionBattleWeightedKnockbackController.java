package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSwapTransitionGuard;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleWeightedKnockbackController {
    private static final double STEP = 0.30D;
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private ActionBattleWeightedKnockbackController() {}

    public static boolean start(PokemonEntity attacker, PokemonEntity target, int resolvedDamage, long tick) {
        ActionBattleSession session = attacker != null ? ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID()) : null;
        if (session == null || target == null || target.isRemoved()) return false;
        Vec3 direction = target.getBoundingBox().getCenter().subtract(attacker.getBoundingBox().getCenter());
        if (direction.lengthSqr() < 0.000001D) direction = attacker.getLookAngle();
        if (direction.lengthSqr() < 0.000001D) return false;
        target.getNavigation().stop();
        ACTIVE.put(target.getPokemon().getUuid(), new Active(session, target, direction.normalize(),
                new ActionBattleWeightedKnockbackState(ActionBattleSteelRules.WEIGHTED_KNOCKBACK_BLOCKS, resolvedDamage)));
        DebugLog.log("[CobblemonNML] Weighted knockback started, intended distance=3.0, target=" + target.getPokemon().getUuid());
        return true;
    }

    public static void tick(ServerLevel level) {
        if (level == null || ACTIVE.isEmpty()) return;
        for (var iterator = ACTIVE.entrySet().iterator(); iterator.hasNext();) {
            Active active = iterator.next().getValue();
            PokemonEntity target = active.target();
            if (target.isRemoved() || !target.isAlive() || target.level() != level
                    || ActionBattleSwapTransitionGuard.rejectsHit(active.session().battleId(), target.getPokemon().getUuid())) {
                iterator.remove();
                continue;
            }
            double stepLength = Math.min(STEP, active.state().remainingDistance());
            Vec3 before = target.position();
            target.move(MoverType.SELF, active.direction().scale(stepLength));
            double actual = target.position().distanceTo(before);
            boolean blocked = actual + 0.01D < stepLength || target.horizontalCollision || target.verticalCollision;
            ActionBattleWeightedKnockbackState.Result result = active.state().advance(actual, blocked);
            if (result == ActionBattleWeightedKnockbackState.Result.NONE) continue;
            iterator.remove();
            target.setDeltaMovement(Vec3.ZERO);
            if (result == ActionBattleWeightedKnockbackState.Result.COLLISION) resolveCollision(active, level.getGameTime());
            else DebugLog.log("[CobblemonNML] Weighted knockback completed without collision. target=" + target.getPokemon().getUuid());
        }
    }

    private static void resolveCollision(Active active, long tick) {
        PokemonEntity target = active.target();
        int before = target.getPokemon().getCurrentHealth();
        int damage = active.state().followupDamage();
        if (damage > 0) target.getPokemon().setCurrentHealth(Math.max(0, before - damage));
        if (damage > 0) {
            ActionBattleDamageFeedbackController.global().recordDamage(active.session().battleId(),
                    target.getPokemon().getUuid(), before, target.getPokemon().getCurrentHealth(),
                    ActionBattleDamageFeedbackCategory.NORMAL);
            ActionBattleGhostRuntime.global().onDamageResolved(target, before);
        }
        ActionBattleInterruptController.apply(active.session(), target,
                ActionBattleSteelRules.WEIGHTED_COLLISION_INTERRUPT_TICKS, tick);
        DebugLog.log("[CobblemonNML] Weighted collision detected. damage=" + damage + ", interrupt=30");
    }

    public static void clearPokemon(UUID pokemonId) { if (pokemonId != null) ACTIVE.remove(pokemonId); }
    public static void clearBattle(UUID battleId) { if (battleId != null) ACTIVE.entrySet().removeIf(e -> e.getValue().session().battleId().equals(battleId)); }
    public static void clearAll() { ACTIVE.clear(); }
    private record Active(ActionBattleSession session, PokemonEntity target, Vec3 direction,
                          ActionBattleWeightedKnockbackState state) {}
}
