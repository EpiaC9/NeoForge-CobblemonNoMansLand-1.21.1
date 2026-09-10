package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.registry.ModBlockEntities;
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
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattleFairyIllusionBlockEntity extends BlockEntity {
    private UUID battleId;
    private UUID sourcePokemonId;
    private UUID copiedEntityId;
    private String speciesId = "cobblemon:missingno";
    private String formId = "normal";
    private String aspectsCsv = "";
    private String animationName = "stand";
    private float yaw;

    public ActionBattleFairyIllusionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACTION_BATTLE_FAIRY_ILLUSION.get(), pos, state);
    }

    public void initialize(UUID battleId, UUID sourcePokemonId, PokemonEntity copied) {
        this.battleId = battleId;
        this.sourcePokemonId = sourcePokemonId;
        copiedEntityId = copied.getUUID();
        copyVisual(copied);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        Entity raw = copiedEntityId != null ? server.getEntity(copiedEntityId) : null;
        if (!(raw instanceof PokemonEntity copied) || copied.isRemoved()) {
            ActionBattleFairyIllusionRuntime.remove(this);
            return;
        }
        copyVisual(copied);
        BlockPos desired = BlockPos.containing(copied.position().add(1.5D, 0.0D, 0.0D));
        if (!desired.equals(worldPosition)) ActionBattleFairyIllusionRuntime.move(this, desired);
    }

    private void copyVisual(PokemonEntity copied) {
        speciesId = copied.getPokemon().getSpecies().getResourceIdentifier().toString();
        formId = copied.getPokemon().getForm().getName();
        aspectsCsv = String.join(",", copied.getPokemon().getAspects());
        animationName = copied.getCurrentPoseType() == null ? "stand"
                : copied.getCurrentPoseType().name().toLowerCase(Locale.ROOT);
        yaw = copied.getYRot();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public UUID battleId() { return battleId; }
    public UUID sourcePokemonId() { return sourcePokemonId; }
    public UUID copiedEntityId() { return copiedEntityId; }
    public String speciesId() { return speciesId; }
    public String formId() { return formId; }
    public String aspectsCsv() { return aspectsCsv; }
    public String animationName() { return animationName; }
    public float yaw() { return yaw; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (battleId != null) tag.putUUID("Battle", battleId);
        if (sourcePokemonId != null) tag.putUUID("Source", sourcePokemonId);
        if (copiedEntityId != null) tag.putUUID("Copied", copiedEntityId);
        tag.putString("Species", speciesId); tag.putString("Form", formId); tag.putString("Aspects", aspectsCsv);
        tag.putString("Animation", animationName); tag.putFloat("Yaw", yaw);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battleId = tag.hasUUID("Battle") ? tag.getUUID("Battle") : null;
        sourcePokemonId = tag.hasUUID("Source") ? tag.getUUID("Source") : null;
        copiedEntityId = tag.hasUUID("Copied") ? tag.getUUID("Copied") : null;
        speciesId = tag.getString("Species"); formId = tag.getString("Form"); aspectsCsv = tag.getString("Aspects");
        animationName = tag.getString("Animation"); yaw = tag.getFloat("Yaw");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        if (packet.getTag() != null) loadAdditional(packet.getTag(), registries);
    }
}
