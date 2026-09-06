package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveParameters;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveServerRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.mixin.ActionBattleLivingEntityAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleGroundController {
    private ActionBattleGroundController() {}

    public static boolean qualifies(String moveType, ActionMoveDeliveryType deliveryType) {
        return ActionBattleGroundRules.qualifies(moveType, deliveryType);
    }

    public static boolean isQualifyingMove(Move move) {
        return move != null && move.getType() != null && qualifies(move.getType().getName(),
                ActionProjectileProfile.deliveryType(move.getName()));
    }

    public static int depthPercent(PokemonEntity target, long currentTick) {
        if (target == null || currentTick < 0L) return 0;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null) return 0;
        return ActionBattleTypeEffectController.global().groundView(
                session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick)
                .map(ActionBattleGroundState.View::depthPercent).orElse(0);
    }

    public static AABB effectiveCombatBox(PokemonEntity target, long currentTick, boolean buriedAware) {
        if (target == null) return null;
        int depthPercent = depthPercent(target, currentTick);
        if (depthPercent <= 0) return target.getBoundingBox();
        ActionBattleGroundCollision.Box original = box(target.getBoundingBox());
        ActionBattleGroundCollision.Box effective = buriedAware
                ? ActionBattleGroundCollision.buriedAwareBox(original, depthPercent)
                : ActionBattleGroundCollision.combatBox(original, depthPercent);
        return box(effective);
    }

    public static boolean segmentIntersects(AABB box, Vec3 start, Vec3 end) {
        return box != null && ActionBattleGroundCollision.segmentIntersects(box(box), point(start), point(end));
    }

    private static ActionBattleGroundCollision.Box box(AABB value) {
        return new ActionBattleGroundCollision.Box(value.minX, value.minY, value.minZ,
                value.maxX, value.maxY, value.maxZ);
    }

    private static AABB box(ActionBattleGroundCollision.Box value) {
        return new AABB(value.minX(), value.minY(), value.minZ(), value.maxX(), value.maxY(), value.maxZ());
    }

    private static ActionBattleGroundCollision.Point point(Vec3 value) {
        return new ActionBattleGroundCollision.Point(value.x, value.y, value.z);
    }

    public static HitPlan planHit(boolean qualifying, boolean targetFlyingTyped, boolean targetAtNinety,
                                  boolean attackerGroundTyped, ActionBattleGroundState.Branch targetBranch) {
        if (!qualifying || targetFlyingTyped) return HitPlan.NOT_QUALIFYING;
        if (!targetAtNinety) return new HitPlan(true, false, 1.0D, targetBranch);
        return new HitPlan(true, true,
                ActionBattleGroundRules.expelDamageMultiplier(attackerGroundTyped), targetBranch);
    }

    public static HitPlan planHit(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || target == null || !isQualifyingMove(move)) return HitPlan.NOT_QUALIFYING;
        if (hasFlyingType(target)) return HitPlan.NOT_QUALIFYING;
        long currentTick = attacker.level().getGameTime();
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        ActionBattleGroundState.View view = session == null ? null
                : ActionBattleTypeEffectController.global().groundView(
                session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick).orElse(null);
        boolean targetGroundTyped = hasGroundType(target);
        ActionBattleGroundState.Branch branch = view != null ? view.branch()
                : targetGroundTyped ? ActionBattleGroundState.Branch.DIG : ActionBattleGroundState.Branch.SINK;
        return planHit(true, false, view != null && view.depthPercent() == 90, hasGroundType(attacker), branch);
    }

    public static Resolution resolveAfterDamage(HitPlan plan, int actualDamage, Runnable advance,
                                                Runnable clear, Runnable aftermath) {
        if (plan == null || !plan.qualifying() || actualDamage <= 0) return Resolution.NONE;
        if (!plan.expels()) {
            advance.run();
            return Resolution.ADVANCED;
        }
        clear.run();
        if (plan.targetBranch() == ActionBattleGroundState.Branch.DIG) {
            aftermath.run();
            return Resolution.EXPELLED_WITH_AFTERMATH;
        }
        return Resolution.EXPELLED;
    }

    public static Resolution resolveAfterDamage(HitPlan plan, PokemonEntity attacker,
                                                PokemonEntity target, int beforeHp) {
        if (plan == null || attacker == null || target == null) return Resolution.NONE;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null) return Resolution.NONE;
        long currentTick = attacker.level().getGameTime();
        int actualDamage = Math.max(0, beforeHp - target.getPokemon().getCurrentHealth());
        return resolveAfterDamage(plan, actualDamage, () -> {
            ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
            effects.applyGround(session.dungeonSessionId(), target.getPokemon().getUuid(),
                    currentTick, plan.targetBranch() == ActionBattleGroundState.Branch.DIG);
            int depth = effects.groundView(session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick)
                    .map(ActionBattleGroundState.View::depthPercent).orElse(0);
            ActionBattleGroundVisualSync.update(target, session.dungeonSessionId(), depth);
            if (effects.groundBlocksMovement(session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick)) {
                target.getNavigation().stop();
                ActionBattleCommandController.cancelPendingOrders(session, target.getPokemon().getUuid(),
                        ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
                ActionBattleConfusionController.cancelMeleeDash(target.getPokemon().getUuid());
            }
        }, () -> {
            if (ActionBattleTypeEffectController.global().expelGround(
                    session.dungeonSessionId(), target.getPokemon().getUuid())) {
                ActionBattleGroundVisualSync.update(target, session.dungeonSessionId(), 0);
            }
        },
                () -> launchExpelWave(session.dungeonSessionId(), target));
    }

    public static int radialDamagePerTarget(int emitterMaximumHealth, int targetCount) {
        return ActionBattleGroundRules.radialDamage(emitterMaximumHealth);
    }

    public static RadialResolution resolveRadialHit(boolean targetAtNinety,
                                                    ActionBattleGroundState.Branch targetBranch,
                                                    Runnable damage, Runnable clear, Runnable childWave) {
        damage.run();
        if (!targetAtNinety) return RadialResolution.DAMAGED;
        clear.run();
        if (targetBranch == ActionBattleGroundState.Branch.DIG) {
            childWave.run();
            return RadialResolution.EXPELLED_WITH_CHILD;
        }
        return RadialResolution.EXPELLED;
    }

    public static boolean launchExpelWave(java.util.UUID sessionId, PokemonEntity emitter) {
        if (sessionId == null || emitter == null || emitter.isRemoved()
                || !(emitter.level() instanceof ServerLevel level)) return false;
        int fixedDamage = radialDamagePerTarget(emitter.getPokemon().getMaxHealth(), 1);
        if (fixedDamage <= 0) return false;
        ActionBattleWaveServerRuntime.launch(sessionId, emitter.getPokemon().getUuid(), emitter.position(),
                level.getGameTime(), new ActionBattleWaveParameters(ActionProjectileProfile.WAVE_AREA_SPEED, 10.0D),
                (waveLevel, target) -> resolveRadialHit(sessionId, target, fixedDamage));
        return true;
    }

    private static RadialResolution resolveRadialHit(java.util.UUID sessionId, PokemonEntity target, int fixedDamage) {
        long currentTick = target.level().getGameTime();
        ActionBattleGroundState.View view = ActionBattleTypeEffectController.global().groundView(
                sessionId, target.getPokemon().getUuid(), currentTick).orElse(null);
        boolean fullyBuried = view != null && view.depthPercent() == 90;
        ActionBattleGroundState.Branch branch = view != null ? view.branch() : null;
        return resolveRadialHit(fullyBuried, branch,
                () -> ActionBattlePokemonHealth.damage(healthAccess(target), fixedDamage),
                () -> {
                    if (ActionBattleTypeEffectController.global().expelGround(
                            sessionId, target.getPokemon().getUuid())) {
                        ActionBattleGroundVisualSync.update(target, sessionId, 0);
                    }
                },
                () -> launchExpelWave(sessionId, target));
    }

    private static ActionBattlePokemonHealth.Access healthAccess(PokemonEntity entity) {
        var pokemon = entity.getPokemon();
        return new ActionBattlePokemonHealth.Access() {
            @Override public int currentHealth() { return pokemon.getCurrentHealth(); }
            @Override public int maxHealth() { return pokemon.getMaxHealth(); }
            @Override public boolean deployed() { return !entity.isRemoved(); }
            @Override public float liveMaxHealth() { return entity.getMaxHealth(); }
            @Override public void setCurrentHealth(int value) { pokemon.setCurrentHealth(value); }
            @Override public void setLiveHealth(float value) {
                if (entity.isRemoved()) return;
                entity.setHealth(value);
                if (value > 0) {
                    entity.deathTime = 0;
                    ((ActionBattleLivingEntityAccessor) entity).cobblemonNml$setDead(false);
                }
            }
        };
    }

    private static boolean hasGroundType(PokemonEntity pokemon) {
        if (pokemon == null || pokemon.getPokemon() == null) return false;
        var value = pokemon.getPokemon();
        return (value.getPrimaryType() != null && "ground".equalsIgnoreCase(value.getPrimaryType().getName()))
                || (value.getSecondaryType() != null && "ground".equalsIgnoreCase(value.getSecondaryType().getName()));
    }

    private static boolean hasFlyingType(PokemonEntity pokemon) {
        if (pokemon == null || pokemon.getPokemon() == null) return false;
        var value = pokemon.getPokemon();
        return ActionBattleGroundRules.isFlyingTyped(
                value.getPrimaryType() != null ? value.getPrimaryType().getName() : null,
                value.getSecondaryType() != null ? value.getSecondaryType().getName() : null);
    }

    public record HitPlan(boolean qualifying, boolean expels, double damageMultiplier,
                          ActionBattleGroundState.Branch targetBranch) {
        public static final HitPlan NOT_QUALIFYING = new HitPlan(false, false, 1.0D, null);
    }

    public enum Resolution { NONE, ADVANCED, EXPELLED, EXPELLED_WITH_AFTERMATH }
    public enum RadialResolution { DAMAGED, EXPELLED, EXPELLED_WITH_CHILD }
}
