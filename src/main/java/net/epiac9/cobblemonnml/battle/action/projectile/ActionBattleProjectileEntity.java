package net.epiac9.cobblemonnml.battle.action.projectile;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import me.rufia.fightorflight.entity.projectile.AbstractPokemonProjectile;
import me.rufia.fightorflight.entity.projectile.PokemonArrow;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
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
        setDamage(FightOrFlightAdapter.isNativeDamageMove(move) ? PokemonAttackEffect.calculatePokemonDamage(shooter, target, move) : 0.0F);
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        double d = target.getX() - getX();
        double e = target.getY(0.5D) - getY();
        double f = target.getZ() - getZ();
        if (ActionProjectileProfile.isLobbed(move.getName())) e += Math.sqrt(d * d + f * f) * 0.30D;
        shoot(d, e, f, (float) ActionProjectileProfile.speedBlocksPerTick(move.getName()), 0.0F);
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
        double speed = ActionProjectileProfile.speedBlocksPerTick(committedMoveName());
        if (ActionProjectileProfile.isHoming(committedMoveName())) {
            Vec3 delta = target.getEyePosition().subtract(position());
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
                horizontal = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
            }
            if (horizontal.lengthSqr() > 0.000001D) horizontal = horizontal.normalize().scale(speed);
            setDeltaMovement(horizontal.x, vertical, horizontal.z);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
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
        PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
        int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
        if (nativeDamageMove) FightOrFlightAdapter.applyOnUseEffectsWithoutActionStatuses(attacker, target, move);
        boolean success = !nativeDamageMove || target.hurt(damageSources().indirectMagic(this, attacker), getDamage());
        if (nativeDamageMove && success) attacker.setLastHurtMob(target);
        if (nativeDamageMove) PokemonUtils.setHurtByPlayer(attacker, target);
        PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
        PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        if (nativeDamageMove) FightOrFlightAdapter.applyPostEffectsWithoutActionStatuses(attacker, target, move, success);
        if (pokemonTarget != null) {
            UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
            if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
            if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
            ActionBattleMoveEffectResolver.applyDeclaredBurnOnHit(attacker, pokemonTarget, move, success);
            ActionBattleMoveEffectResolver.applyDeclaredFreezeOnHit(attacker, pokemonTarget, move, success);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        intendedTargetUUID = tag.hasUUID("ActionTarget") ? tag.getUUID("ActionTarget") : null;
        committedMoveName = tag.getString("ActionMove");
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        maxLifetimeTicks = tag.contains("ActionLifetime") ? tag.getInt("ActionLifetime") : 80;
    }
}
