package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.PokemonInterface;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleEvasionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleStatResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class FightOrFlightAdapter {
    private FightOrFlightAdapter() {}

    public static boolean supports(Move move) {
        return move != null && (ActionBattleBalefulBunkerHandler.isBalefulBunker(move) || ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isMeleeAttackMove(move) || PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedFlinchOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedConfusionOnHitMetadata(move))));
    }

    public static boolean isMeleeMove(Move move) { return move != null && PokemonUtils.isMeleeAttackMove(move); }

    public static boolean isRangedMove(Move move) {
        return move != null && (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedFlinchOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedConfusionOnHitMetadata(move))));
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
        float stageScaledDamage = Math.max(0.0F, (float) (baseDamage * multiplier));
        float fireModifiedDamage = ActionBattleFireController.modifyDamage(attacker, target, move, stageScaledDamage);
        return ActionBattleIceController.modifyDamage(attacker, target, move, fireModifiedDamage);
    }

    private static void applyPostHitActionStatScaling(PokemonEntity attacker, PokemonEntity target, Move move, int beforeHp) {
        if (attacker == null || target == null || move == null || beforeHp <= 0) return;
        int afterHp = target.getPokemon().getCurrentHealth();
        int baseDamage = Math.max(0, beforeHp - afterHp);
        if (baseDamage <= 0) return;
        int scaledDamage = Math.max(1, Math.round(scaleActionDamage(attacker, target, move, baseDamage)));
        target.getPokemon().setCurrentHealth(Math.max(0, beforeHp - scaledDamage));
    }

    private static boolean isSpecialDamageCategory(Move move) {
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
        int priority = movePriority(move);
        if (priority > 0) return 40L;
        if (priority < 0) return 80L;
        return 60L;
    }

    public static boolean canCommitHail(PokemonEntity attacker, LivingEntity target) {
        if (attacker == null || target == null || !target.isAlive()) return false;
        if (!attacker.getSensing().hasLineOfSight(target)) return false;
        double range = ActionProjectileProfile.rangedCommitDistance();
        return attacker.distanceToSqr(target) <= range * range;
    }

    public static boolean canCommit(PokemonEntity attacker, LivingEntity target, Move move) {
        if (attacker == null || target == null || move == null || !target.isAlive() || !supports(move)) return false;
        if (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move)) return canCommitHail(attacker, target);
        if (!attacker.getSensing().hasLineOfSight(target)) return false;
        if (PokemonUtils.isMeleeAttackMove(move)) {
            if (attacker.isWithinMeleeAttackRange(target)) return true;
            return ActionProjectileProfile.isDashRush(move.getName())
                    && attacker.getBoundingBox().inflate(ActionProjectileProfile.dashRangeBonus(move.getName())).intersects(target.getBoundingBox());
        }
        double range = ActionProjectileProfile.rangedCommitDistance();
        return attacker.distanceToSqr(target) <= range * range;
    }

    public static void applyProtectImpact(PokemonEntity attacker, PokemonEntity target, Move move, int beforeHp, boolean hitSucceeded) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        long currentTick = attacker.level().getGameTime();
        var stance = ActionBattleProtectController.global().activeStance(battleId, target.getPokemon().getUuid(), currentTick);
        if (stance == null) return;
        int afterHp = target.getPokemon().getCurrentHealth();
        int actualDamage = Math.max(0, beforeHp - afterHp);
        if (actualDamage > 0) {
            int protectedDamage = ActionBattleProtectController.global().modifyFinalDamage(
                    battleId, target.getPokemon().getUuid(), currentTick, actualDamage);
            target.getPokemon().setCurrentHealth(Math.max(0, beforeHp - protectedDamage));
        }
    }

    public static boolean executeConfusedRanged(PokemonEntity attacker, Move move, net.minecraft.world.phys.Vec3 direction) {
        if (attacker == null || move == null || direction == null || !isRangedMove(move)) return false;
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(null);
        PokemonUtils.sendAnimationPacket(attacker, "special");
        ActionBattleProjectileEntity projectile = new ActionBattleProjectileEntity(attacker.level(), attacker, move, direction);
        attacker.level().addFreshEntity(projectile);
        return true;
    }

    public static boolean execute(PokemonEntity attacker, LivingEntity target, Move move) {
        if (!canCommit(attacker, target, move)) return false;
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(target);
        if (PokemonUtils.isMeleeAttackMove(move)) {
            PokemonUtils.sendAnimationPacket(attacker, "physical");
            PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
            long evasionTick = attacker.level().getGameTime();
            if (pokemonTarget != null && ActionBattleEvasionController.isEvading(pokemonTarget, evasionTick)) {
                net.minecraft.world.phys.Vec3 tracked = ActionBattleEvasionController.trackedPosition(pokemonTarget, evasionTick);
                net.minecraft.world.phys.AABB staleHitBox = pokemonTarget.getBoundingBox().move(tracked.subtract(pokemonTarget.position())).inflate(0.10D);
                if (!staleHitBox.intersects(pokemonTarget.getBoundingBox())) {
                    PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
                    return true;
                }
            }
            int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
            ActionBattleSession sleepSession = pokemonTarget != null ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemonTarget.getUUID()) : null;
            long currentTick = attacker.level().getGameTime();
            ActionBattleSleepController.WakePlan wakePlan = pokemonTarget != null
                    ? ActionBattleSleepController.planDamagingWake(sleepSession, pokemonTarget, currentTick, false, false)
                    : ActionBattleSleepController.WakePlan.NONE;
            boolean success = withStatusMoveDataSuppressed(move, () -> PokemonAttackEffect.pokemonAttack(attacker, target));
            if (pokemonTarget != null) {
                if (success) applyPostHitActionStatScaling(attacker, pokemonTarget, move, beforeHp);
                applyProtectImpact(attacker, pokemonTarget, move, beforeHp, success);
                if (success) ActionBattleFireController.onSuccessfulMoveHit(attacker, pokemonTarget, move, ActionBattleFireRules.NORMAL_PRESSURE);
                if (ActionBattleIceRules.isQualifyingDamagingHit(success, beforeHp, pokemonTarget.getPokemon().getCurrentHealth())) {
                    ActionBattleIceController.onSuccessfulMoveHit(attacker, pokemonTarget, move);
                }
                if (success) ActionBattleSleepController.applyWakeDamageAndWake(sleepSession, pokemonTarget, currentTick, beforeHp, wakePlan);
                UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
                if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
                if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
                ActionBattleMoveEffectResolver.applyDeclaredFlinchOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredConfusionOnHit(attacker, pokemonTarget, move, success);
            }
            return true;
        }
        if (PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedFlinchOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedConfusionOnHitMetadata(move)))) {
            PokemonUtils.sendAnimationPacket(attacker, "special");
            ActionBattleProjectileEntity projectile = new ActionBattleProjectileEntity(attacker.level(), attacker, target, move);
            attacker.level().addFreshEntity(projectile);
            return true;
        }
        return false;
    }

    public static void applyOnUseEffectsWithoutActionStatuses(PokemonEntity attacker, LivingEntity target, Move move) {
        withStatusMoveDataSuppressed(move, () -> {
            PokemonAttackEffect.applyOnUseEffect(attacker, target, move);
            return true;
        });
    }

    public static void applyPostEffectsWithoutActionStatuses(PokemonEntity attacker, LivingEntity target, Move move, boolean success) {
        withStatusMoveDataSuppressed(move, () -> {
            PokemonAttackEffect.applyPostEffect(attacker, target, move, success);
            return true;
        });
    }

    private static boolean withStatusMoveDataSuppressed(Move move, BooleanOperation operation) {
        if (move == null || operation == null) return false;
        synchronized (MoveData.moveData) {
            List<MoveData> original = MoveData.moveData.get(move.getName());
            if (original == null || original.stream().noneMatch(StatusEffectMoveData.class::isInstance)) return operation.run();
            List<MoveData> filtered = new ArrayList<>(original.size());
            for (MoveData entry : original) {
                if (entry instanceof StatusEffectMoveData status && ActionBattleMoveEffectResolver.isOwnedActionStatus(status)) continue;
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
