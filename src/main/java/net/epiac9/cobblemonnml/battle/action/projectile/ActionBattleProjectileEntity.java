package net.epiac9.cobblemonnml.battle.action.projectile;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import me.rufia.fightorflight.entity.projectile.AbstractPokemonProjectile;
import me.rufia.fightorflight.entity.projectile.PokemonArrow;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleEvasionController;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ActionBattleProjectileEntity extends PokemonArrow {
    private static final EntityDataAccessor<String> DATA_MOVE_NAME = SynchedEntityData.defineId(ActionBattleProjectileEntity.class, EntityDataSerializers.STRING);
    private int maxLifetimeTicks = 80;
    private UUID intendedTargetUUID;
    private String committedMoveName = "";
    private transient Move committedMove;
    private boolean confusedShot;
    private double accuracySpeedMultiplier = 1.0D;

    public ActionBattleProjectileEntity(EntityType<? extends AbstractPokemonProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move) {
        super(ModEntities.ACTION_BATTLE_PROJECTILE.get(), level);
        initPosition(shooter);
        setOwner(shooter);
        setNoGravity(!ActionProjectileProfile.isLobbed(move.getName()));
        intendedTargetUUID = target.getUUID();
        committedMoveName = move.getName();
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        committedMove = move;
        setElementalType(move.getType().getName());
        setDamage(FightOrFlightAdapter.isNativeDamageMove(move) ? FightOrFlightAdapter.scaleActionDamage(shooter, target, move, PokemonAttackEffect.calculatePokemonDamage(shooter, target, move)) : 0.0F);
        accuracySpeedMultiplier = FightOrFlightAdapter.actionAccuracyProjectileMultiplier(shooter);
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        Vec3 trackedTarget = target instanceof PokemonEntity pokemonTarget
                ? ActionBattleEvasionController.trackedPosition(pokemonTarget, level.getGameTime()).add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double d = trackedTarget.x - getX();
        double e = trackedTarget.y - getY();
        double f = trackedTarget.z - getZ();
        if (ActionProjectileProfile.isLobbed(move.getName())) e += Math.sqrt(d * d + f * f) * 0.30D;
        shoot(d, e, f, (float) projectileSpeed(move.getName()), 0.0F);
    }


    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction) {
        super(ModEntities.ACTION_BATTLE_PROJECTILE.get(), level);
        initPosition(shooter);
        setOwner(shooter);
        setNoGravity(!ActionProjectileProfile.isLobbed(move.getName()));
        intendedTargetUUID = null;
        confusedShot = true;
        committedMoveName = move.getName();
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        committedMove = move;
        setElementalType(move.getType().getName());
        setDamage(0.0F);
        accuracySpeedMultiplier = FightOrFlightAdapter.actionAccuracyProjectileMultiplier(shooter);
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        Vec3 shot = direction.lengthSqr() > 0.000001D ? direction.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        shoot(shot.x, shot.y, shot.z, (float) projectileSpeed(move.getName()), 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVE_NAME, "");
    }

    public String committedMoveName() {
        String synced = entityData.get(DATA_MOVE_NAME);
        return synced.isEmpty() ? committedMoveName : synced;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) updateDeliveryMotion();
        super.tick();
        if (!level().isClientSide && tickCount >= maxLifetimeTicks) discard();
    }

    private void updateDeliveryMotion() {
        if (!(level() instanceof ServerLevel serverLevel) || intendedTargetUUID == null) return;
        Entity rawTarget = serverLevel.getEntity(intendedTargetUUID);
        if (!(rawTarget instanceof LivingEntity target) || !target.isAlive()) return;
        double speed = projectileSpeed(committedMoveName());
        if (ActionProjectileProfile.isHoming(committedMoveName())) {
            Vec3 trackedEye = target instanceof PokemonEntity pokemonTarget
                    ? ActionBattleEvasionController.trackedEyePosition(pokemonTarget, serverLevel.getGameTime()) : target.getEyePosition();
            Vec3 delta = trackedEye.subtract(position());
            if (delta.lengthSqr() > 0.000001D) setDeltaMovement(delta.normalize().scale(speed));
            return;
        }
        if (ActionProjectileProfile.isGrounded(committedMoveName())) {
            BlockPos column = blockPosition();
            double surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) + 0.15D;
            Vec3 current = getDeltaMovement();
            double vertical = Math.max(-0.20D, Math.min(0.20D, surfaceY - getY()));
            Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
            if (horizontal.lengthSqr() < 0.000001D) {
                Vec3 tracked = target instanceof PokemonEntity pokemonTarget
                        ? ActionBattleEvasionController.trackedPosition(pokemonTarget, serverLevel.getGameTime()) : target.position();
                horizontal = new Vec3(tracked.x - getX(), 0.0D, tracked.z - getZ());
            }
            if (horizontal.lengthSqr() > 0.000001D) horizontal = horizontal.normalize().scale(speed);
            setDeltaMovement(horizontal.x, vertical, horizontal.z);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (confusedShot) return target instanceof LivingEntity && target != getOwner() && super.canHitEntity(target);
        return intendedTargetUUID != null && intendedTargetUUID.equals(target.getUUID()) && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity owner = getOwner();
        Entity rawTarget = result.getEntity();
        if (!(owner instanceof PokemonEntity attacker) || !(rawTarget instanceof LivingEntity target)) {
            discard();
            return;
        }
        Move move = resolveCommittedMove(attacker);
        if (move == null) {
            discard();
            return;
        }
        boolean nativeDamageMove = FightOrFlightAdapter.isNativeDamageMove(move);
        if (nativeDamageMove) setDamage(FightOrFlightAdapter.scaleActionDamage(attacker, target, move, PokemonAttackEffect.calculatePokemonDamage(attacker, target, move)));
        PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
        int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
        ActionBattleSession sleepSession = pokemonTarget != null ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemonTarget.getUUID()) : null;
        long currentTick = attacker.level().getGameTime();
        ActionBattleSleepController.WakePlan wakePlan = nativeDamageMove && pokemonTarget != null
                ? ActionBattleSleepController.planDamagingWake(sleepSession, pokemonTarget, currentTick, true, false)
                : ActionBattleSleepController.WakePlan.NONE;
        if (nativeDamageMove) FightOrFlightAdapter.applyOnUseEffectsWithoutActionStatuses(attacker, target, move);
        boolean success = !nativeDamageMove || target.hurt(damageSources().indirectMagic(this, attacker), getDamage());
        if (nativeDamageMove && success) attacker.setLastHurtMob(target);
        if (nativeDamageMove) PokemonUtils.setHurtByPlayer(attacker, target);
        PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
        PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        if (nativeDamageMove) FightOrFlightAdapter.applyPostEffectsWithoutActionStatuses(attacker, target, move, success);
        if (pokemonTarget != null) {
            FightOrFlightAdapter.applyProtectImpact(attacker, pokemonTarget, move, beforeHp, success);
            if (nativeDamageMove && success) ActionBattleFireController.onSuccessfulMoveHit(attacker, pokemonTarget, move, ActionBattleFireRules.NORMAL_PRESSURE);
            if (nativeDamageMove && success) ActionBattleSleepController.applyWakeDamageAndWake(sleepSession, pokemonTarget, currentTick, beforeHp, wakePlan);
            UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
            if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
            if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
            ActionBattleMoveEffectResolver.applyDeclaredFlinchOnHit(attacker, pokemonTarget, move, success);
            ActionBattleMoveEffectResolver.applyDeclaredConfusionOnHit(attacker, pokemonTarget, move, success);
        }
        discard();
    }

    private Move resolveCommittedMove(PokemonEntity attacker) {
        if (committedMove != null && committedMoveName.equals(committedMove.getName())) return committedMove;
        for (Move move : attacker.getPokemon().getMoveSet()) {
            if (move != null && committedMoveName.equals(move.getName())) {
                committedMove = move;
                return move;
            }
        }
        return null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (intendedTargetUUID != null) tag.putUUID("ActionTarget", intendedTargetUUID);
        tag.putString("ActionMove", committedMoveName);
        tag.putInt("ActionLifetime", maxLifetimeTicks);
        tag.putBoolean("ActionConfusedShot", confusedShot);
        tag.putDouble("ActionAccuracySpeedMultiplier", accuracySpeedMultiplier);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        intendedTargetUUID = tag.hasUUID("ActionTarget") ? tag.getUUID("ActionTarget") : null;
        committedMoveName = tag.getString("ActionMove");
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        maxLifetimeTicks = tag.contains("ActionLifetime") ? tag.getInt("ActionLifetime") : 80;
        confusedShot = tag.getBoolean("ActionConfusedShot");
        accuracySpeedMultiplier = tag.contains("ActionAccuracySpeedMultiplier") ? tag.getDouble("ActionAccuracySpeedMultiplier") : 1.0D;
    }
    private double projectileSpeed(String moveName) {
        return ActionProjectileProfile.speedBlocksPerTick(moveName) * accuracySpeedMultiplier;
    }

}
