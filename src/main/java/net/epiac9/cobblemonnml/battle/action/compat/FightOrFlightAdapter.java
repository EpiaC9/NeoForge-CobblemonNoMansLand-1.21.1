package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.PokemonInterface;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatChangeMoveData;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleLineOfSight;
import net.epiac9.cobblemonnml.battle.action.ActionBattleRangeRules;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTargetTracker;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSwapTransitionGuard;
import net.epiac9.cobblemonnml.battle.action.ActionBattleEvasionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleStatResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleEarthquakeHandler;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommittedMove;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleAquaShieldProtection;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleWeightedKnockbackController;
import net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class FightOrFlightAdapter {
    private FightOrFlightAdapter() {}

    public static boolean supports(Move move) {
        return move != null && (ActionBattleBalefulBunkerHandler.isBalefulBunker(move) || ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isMeleeAttackMove(move) || PokemonUtils.isRangeAttackMove(move) || ActionBattleSteelRuntime.isQualifyingSelfBuffMove(move) || ActionBattleWaterController.isQualifyingInteraction(move) || ActionBattleGrassController.isQualifyingMove(move) || (movePower(move) == 0 && ActionBattleMoveEffectResolver.hasSupportedActionStatusMetadata(move)));
    }

    public static boolean isMeleeMove(Move move) { return move != null && PokemonUtils.isMeleeAttackMove(move); }

    public static boolean isRangedMove(Move move) {
        return move != null && (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isRangeAttackMove(move) || (!PokemonUtils.isMeleeAttackMove(move) && ActionBattleWaterController.isQualifyingInteraction(move)) || (movePower(move) == 0 && ActionBattleMoveEffectResolver.hasSupportedActionStatusMetadata(move)));
    }

    public static boolean isNativeDamageMove(Move move) {
        return move != null && (PokemonUtils.isMeleeAttackMove(move) || PokemonUtils.isRangeAttackMove(move));
    }

    public static double actionAccuracyProjectileMultiplier(PokemonEntity attacker) {
        if (attacker == null || attacker.level().isClientSide) return 1.0D;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        if (session == null) return 1.0D;
        return ActionBattleStatResolver.accuracyProjectileMultiplier(
                session.battleId(), attacker.getPokemon().getUuid(), attacker.level().getGameTime());
    }

    public static float scaleActionDamage(PokemonEntity attacker, LivingEntity target, Move move, float baseDamage) {
        return scaleActionDamage(attacker, target, move, baseDamage, 1.0D);
    }

    public static float scaleActionDamage(PokemonEntity attacker, LivingEntity target, Move move, float baseDamage,
                                          double committedGrassMultiplier) {
        if (attacker == null || !(target instanceof PokemonEntity pokemonTarget) || move == null || !(baseDamage > 0.0F)) return baseDamage;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID()))) return baseDamage;
        long tick = attacker.level().getGameTime();
        boolean special = isSpecialDamageCategory(move);
        ActionBattleStat offense = special ? ActionBattleStat.SPECIAL_ATTACK : ActionBattleStat.ATTACK;
        ActionBattleStat defense = special ? ActionBattleStat.SPECIAL_DEFENSE : ActionBattleStat.DEFENSE;
        int offenseStage = ActionBattleStatResolver.effectiveStage(battleId, attacker.getPokemon().getUuid(), offense, tick);
        int defenseStage = ActionBattleStatResolver.effectiveStage(battleId, pokemonTarget.getPokemon().getUuid(), defense, tick);
        double multiplier = ActionBattleStatRules.damageMultiplier(offenseStage, defenseStage);
        return Math.max(0.0F, (float) (baseDamage * multiplier));
    }

    private static void applyPostHitActionStatScaling(PokemonEntity attacker, PokemonEntity target, Move move, int beforeHp,
                                                       double committedGrassMultiplier, double groundMultiplier,
                                                       double ghostDamageMultiplier, ActionBattleCommittedMove committedMove) {
        if (attacker == null || target == null || move == null || beforeHp <= 0) return;
        int afterHp = target.getPokemon().getCurrentHealth();
        int baseDamage = Math.max(0, beforeHp - afterHp);
        if (baseDamage <= 0) return;
        float scaled = scaleActionDamage(attacker, target, move, baseDamage, committedGrassMultiplier);
        int scaledDamage = Math.max(1, Math.round(ActionBattleCriticalRules.apply(
                scaled, committedMove != null ? committedMove.critical() : null)));
        target.getPokemon().setCurrentHealth(Math.max(0, beforeHp - scaledDamage));
    }

    public static boolean isSpecialDamageCategory(Move move) {
        if (move == null) return false;
        Object category = invokeGetter(move, "getDamageCategory");
        if (category == null) category = invokeGetter(move, "getCategory");
        if (category == null) {
            Object template = invokeGetter(move, "getTemplate");
            if (template != null) {
                category = invokeGetter(template, "getDamageCategory");
                if (category == null) category = invokeGetter(template, "getCategory");
            }
        }
        if (category != null) {
            String id = category.toString().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
            if (id.contains("special")) return true;
            if (id.contains("physical")) return false;
        }
        return PokemonUtils.isRangeAttackMove(move) && !PokemonUtils.isMeleeAttackMove(move);
    }

    public static boolean makesContact(Move move) {
        if (move == null) return false;
        try {
            Object template = move.getClass().getMethod("getTemplate").invoke(move);
            if (template == null) return false;
            Object flags = template.getClass().getMethod("getFlags").invoke(template);
            if (!(flags instanceof Iterable<?> iterable)) return false;
            for (Object flag : iterable) {
                if (flag == null) continue;
                String value = flag.toString().replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
                if (value.equals("contact") || value.endsWith("contact")) return true;
            }
        } catch (ReflectiveOperationException ignored) {}
        return false;
    }

    public static boolean hasPp(Move move) {
        return currentPp(move) > 0;
    }

    public static int currentPp(Move move) {
        if (move == null) return 0;
        try {
            Method getter = move.getClass().getMethod("getCurrentPp");
            Object value = getter.invoke(move);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException exception) {
            return 0;
        }
    }

    public static int maxPp(Move move) {
        if (move == null) return 0;
        Integer direct = invokeIntGetter(move, "getPp");
        if (direct != null && direct > 0) return direct;
        try {
            Object template = move.getClass().getMethod("getTemplate").invoke(move);
            Integer templatePp = invokeIntGetter(template, "getPp");
            if (templatePp != null && templatePp > 0) return templatePp;
        } catch (ReflectiveOperationException ignored) {}
        return Math.max(0, currentPp(move));
    }

    public static boolean consumeOnePp(Move move) {
        int current = currentPp(move);
        if (move == null || current <= 0) return false;
        return setCurrentPp(move, current - 1);
    }

    public static boolean refundOnePp(Move move) {
        if (move == null) return false;
        return setCurrentPp(move, currentPp(move) + 1);
    }

    private static boolean setCurrentPp(Move move, int value) {
        try {
            Method setter = move.getClass().getMethod("setCurrentPp", int.class);
            setter.invoke(move, Math.max(0, value));
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static long cooldownTicks(Move move) {
        return net.epiac9.cobblemonnml.battle.action.ActionBattleAbilityCooldownRules.normal().sharedTicks();
    }

    public static boolean consumeOnePp(PokemonEntity caster, Move move) {
        ActionBattleTypeMechanicIdentity.logCommitSnapshot(caster);
        if (!consumeOnePp(move)) return false;
        ActionBattleGhostRuntime.global().onPpConsumed(caster, 1);
        UUID battleId = caster != null ? ActionBattleManager.battleIdForPokemonEntity(caster.getUUID()) : null;
        if (battleId != null) ActionBattleGhostRuntime.global().onAffectedMoveCommitted(
                battleId, caster.getPokemon().getUuid(), caster.level().getGameTime());
        return true;
    }

    public static boolean canCommitHail(PokemonEntity attacker, LivingEntity target) {
        if (attacker == null || target == null || !target.isAlive()) return false;
        if (!hasActionLineOfSight(attacker, target)) return false;
        return ActionBattleRangeRules.withinHitboxRange(attacker.getBoundingBox(), target.getBoundingBox(),
                ActionBattleRangeRules.DEFAULT_RANGED_EXECUTION_RANGE);
    }

    public static boolean hasActionLineOfSight(PokemonEntity attacker, LivingEntity target) {
        if (attacker == null || target == null || !target.isAlive()) return false;
        if (!(target instanceof PokemonEntity pokemonTarget)) return attacker.getSensing().hasLineOfSight(target);
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        if (session == null || session != ActionBattleManager.findSessionForBattlePokemonEntity(pokemonTarget.getUUID())) {
            return attacker.getSensing().hasLineOfSight(target);
        }
        var visibility = ActionBattleLineOfSight.evaluate(session, attacker, pokemonTarget);
        ActionBattleTargetTracker.global().observe(session.battleId(), attacker.getPokemon().getUuid(),
                pokemonTarget.getPokemon().getUuid(),
                new net.epiac9.cobblemonnml.battle.action.ActionBattleTargetingRules.Point(
                        pokemonTarget.getX(), pokemonTarget.getY(), pokemonTarget.getZ()), visibility);
        return visibility.visible();
    }

    public static boolean canCommit(PokemonEntity attacker, LivingEntity target, Move move) {
        if (attacker == null || move == null || !supports(move)) return false;
        if (ActionBattleEarthquakeHandler.isEarthquake(move)) {
            return ActionBattleEarthquakeHandler.canLaunch(attacker);
        }
        LivingEntity executionTarget = resolveMoveTarget(attacker, target, move);
        if (executionTarget == null || !executionTarget.isAlive()) return false;
        target = executionTarget;
        if (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move)) return canCommitHail(attacker, target);
        if (!isSelfOrAllyTargetCategory(moveTargetCategory(move)) && !hasActionLineOfSight(attacker, target)) return false;
        if (PokemonUtils.isMeleeAttackMove(move)) {
            net.minecraft.world.phys.AABB targetBox = target instanceof PokemonEntity pokemonTarget
                    ? ActionBattleGroundController.effectiveCombatBox(pokemonTarget, attacker.level().getGameTime(), false)
                    : target.getBoundingBox();
            double executionRange = ActionBattleRangeRules.DEFAULT_MELEE_EXECUTION_RANGE
                    + ActionProjectileProfile.dashRangeBonus(move.getName());
            if (ActionBattleRangeRules.withinHitboxRange(
                    attacker.getBoundingBox(), targetBox, executionRange)
                    && ActionBattleGroundController.segmentIntersects(
                    targetBox, attacker.getEyePosition(), target.getEyePosition())) return true;
            return false;
        }
        return ActionBattleRangeRules.withinHitboxRange(attacker.getBoundingBox(), target.getBoundingBox(),
                ActionBattleRangeRules.DEFAULT_RANGED_EXECUTION_RANGE);
    }

    public static ProtectionOutcome applyProtectImpact(PokemonEntity attacker, PokemonEntity target, Move move, int beforeHp,
                                          int attemptedPokemonDamage, boolean hitSucceeded) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return ProtectionOutcome.NONE;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return ProtectionOutcome.NONE;
        long currentTick = attacker.level().getGameTime();
        UUID pokemonUUID = target.getPokemon().getUuid();
        var stance = ActionBattleProtectController.global().activeStance(battleId, pokemonUUID, currentTick);
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        boolean aquaActive = sessionId != null && ActionBattleTypeEffectController.global()
                .aquaShieldView(sessionId, pokemonUUID, currentTick).isPresent();
        if (stance == null && !aquaActive) {
            int after = target.getPokemon().getCurrentHealth();
            int actual = Math.max(0, beforeHp - after);
            return new ProtectionOutcome(false, false, after == 0 ? Math.max(actual, attemptedPokemonDamage) : actual);
        }
        int afterHp = target.getPokemon().getCurrentHealth();
        int actualDamage = Math.max(0, beforeHp - afterHp);
        int resolvedDamage = actualDamage;
        if (actualDamage > 0) {
            int damageForProtection = afterHp == 0
                    ? Math.max(actualDamage, attemptedPokemonDamage) : actualDamage;
            int dsLevel = ActionBattleProtectController.global().deterioratingShieldLevel(battleId, pokemonUUID);
            ActionBattleAquaShieldProtection.Result protection = ActionBattleAquaShieldProtection.resolve(
                    damageForProtection, dsLevel, stance != null, aquaActive);
            resolvedDamage = protection.finalDamage();
            if (stance != null && aquaActive) ActionBattleProtectController.global().breakStance(battleId, pokemonUUID);
            if (aquaActive) {
                ActionBattleTypeEffectController.global().breakAquaShield(
                        sessionId, pokemonUUID, currentTick, stance != null);
                ActionBattleWaterController.resolveProtectedHitShieldEnd(
                        sessionId, pokemonUUID, target, beforeHp, protection.finalDamage());
            } else {
                target.getPokemon().setCurrentHealth(Math.max(0, beforeHp - protection.finalDamage()));
            }
        }
        return new ProtectionOutcome(stance != null, aquaActive, resolvedDamage);
    }

    public static ActionBattlePropulsionRules.CommitMode commitMode(PokemonEntity attacker,
                                                                     LivingEntity target,
                                                                     Move move, int momentum) {
        boolean flyingMove = ActionBattleFlyingRules.isFlyingMove(move);
        boolean meleeMove = isMeleeMove(move);
        boolean enemyTargeted = !isSelfOrAllyTargetCategory(moveTargetCategory(move));
        boolean propulsion = enemyTargeted
                && ActionBattleFlyingRules.usesPropulsion(flyingMove, meleeMove, momentum);
        boolean propulsionReady = propulsion && canLaunchPropulsion(attacker, target, move, momentum);
        boolean normalReady = !propulsion && canCommit(attacker, target, move);
        return ActionBattlePropulsionRules.commitMode(propulsion, normalReady, propulsionReady);
    }

    public static boolean canLaunchPropulsion(PokemonEntity attacker, LivingEntity target,
                                              Move move, int momentum) {
        if (attacker == null || move == null
                || isSelfOrAllyTargetCategory(moveTargetCategory(move))
                || !ActionBattleFlyingRules.usesPropulsion(
                ActionBattleFlyingRules.isFlyingMove(move), isMeleeMove(move), momentum)) return false;
        LivingEntity executionTarget = resolveMoveTarget(attacker, target, move);
        if (!(executionTarget instanceof PokemonEntity pokemonTarget)
                || !pokemonTarget.isAlive() || pokemonTarget.isRemoved()
                || !hasActionLineOfSight(attacker, pokemonTarget)) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        return ActionBattlePropulsionController.canLaunch(session, attacker, pokemonTarget, momentum);
    }

    public record ProtectionOutcome(boolean protectParticipated, boolean aquaParticipated, int incomingDamage) {
        public static final ProtectionOutcome NONE = new ProtectionOutcome(false, false, 0);
    }

    public static boolean resolveRangedNativePokemonHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                         double committedGrassMultiplier) {
        return resolveRangedNativePokemonHit(attacker, target, move, committedGrassMultiplier,
                ActionBattleCommittedMove.none());
    }

    public static boolean resolveRangedNativePokemonHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                         double committedGrassMultiplier,
                                                         ActionBattleCommittedMove committedMove) {
        if (attacker == null || target == null || move == null || !target.isAlive()
                || !isNativeDamageMove(move)) return false;
        UUID guardedBattle = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        if (guardedBattle != null && ActionBattleSwapTransitionGuard.rejectsHit(
                guardedBattle, target.getPokemon().getUuid())) return false;
        ActionBattleGroundController.HitPlan groundPlan = ActionBattleGroundController.planHit(
                attacker, target, move);
        float scaledDamage = ActionBattleCriticalRules.apply(scaleActionDamage(attacker, target, move,
                PokemonAttackEffect.calculatePokemonDamage(attacker, target, move), committedGrassMultiplier),
                committedMove != null ? committedMove.critical() : null);
        int beforeHp = target.getPokemon().getCurrentHealth();
        int attemptedPokemonDamage = ActionBattleWaterHealth.toPokemonDamage(
                target.getPokemon().getMaxHealth(), target.getMaxHealth(), scaledDamage);
        long currentTick = attacker.level().getGameTime();
        applyOnUseEffectsWithoutActionStatuses(attacker, target, move);
        boolean success = target.hurt(attacker.damageSources().indirectMagic(attacker, attacker), scaledDamage);
        if (success) attacker.setLastHurtMob(target);
        PokemonUtils.setHurtByPlayer(attacker, target);
        PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
        PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        applyPostEffectsWithoutActionStatuses(attacker, target, move, success);
        boolean qualifyingWaterInteraction = success;
        ProtectionOutcome protection = applyProtectImpact(attacker, target, move, beforeHp, attemptedPokemonDamage, success);
        ActionBattleDarkRuntime.onConnectedHit(attacker, target, move, success);
        ActionBattleRockRuntime.HitResult rockHit = ActionBattleRockRuntime.resolveDirectHit(attacker, target,
                beforeHp, protection.incomingDamage(), success, protection.protectParticipated());
        ActionBattleGroundController.resolveAfterDamage(groundPlan, attacker, target, beforeHp);
        if (success) ActionBattleGrassController.onPokemonDamageResolved(attacker, target,
                Math.max(0, beforeHp - target.getPokemon().getCurrentHealth()));
        if (qualifyingWaterInteraction) ActionBattleWaterController.onSuccessfulInteraction(attacker, target, move);
        if (success) ActionBattleGrassController.onSuccessfulMoveResolved(attacker, target, move);
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(
                battleId, target.getPokemon().getUuid(), beforeHp, target.getPokemon().getCurrentHealth(),
                ActionBattleDamageFeedbackCategory.NORMAL);
        ActionBattleMoveEffectResolver.applyDeclaredFlinchOnHit(attacker, target, move, success);
        ActionBattleMoveEffectResolver.applyDeclaredConfusionOnHit(attacker, target, move, success);
        ActionBattleMoveEffectResolver.applyDeclaredParalysisOnHit(attacker, target, move, success);
        ActionBattleMoveEffectResolver.applyDeclaredMajorStatusesOnHit(attacker, target, move, success);
        ActionBattlePsycUpController.onSuccessfulEnemyMoveResolved(attacker, target, move, success);
        ActionBattleGhostRuntime.global().onDamageResolved(target, beforeHp);
        ActionBattleRockRuntime.applyReflection(attacker, rockHit);
        suppressWeightedKnockback(target, currentTick);
        if (success && !protection.protectParticipated() && !protection.aquaParticipated()
                && ActionBattleSteelRuntime.isActive(attacker, ActionBattleSteelRules.Branch.MAGNET_RISE, currentTick)
                && move.getType() != null && "steel".equalsIgnoreCase(move.getType().getName()) && isRangedMove(move)) {
            ActionBattleInterruptController.apply(ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID()),
                    target, ActionBattleSteelRules.MAGNET_RISE_INTERRUPT_TICKS, currentTick);
        }
        return success;
    }

    public static boolean executeConfusedRanged(PokemonEntity attacker, Move move, net.minecraft.world.phys.Vec3 direction) {
        return executeConfusedRanged(attacker, move, direction, 1.0D);
    }

    public static boolean executeConfusedRanged(PokemonEntity attacker, Move move, net.minecraft.world.phys.Vec3 direction,
                                                 double committedGrassMultiplier) {
        return executeConfusedRanged(attacker, move, direction, committedGrassMultiplier,
                ActionBattleCommittedMove.none());
    }

    public static boolean executeConfusedRanged(PokemonEntity attacker, Move move, net.minecraft.world.phys.Vec3 direction,
                                                 double committedGrassMultiplier,
                                                 ActionBattleCommittedMove committedMove) {
        if (attacker == null || move == null || direction == null || !isRangedMove(move)) return false;
        double ghostDamageMultiplier = ActionBattleGhostRuntime.global().prepareDamagingAbility(attacker, move);
        ActionBattleGhostCast ghostCast = ActionBattleGhostRuntime.global().completeMove(attacker, move).orElse(null);
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(null);
        PokemonUtils.sendAnimationPacket(attacker, "special");
        ActionBattleProjectileEntity projectile = new ActionBattleProjectileEntity(
                attacker.level(), attacker, move, direction, committedGrassMultiplier,
                ghostDamageMultiplier, ghostCast, committedMove);
        attacker.level().addFreshEntity(projectile);
        return true;
    }

    public static boolean execute(PokemonEntity attacker, LivingEntity target, Move move) {
        return execute(attacker, target, move, 1.0D);
    }

    public static boolean execute(PokemonEntity attacker, LivingEntity target, Move move, double committedGrassMultiplier) {
        return execute(attacker, target, move, committedGrassMultiplier, ActionBattleCommittedMove.none());
    }

    public static boolean execute(PokemonEntity attacker, LivingEntity target, Move move, double committedGrassMultiplier,
                                  ActionBattleCommittedMove committedMove) {
        return executeInternal(attacker, target, move, committedGrassMultiplier, committedMove, true, true);
    }

    private static boolean executeInternal(PokemonEntity attacker, LivingEntity target, Move move,
                                           double committedGrassMultiplier,
                                           ActionBattleCommittedMove committedMove,
                                           boolean allowPropulsion, boolean validateCommit) {
        if (ActionBattleEarthquakeHandler.isEarthquake(move)) {
            return ActionBattleEarthquakeHandler.launch(attacker, move, committedGrassMultiplier, committedMove);
        }
        LivingEntity executionTarget = resolveMoveTarget(attacker, target, move);
        int momentum = committedMove != null ? committedMove.flyingMomentum() : 0;
        ActionBattlePropulsionRules.CommitMode commitMode = commitMode(
                attacker, executionTarget, move, momentum);
        if (validateCommit && commitMode == ActionBattlePropulsionRules.CommitMode.REPOSITION) return false;
        target = executionTarget;
        if (allowPropulsion && commitMode == ActionBattlePropulsionRules.CommitMode.PROPULSION
                && target instanceof PokemonEntity pokemonTarget) {
            ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
            LivingEntity committedTarget = target;
            ((PokemonInterface) attacker).setCurrentMove(move);
            attacker.setTarget(target);
            PokemonUtils.sendAnimationPacket(attacker, "physical");
            return ActionBattlePropulsionController.launch(session, attacker, pokemonTarget, momentum,
                    () -> executeInternal(attacker, committedTarget, move, committedGrassMultiplier,
                            committedMove, false, false));
        }
        double ghostDamageMultiplier = ActionBattleGhostRuntime.global().prepareDamagingAbility(attacker, move);
        ActionBattleGhostCast ghostCast = ActionBattleGhostRuntime.global().completeMove(attacker, move).orElse(null);
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(target);
        if (PokemonUtils.isMeleeAttackMove(move)) {
            ActionBattleBugCast bugCast = ActionBattleBugRuntime.arm(attacker,
                    target instanceof PokemonEntity value ? value : null, move).orElse(null);
            PokemonUtils.sendAnimationPacket(attacker, "physical");
            PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
            UUID guardedBattle = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
            if (pokemonTarget != null && guardedBattle != null && ActionBattleSwapTransitionGuard.rejectsHit(
                    guardedBattle, pokemonTarget.getPokemon().getUuid())) {
                ActionBattleGhostRuntime.global().discard(ghostCast);
                return true;
            }
            ActionBattleGroundController.HitPlan groundPlan = pokemonTarget != null
                    ? ActionBattleGroundController.planHit(attacker, pokemonTarget, move)
                    : ActionBattleGroundController.HitPlan.NOT_QUALIFYING;
            long evasionTick = attacker.level().getGameTime();
            if (pokemonTarget != null && ActionBattleEvasionController.isEvading(pokemonTarget, evasionTick)) {
                net.minecraft.world.phys.Vec3 tracked = ActionBattleEvasionController.trackedPosition(pokemonTarget, evasionTick);
                net.minecraft.world.phys.AABB targetHitBox = ActionBattleGroundController.effectiveCombatBox(
                        pokemonTarget, evasionTick, false);
                net.minecraft.world.phys.AABB staleHitBox = targetHitBox.move(tracked.subtract(pokemonTarget.position())).inflate(0.10D);
                if (!staleHitBox.intersects(targetHitBox)) {
                    PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
                    ActionBattleGhostRuntime.global().discard(ghostCast);
                    return true;
                }
            }
            int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
            int attemptedPokemonDamage = pokemonTarget != null ? ActionBattleWaterHealth.toPokemonDamage(
                    pokemonTarget.getPokemon().getMaxHealth(), pokemonTarget.getMaxHealth(),
                    ActionBattleCriticalRules.apply(scaleActionDamage(attacker, pokemonTarget, move,
                            PokemonAttackEffect.calculatePokemonDamage(attacker, pokemonTarget, move), committedGrassMultiplier),
                            committedMove != null ? committedMove.critical() : null)) : 0;
            long currentTick = attacker.level().getGameTime();
            LivingEntity finalTarget = target;
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, finalTarget, move,
                    ActionBattleMoveEffectResolver.StatTrigger.BEFORE_USE, true);
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, finalTarget, move,
                    ActionBattleMoveEffectResolver.StatTrigger.ON_USE, true);
            boolean success = withOwnedMoveDataSuppressed(move, () -> PokemonAttackEffect.pokemonAttack(attacker, finalTarget));
            if (pokemonTarget != null) {
                if (success) applyPostHitActionStatScaling(attacker, pokemonTarget, move, beforeHp,
                        committedGrassMultiplier, groundPlan.damageMultiplier(), ghostDamageMultiplier, committedMove);
                boolean qualifyingWaterHit = success;
                ProtectionOutcome protection = applyProtectImpact(attacker, pokemonTarget, move, beforeHp, attemptedPokemonDamage, success);
                int actualBugTriggerDamage = success
                        ? ActionBattleBugRuntime.resolveIncomingDamage(pokemonTarget, beforeHp) : 0;
                ActionBattleBugRuntime.resolveHit(bugCast, pokemonTarget, actualBugTriggerDamage, move);
                ActionBattleDarkRuntime.onConnectedHit(attacker, pokemonTarget, move, success);
                ActionBattleRockRuntime.HitResult rockHit = ActionBattleRockRuntime.resolveDirectHit(attacker, pokemonTarget,
                        beforeHp, protection.incomingDamage(), success, protection.protectParticipated());
                ActionBattleGroundController.resolveAfterDamage(groundPlan, attacker, pokemonTarget, beforeHp);
                if (success) ActionBattleGrassController.onPokemonDamageResolved(attacker, pokemonTarget,
                        Math.max(0, beforeHp - pokemonTarget.getPokemon().getCurrentHealth()));
                if (qualifyingWaterHit) ActionBattleWaterController.onSuccessfulInteraction(attacker, pokemonTarget, move);
                if (success) ActionBattleGrassController.onSuccessfulMoveResolved(attacker, pokemonTarget, move);
                UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
                if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
                if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
                ActionBattleMoveEffectResolver.applyDeclaredFlinchOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredConfusionOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredParalysisOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredMajorStatusesOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, pokemonTarget, move,
                        ActionBattleMoveEffectResolver.StatTrigger.ON_HIT, success);
                ActionBattlePsycUpController.onSuccessfulEnemyMoveResolved(attacker, pokemonTarget, move, success);
                if (success) ActionBattleGhostRuntime.global().connect(ghostCast, pokemonTarget);
                else ActionBattleGhostRuntime.global().discard(ghostCast);
                ActionBattleGhostRuntime.global().onDamageResolved(pokemonTarget, beforeHp);
                ActionBattleRockRuntime.applyReflection(attacker, rockHit);
                suppressWeightedKnockback(pokemonTarget, currentTick);
                if (success && !protection.protectParticipated() && !protection.aquaParticipated()
                        && ActionBattleSteelRuntime.qualifiesWeightedMelee(attacker, move, currentTick)) {
                    ActionBattleWeightedKnockbackController.start(attacker, pokemonTarget,
                            Math.max(0, beforeHp - pokemonTarget.getPokemon().getCurrentHealth()), currentTick);
                }
            } else {
                ActionBattleGhostRuntime.global().discard(ghostCast);
            }
            return true;
        }
        if (isSelfOrAllyTargetCategory(moveTargetCategory(move))) {
            PokemonUtils.sendAnimationPacket(attacker, "special");
            LivingEntity finalTarget = target;
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, finalTarget, move,
                    ActionBattleMoveEffectResolver.StatTrigger.BEFORE_USE, true);
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, finalTarget, move,
                    ActionBattleMoveEffectResolver.StatTrigger.ON_USE, true);
            boolean success = withOwnedMoveDataSuppressed(move, () -> PokemonAttackEffect.pokemonAttack(attacker, finalTarget));
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, finalTarget, move,
                    ActionBattleMoveEffectResolver.StatTrigger.ON_HIT, success);
            if (success && target instanceof PokemonEntity pokemonTarget) {
                ActionBattleWaterController.onSuccessfulInteraction(attacker, pokemonTarget, move);
                ActionBattleGrassController.onSuccessfulMoveResolved(attacker, pokemonTarget, move);
                ActionBattleGhostRuntime.global().connect(ghostCast, pokemonTarget);
            } else {
                ActionBattleGhostRuntime.global().discard(ghostCast);
            }
            return true;
        }
        if (PokemonUtils.isRangeAttackMove(move) || ActionBattleWaterController.isQualifyingInteraction(move) || ActionBattleGrassController.isQualifyingMove(move) || (movePower(move) == 0 && ActionBattleMoveEffectResolver.hasSupportedActionStatusMetadata(move))) {
            PokemonUtils.sendAnimationPacket(attacker, "special");
            ActionBattleProjectileEntity projectile = new ActionBattleProjectileEntity(
                    attacker.level(), attacker, target, move, committedGrassMultiplier,
                    ghostDamageMultiplier, ghostCast, committedMove);
            attacker.level().addFreshEntity(projectile);
            return true;
        }
        ActionBattleGhostRuntime.global().discard(ghostCast);
        return false;
    }

    private static void suppressWeightedKnockback(PokemonEntity target, long currentTick) {
        if (ActionBattleSteelRuntime.isActive(target, ActionBattleSteelRules.Branch.WEIGHTED, currentTick)) {
            target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            target.hurtMarked = true;
        }
    }

    public static void applyOnUseEffectsWithoutActionStatuses(PokemonEntity attacker, LivingEntity target, Move move) {
        ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                ActionBattleMoveEffectResolver.StatTrigger.BEFORE_USE, true);
        ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                ActionBattleMoveEffectResolver.StatTrigger.ON_USE, true);
        withOwnedMoveDataSuppressed(move, () -> {
            PokemonAttackEffect.applyOnUseEffect(attacker, target, move);
            return true;
        });
    }

    public static void applyPostEffectsWithoutActionStatuses(PokemonEntity attacker, LivingEntity target, Move move, boolean success) {
        withOwnedMoveDataSuppressed(move, () -> {
            PokemonAttackEffect.applyPostEffect(attacker, target, move, success);
            return true;
        });
        ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                ActionBattleMoveEffectResolver.StatTrigger.ON_HIT, success);
    }

    private static boolean withOwnedMoveDataSuppressed(Move move, BooleanOperation operation) {
        if (move == null || operation == null) return false;
        synchronized (MoveData.moveData) {
            List<MoveData> original = MoveData.moveData.get(move.getName());
            if (original == null || original.stream().noneMatch(entry -> entry instanceof StatChangeMoveData
                    || entry instanceof StatusEffectMoveData status && ActionBattleMoveEffectResolver.isOwnedActionStatus(status))) {
                return operation.run();
            }
            List<MoveData> filtered = new ArrayList<>(original.size());
            for (MoveData entry : original) {
                if (entry instanceof StatusEffectMoveData status && ActionBattleMoveEffectResolver.isOwnedActionStatus(status)) continue;
                if (entry instanceof StatChangeMoveData) continue;
                filtered.add(entry);
            }
            MoveData.moveData.put(move.getName(), filtered);
            try {
                return operation.run();
            } finally {
                MoveData.moveData.put(move.getName(), original);
            }
        }
    }

    @FunctionalInterface
    private interface BooleanOperation { boolean run(); }


    public static int movePower(Move move) {
        if (move == null) return 0;
        Integer direct = invokeIntGetter(move, "getPower");
        if (direct != null) return Math.max(0, direct);
        try {
            Object template = move.getClass().getMethod("getTemplate").invoke(move);
            Integer templatePower = invokeIntGetter(template, "getPower");
            return templatePower != null ? Math.max(0, templatePower) : 0;
        } catch (ReflectiveOperationException exception) {
            return 0;
        }
    }

    public static int movePriority(Move move) {
        if (move == null) return 0;
        Integer direct = invokeIntGetter(move, "getPriority");
        if (direct != null) return direct;
        try {
            Object template = move.getClass().getMethod("getTemplate").invoke(move);
            Integer templatePriority = invokeIntGetter(template, "getPriority");
            return templatePriority != null ? templatePriority : 0;
        } catch (ReflectiveOperationException exception) {
            return 0;
        }
    }

    public static String moveTargetCategory(Move move) {
        if (move == null) return "";
        Object target = invokeGetter(move, "getTarget");
        if (target == null) {
            Object template = invokeGetter(move, "getTemplate");
            target = invokeGetter(template, "getTarget");
        }
        return target != null ? target.toString() : "";
    }

    public static LivingEntity resolveMoveTarget(PokemonEntity attacker, LivingEntity suppliedTarget, Move move) {
        return isSelfOrAllyTargetCategory(moveTargetCategory(move)) ? attacker : suppliedTarget;
    }

    public static boolean isSelfOrAllyTargetCategory(String category) {
        return ActionBattleMoveTargetRules.usesCasterInSingles(category);
    }

    private static Object invokeGetter(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Integer invokeIntGetter(Object target, String methodName) {
        if (target == null) return null;
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Number number ? number.intValue() : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
