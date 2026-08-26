package net.epiac9.cobblemonnml.overworld.village.overworld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public final class OverworldPortalSavedData extends SavedData {
    private static final String DATA_NAME = "cobblemonnml_overworld_portal";
    private boolean generated;
    private BlockPos portalPos;
    // CREATE
    public static OverworldPortalSavedData create() {
        return new OverworldPortalSavedData();
    }
    // LOAD
    public static OverworldPortalSavedData load( CompoundTag tag, HolderLookup.Provider registries ) {
        OverworldPortalSavedData data = new OverworldPortalSavedData();
        data.generated = tag.getBoolean( "Generated" );
        if (tag.contains( "PortalX" ) && tag.contains( "PortalY" ) && tag.contains( "PortalZ" )) {
            data.portalPos = new BlockPos( tag.getInt( "PortalX" ), tag.getInt( "PortalY" ), tag.getInt( "PortalZ" ) );
        }
        return data;
    }
    // GET
    public static OverworldPortalSavedData get(ServerLevel overworld) {
        return overworld
                .getDataStorage()
                .computeIfAbsent(
                        new Factory<>( OverworldPortalSavedData::create, OverworldPortalSavedData::load ),
                        DATA_NAME
                );
    }
    // GENERATED
    public boolean isGenerated() {
        return generated;
    }
    // PORTAL POSITION
    public BlockPos getPortalPos() {
        return portalPos;
    }
    // SET PORTAL
    public void setPortal(BlockPos portalPos) {
        if (portalPos == null) {
            return;
        }
        this.generated = true;
        this.portalPos = portalPos.immutable();
        setDirty();
    }
    // SAVE
    @Override
    public @NotNull CompoundTag save( CompoundTag tag, HolderLookup.@NotNull Provider registries ) {
        tag.putBoolean( "Generated", generated );
        if (portalPos != null) {
            tag.putInt( "PortalX", portalPos.getX() );
            tag.putInt( "PortalY", portalPos.getY() );
            tag.putInt( "PortalZ", portalPos.getZ() );
        }
        return tag;
    }
}
