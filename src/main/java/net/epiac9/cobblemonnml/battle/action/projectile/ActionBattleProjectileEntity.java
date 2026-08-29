package net.epiac9.cobblemonnml.battle.action.projectile;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import me.rufia.fightorflight.entity.projectile.AbstractPokemonProjectile;
import me.rufia.fightorflight.entity.projectile.PokemonArrow;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.epiac9.cobblemonnml.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

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
        setNoGravity(true);
        intendedTargetUUID = target.getUUID();
        committedMoveName = move.getName();
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        committedMove = move;
        setElementalType(move.getType().getName());
        setDamage(PokemonAttackEffect.calculatePokemonDamage(shooter, target, move));
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        double d = target.getX() - getX();
        double e = target.getY(0.5D) - getY();
        double f = target.getZ() - getZ();
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
        super.tick();
        if (!level().isClientSide && tickCount >= maxLifetimeTicks) discard();
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
        PokemonAttackEffect.applyOnUseEffect(attacker, target, move);
        boolean success = target.hurt(damageSources().indirectMagic(this, attacker), getDamage());
        if (success) attacker.setLastHurtMob(target);
        PokemonUtils.setHurtByPlayer(attacker, target);
        PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
        PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        PokemonAttackEffect.applyPostEffect(attacker, target, move, success);
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
