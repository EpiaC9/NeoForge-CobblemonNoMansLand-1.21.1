package net.epiac9.cobblemonnml.battle.action.projectile.lob;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldPlacement;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

public final class ActionBattleLobProjectileEntity extends Entity {
    private ActionBattleLobPayload payload;
    private ActionBattleLobTrajectory.Point origin;
    private int durationTicks;
    private double arcHeight;
    private final ActionBattleLobRuntime collision = new ActionBattleLobRuntime();

    public ActionBattleLobProjectileEntity(EntityType<? extends ActionBattleLobProjectileEntity> type, Level level) { super(type, level); }

    public ActionBattleLobProjectileEntity(ServerLevel level, Vec3 origin, ActionBattleLobPayload payload,
                                            int durationTicks, double arcHeight) {
        super(ModEntities.ACTION_BATTLE_LOB_PROJECTILE.get(), level);
        if (origin == null || payload == null || durationTicks <= 0 || arcHeight < 0) throw new IllegalArgumentException("Invalid Lob launch.");
        this.payload = payload;
        this.origin = point(origin);
        this.durationTicks = durationTicks;
        this.arcHeight = arcHeight;
        setPos(origin);
        noPhysics = true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override public void tick() {
        super.tick();
        if (level().isClientSide || payload == null || origin == null) return;
        if (tickCount > durationTicks) { discard(); return; }
        Vec3 previous = position();
        var destination = payload.intendedLanding().getCenter();
        var nextPoint = ActionBattleLobTrajectory.position(origin, point(destination), arcHeight, tickCount, durationTicks);
        Vec3 next = vec(nextPoint);
        BlockHitResult hit = level().clip(new ClipContext(previous, next, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            resolve(hit.getBlockPos().relative(hit.getDirection()));
            return;
        }
        setPos(next);
        if (level() instanceof ServerLevel server) {
            var particle = payload.kind() == ActionBattleLobPayload.PayloadKind.GRASS_SEED
                    ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SPLASH;
            server.sendParticles(particle, next.x, next.y, next.z, 2, 0.08D, 0.08D, 0.08D, 0.0D);
        }
        if (tickCount >= durationTicks) resolve(payload.intendedLanding());
    }

    private void resolve(BlockPos position) {
        var result = collision.onArrival(runtimePosition(position), this::validPlacement);
        if (result.resolution() == ActionBattleLobRuntime.Resolution.VALID && level() instanceof ServerLevel server) {
            BlockPos actual = blockPos(result.position());
            switch (payload.kind()) {
                case AQUA_BUBBLE -> ActionBattleWaterController.onLobImpact(server, payload, actual);
                case GRASS_SEED -> ActionBattleGrassController.onSeedLobImpact(server, payload, actual);
            }
        }
        discard();
    }

    private boolean validPlacement(ActionBattleLobRuntime.Position position) {
        if (!(level() instanceof ServerLevel server)) return false;
        BlockPos pos = blockPos(position);
        int dx = pos.getX() - payload.anchor().getX();
        int dz = pos.getZ() - payload.anchor().getZ();
        return ActionBattleFieldPlacement.isHorizontalOffsetEligible(dx, dz)
                && server.isLoaded(pos) && server.getWorldBorder().isWithinBounds(pos)
                && server.getBlockState(pos).isAir() && server.getBlockState(pos.above()).isAir()
                && server.getBlockState(pos.below()).isFaceSturdy(server, pos.below(), net.minecraft.core.Direction.UP);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.hasUUID("LobSession") || !tag.hasUUID("LobBattle") || !tag.hasUUID("LobOwner")
                || !tag.contains("LobKind") || !tag.contains("LobSide") || !tag.contains("LobAnchor")
                || !tag.contains("LobLanding") || !tag.contains("LobOriginX") || !tag.contains("LobDuration")) {
            discard();
            return;
        }
        try {
            payload = new ActionBattleLobPayload(
                    ActionBattleLobPayload.PayloadKind.valueOf(tag.getString("LobKind")),
                    tag.getUUID("LobSession"), tag.getUUID("LobBattle"), tag.getUUID("LobOwner"),
                    net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject.OwnerSide.valueOf(tag.getString("LobSide")),
                    BlockPos.of(tag.getLong("LobAnchor")), BlockPos.of(tag.getLong("LobLanding")));
            origin = new ActionBattleLobTrajectory.Point(
                    tag.getDouble("LobOriginX"), tag.getDouble("LobOriginY"), tag.getDouble("LobOriginZ"));
            durationTicks = Math.max(1, tag.getInt("LobDuration"));
            arcHeight = Math.max(0.0D, tag.getDouble("LobArcHeight"));
            noPhysics = true;
        } catch (IllegalArgumentException exception) {
            discard();
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (payload == null || origin == null) return;
        tag.putString("LobKind", payload.kind().name());
        tag.putUUID("LobSession", payload.sessionId());
        tag.putUUID("LobBattle", payload.battleId());
        tag.putUUID("LobOwner", payload.ownerPokemonId());
        tag.putString("LobSide", payload.ownerSide().name());
        tag.putLong("LobAnchor", payload.anchor().asLong());
        tag.putLong("LobLanding", payload.intendedLanding().asLong());
        tag.putDouble("LobOriginX", origin.x());
        tag.putDouble("LobOriginY", origin.y());
        tag.putDouble("LobOriginZ", origin.z());
        tag.putInt("LobDuration", durationTicks);
        tag.putDouble("LobArcHeight", arcHeight);
    }
    private static ActionBattleLobTrajectory.Point point(Vec3 value) { return new ActionBattleLobTrajectory.Point(value.x, value.y, value.z); }
    private static Vec3 vec(ActionBattleLobTrajectory.Point value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static ActionBattleLobRuntime.Position runtimePosition(BlockPos pos) { return new ActionBattleLobRuntime.Position(pos.getX(), pos.getY(), pos.getZ()); }
    private static BlockPos blockPos(ActionBattleLobRuntime.Position pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
}
