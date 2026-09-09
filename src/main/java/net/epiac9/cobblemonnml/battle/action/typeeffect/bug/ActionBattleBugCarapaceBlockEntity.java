package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattleBugCarapaceBlockEntity extends BlockEntity {
    private UUID battleId;
    private UUID ownerEntityId;
    private UUID ownerPokemonId;
    private String speciesId = "cobblemon:missingno";
    private String formId = "normal";
    private String aspectsCsv = "";
    private String animationName = "stand";
    private float statueYaw;
    private boolean physical;
    private boolean effectZone;
    private int shellRemainingTicks;
    private int zoneRemainingTicks;
    private float collisionWidth = 1.0F;
    private float collisionHeight = 1.0F;

    public ActionBattleBugCarapaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACTION_BATTLE_BUG_CARAPACE.get(), pos, state);
    }

    public void initialize(UUID battleId, PokemonEntity owner, ActionBattleBugCarapaceState.Snapshot snapshot) {
        this.battleId = battleId;
        ownerEntityId = owner.getUUID();
        ownerPokemonId = owner.getPokemon().getUuid();
        speciesId = owner.getPokemon().getSpecies().getResourceIdentifier().toString();
        formId = owner.getPokemon().getForm().getName();
        aspectsCsv = String.join(",", owner.getPokemon().getAspects());
        animationName = owner.getCurrentPoseType() == null ? "stand"
                : owner.getCurrentPoseType().name().toLowerCase(Locale.ROOT);
        statueYaw = owner.getYRot();
        collisionWidth = Math.max(1.0F, owner.getBbWidth());
        collisionHeight = Math.max(1.0F, owner.getBbHeight());
        update(snapshot);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || battleId == null || ownerPokemonId == null) return;
        Entity rawOwner = ownerEntityId != null ? server.getEntity(ownerEntityId) : null;
        ActionBattleBugState state = ActionBattleBugController.global().state(battleId, ownerPokemonId).orElse(null);
        if (!(rawOwner instanceof PokemonEntity owner) || owner.isRemoved() || state == null
                || !ActionBattleBugController.global().isConstructAt(battleId, ownerPokemonId,
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ())) {
            remove("owner-unavailable");
            return;
        }
        boolean wasPhysical = physical;
        boolean wasZone = effectZone;
        update(state.carapaceSnapshot(server.getGameTime()));
        if (wasZone && !effectZone) DebugLog.logf("[BugAdapt] zone expired owner=%s", ownerPokemonId);
        if (wasPhysical && !physical) DebugLog.logf("[BugAdapt] carapace expired owner=%s", ownerPokemonId);
        if (!physical && !effectZone) remove("expired");
    }

    public ActionBattleBugCarapaceState.ProjectileOutcome collideProjectile(UUID attackingPokemonId, long tick) {
        if (battleId == null || ownerPokemonId == null
                || !ActionBattleBugRuntime.areEnemies(battleId, ownerPokemonId, attackingPokemonId)) {
            return ActionBattleBugCarapaceState.ProjectileOutcome.MISS;
        }
        ActionBattleBugState state = ActionBattleBugController.global().state(battleId, ownerPokemonId).orElse(null);
        if (state == null || (!physical && !effectZone)) return ActionBattleBugCarapaceState.ProjectileOutcome.MISS;
        ActionBattleBugCarapaceState.ProjectileOutcome outcome = physical
                ? ActionBattleBugCarapaceState.ProjectileOutcome.DESTROY_BOTH
                : ActionBattleBugCarapaceState.ProjectileOutcome.DESTROY_CONSTRUCT_AND_CONTINUE;
        state.breakCarapace();
        DebugLog.logf("[BugAdapt] %s owner=%s projectile=%s",
                physical && effectZone ? "combined carapace+zone destroyed by projectile"
                        : physical ? "carapace destroyed by projectile"
                        : "zone destroyed; projectile continues", ownerPokemonId, attackingPokemonId);
        remove("projectile");
        return outcome;
    }

    private void update(ActionBattleBugCarapaceState.Snapshot snapshot) {
        physical = snapshot.physicalActive();
        effectZone = snapshot.effectZoneActive();
        shellRemainingTicks = remainingInt(snapshot.physicalRemainingTicks());
        zoneRemainingTicks = remainingInt(snapshot.effectZoneRemainingTicks());
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void remove(String reason) {
        if (level == null || level.isClientSide) return;
        DebugLog.logf("[BugAdapt] carapace block remove owner=%s reason=%s pos=%s", ownerPokemonId, reason, worldPosition);
        level.removeBlock(worldPosition, false);
    }

    public AABB collisionBox() {
        double centerX = worldPosition.getX() + 0.5D;
        double centerZ = worldPosition.getZ() + 0.5D;
        return AABB.ofSize(new net.minecraft.world.phys.Vec3(centerX,
                worldPosition.getY() + collisionHeight * 0.5D, centerZ),
                collisionWidth, collisionHeight, collisionWidth).inflate(0.10D);
    }

    public UUID battleId() { return battleId; }
    public UUID ownerEntityId() { return ownerEntityId; }
    public UUID ownerPokemonId() { return ownerPokemonId; }
    public String speciesId() { return speciesId; }
    public String formId() { return formId; }
    public String aspectsCsv() { return aspectsCsv; }
    public String animationName() { return animationName; }
    public float statueYaw() { return statueYaw; }
    public boolean physical() { return physical; }
    public boolean effectZone() { return effectZone; }
    public boolean combined() { return physical && effectZone; }
    public int shellRemainingTicks() { return shellRemainingTicks; }
    public int zoneRemainingTicks() { return zoneRemainingTicks; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (battleId != null) tag.putUUID("Battle", battleId);
        if (ownerEntityId != null) tag.putUUID("OwnerEntity", ownerEntityId);
        if (ownerPokemonId != null) tag.putUUID("OwnerPokemon", ownerPokemonId);
        tag.putString("Species", speciesId);
        tag.putString("Form", formId);
        tag.putString("Aspects", aspectsCsv);
        tag.putString("Animation", animationName);
        tag.putFloat("Yaw", statueYaw);
        tag.putBoolean("Physical", physical);
        tag.putBoolean("EffectZone", effectZone);
        tag.putInt("ShellRemaining", shellRemainingTicks);
        tag.putInt("ZoneRemaining", zoneRemainingTicks);
        tag.putFloat("CollisionWidth", collisionWidth);
        tag.putFloat("CollisionHeight", collisionHeight);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battleId = tag.hasUUID("Battle") ? tag.getUUID("Battle") : null;
        ownerEntityId = tag.hasUUID("OwnerEntity") ? tag.getUUID("OwnerEntity") : null;
        ownerPokemonId = tag.hasUUID("OwnerPokemon") ? tag.getUUID("OwnerPokemon") : null;
        speciesId = tag.getString("Species");
        formId = tag.getString("Form");
        aspectsCsv = tag.getString("Aspects");
        animationName = tag.getString("Animation");
        statueYaw = tag.getFloat("Yaw");
        physical = tag.getBoolean("Physical");
        effectZone = tag.getBoolean("EffectZone");
        shellRemainingTicks = tag.getInt("ShellRemaining");
        zoneRemainingTicks = tag.getInt("ZoneRemaining");
        collisionWidth = Math.max(1.0F, tag.getFloat("CollisionWidth"));
        collisionHeight = Math.max(1.0F, tag.getFloat("CollisionHeight"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                                       HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }

    private static int remainingInt(long ticks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, ticks));
    }
}
