package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.PokemonInterface;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public final class FightOrFlightAdapter {
    private FightOrFlightAdapter() {}

    public static boolean supports(Move move) {
        return move != null && (ActionBattleBalefulBunkerHandler.isBalefulBunker(move) || ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isMeleeAttackMove(move) || PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedBurnOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedFreezeOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedPoisonOnHitMetadata(move))));
    }

    public static boolean isRangedMove(Move move) {
        return move != null && (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move) || PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedBurnOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedFreezeOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedPoisonOnHitMetadata(move))));
    }

    public static boolean isNativeDamageMove(Move move) {
        return move != null && (PokemonUtils.isMeleeAttackMove(move) || PokemonUtils.isRangeAttackMove(move));
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
        if (stance.contactPoisonRetaliation() && makesContact(move)) {
            ActionBattleEffectController.global().applyPoison(battleId, attacker.getPokemon().getUuid(), 1, currentTick);
        }
    }

    public static boolean execute(PokemonEntity attacker, LivingEntity target, Move move) {
        if (!canCommit(attacker, target, move)) return false;
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(target);
        if (PokemonUtils.isMeleeAttackMove(move)) {
            PokemonUtils.sendAnimationPacket(attacker, "physical");
            PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
            int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
            boolean success = withStatusMoveDataSuppressed(move, () -> PokemonAttackEffect.pokemonAttack(attacker, target));
            if (pokemonTarget != null) {
                applyProtectImpact(attacker, pokemonTarget, move, beforeHp, success);
                UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
                if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
                if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
                ActionBattleMoveEffectResolver.applyDeclaredBurnOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredFreezeOnHit(attacker, pokemonTarget, move, success);
                ActionBattleMoveEffectResolver.applyDeclaredPoisonOnHit(attacker, pokemonTarget, move, success);
            }
            return true;
        }
        if (PokemonUtils.isRangeAttackMove(move) || (movePower(move) == 0 && (ActionBattleMoveEffectResolver.hasSupportedBurnOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedFreezeOnHitMetadata(move) || ActionBattleMoveEffectResolver.hasSupportedPoisonOnHitMetadata(move)))) {
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
