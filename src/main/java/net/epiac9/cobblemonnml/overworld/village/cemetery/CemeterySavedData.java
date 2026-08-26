package net.epiac9.cobblemonnml.overworld.village.cemetery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CemeterySavedData extends SavedData {
    private static final String DATA_NAME = "cobblemonnml_cemetery";

    private static final Factory<CemeterySavedData> FACTORY =
            new Factory<>( CemeterySavedData::new, CemeterySavedData::load );

    /*
     * All valid cemetery grave plot positions.
     */
    private final List<BlockPos> gravePlots = new ArrayList<>();

    /*
     * Persistent player -> grave plot assignments.
     *
     * A player keeps the same cemetery plot even after
     * successfully claiming their grave.
     */
    private final Map<UUID, BlockPos> playerPlots = new HashMap<>();

    /*
     * Players that currently have an active grave
     * occupying their assigned cemetery plot.
     */
    private final Set<UUID> activeGraves = new HashSet<>();

    /*
     * Optional cemetery origin.
     */
    private BlockPos cemeteryOrigin;
    // GET
    public static CemeterySavedData get( MinecraftServer server ) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent( FACTORY, DATA_NAME );
    }
    // CEMETERY
    public boolean hasCemetery() {
        return cemeteryOrigin != null
                && !gravePlots.isEmpty();
    }

    public BlockPos getCemeteryOrigin() {
        if (cemeteryOrigin == null) {
            return null;
        }

        return cemeteryOrigin.immutable();
    }

    public void setCemetery( BlockPos origin, List<BlockPos> positions ) {
        if (origin == null || positions == null || positions.isEmpty()) {
            return;
        }

        boolean sameCemetery =
                cemeteryOrigin != null
                        && cemeteryOrigin.equals( origin );

        cemeteryOrigin = origin.immutable();

        gravePlots.clear();

        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }

            BlockPos immutablePos = pos.immutable();

            if (gravePlots.contains( immutablePos )) {
                continue;
            }

            gravePlots.add( immutablePos );
        }

        /*
         * A completely different cemetery invalidates all
         * previous player assignments and active grave state.
         */
        if (!sameCemetery) {
            playerPlots.clear();
            activeGraves.clear();
            setDirty();
            return;
        }

        /*
         * If the same cemetery was rescanned, preserve valid
         * player assignments but remove assignments to plots
         * that no longer exist.
         */
        Set<UUID> invalidAssignments = new HashSet<>();

        for (Map.Entry<UUID, BlockPos> entry : playerPlots.entrySet()) {
            if (gravePlots.contains( entry.getValue() )) {
                continue;
            }

            invalidAssignments.add( entry.getKey() );
        }

        for (UUID playerId : invalidAssignments) {
            playerPlots.remove( playerId );

            activeGraves.remove( playerId );
        }

        setDirty();
    }
    // GRAVE PLOTS
    public List<BlockPos> getGravePlots() {
        List<BlockPos> result = new ArrayList<>();

        for (BlockPos pos : gravePlots) {
            result.add( pos.immutable() );
        }

        return result;
    }

    public boolean hasGravePlot( BlockPos pos ) {
        if (pos == null) {
            return false;
        }

        return gravePlots.contains( pos );
    }

    public int getGravePlotCount() {
        return gravePlots.size();
    }
    // PLAYER PLOT ASSIGNMENT
    public BlockPos getPlayerPlot( UUID playerId ) {
        if (playerId == null) {
            return null;
        }

        BlockPos plotPos = playerPlots.get( playerId );

        if (plotPos == null) {
            return null;
        }

        if (!gravePlots.contains( plotPos )) {
            playerPlots.remove( playerId );

            activeGraves.remove( playerId );

            setDirty();

            return null;
        }

        return plotPos.immutable();
    }

    public boolean hasPlayerPlot( UUID playerId ) {
        return getPlayerPlot( playerId ) != null;
    }

    public BlockPos assignPlayerPlot( UUID playerId ) {
        if (playerId == null || !hasCemetery()) {
            return null;
        }

        BlockPos existingPlot = getPlayerPlot( playerId );

        if (existingPlot != null) {
            return existingPlot;
        }

        Set<BlockPos> assignedPlots = new HashSet<>( playerPlots.values() );

        for (BlockPos plotPos : gravePlots) {
            if (assignedPlots.contains( plotPos )) {
                continue;
            }

            playerPlots.put( playerId, plotPos.immutable() );

            setDirty();

            return plotPos.immutable();
        }

        return null;
    }

    public boolean isPlotAssigned( BlockPos plotPos ) {
        if (plotPos == null) {
            return false;
        }

        return playerPlots.containsValue( plotPos );
    }

    public UUID getPlayerForPlot( BlockPos plotPos ) {
        if (plotPos == null) {
            return null;
        }

        for (Map.Entry<UUID, BlockPos> entry : playerPlots.entrySet()) {
            if (!entry.getValue().equals( plotPos )) {
                continue;
            }

            return entry.getKey();
        }

        return null;
    }

    public int getAssignedPlayerCount() {
        return playerPlots.size();
    }

    public List<BlockPos> getUnassignedGravePlots() {
        List<BlockPos> result = new ArrayList<>();

        Set<BlockPos> assignedPlots = new HashSet<>( playerPlots.values() );

        for (BlockPos plotPos : gravePlots) {
            if (assignedPlots.contains( plotPos )) {
                continue;
            }

            result.add( plotPos.immutable() );
        }

        return result;
    }
    // ACTIVE GRAVES
    public boolean hasActiveGrave( UUID playerId ) {
        if (playerId == null) {
            return false;
        }

        return activeGraves.contains( playerId );
    }

    public void setActiveGrave( UUID playerId, boolean active ) {
        if (playerId == null) {
            return;
        }

        boolean changed;

        if (active) {
            /*
             * A player cannot have an active cemetery grave
             * without first owning a valid cemetery plot.
             */
            if (!hasPlayerPlot( playerId )) {
                return;
            }

            changed = activeGraves.add( playerId );
        } else {
            changed = activeGraves.remove( playerId );
        }

        if (changed) {
            setDirty();
        }
    }
    // CLEAR
    public void clearAll() {
        gravePlots.clear();
        playerPlots.clear();
        activeGraves.clear();

        cemeteryOrigin = null;

        setDirty();
    }
    // SAVE
    @Override
    public @NotNull CompoundTag save( @NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries ) {
        if (cemeteryOrigin != null) {
            tag.putLong( "CemeteryOrigin", cemeteryOrigin.asLong() );
        }
        // GRAVE PLOTS
        ListTag gravePlotList = new ListTag();

        for (BlockPos pos : gravePlots) {
            CompoundTag posTag = new CompoundTag();

            posTag.putLong( "Pos", pos.asLong() );

            gravePlotList.add( posTag );
        }

        tag.put( "GravePlots", gravePlotList );
        // PLAYER PLOTS
        ListTag playerPlotList = new ListTag();

        for (Map.Entry<UUID, BlockPos> entry : playerPlots.entrySet()) {
            CompoundTag playerTag = new CompoundTag();

            playerTag.putUUID( "Player", entry.getKey() );

            playerTag.putLong( "Pos", entry.getValue().asLong() );

            playerPlotList.add( playerTag );
        }

        tag.put( "PlayerPlots", playerPlotList );
        // ACTIVE GRAVES
        ListTag activeGraveList = new ListTag();

        for (UUID playerId : activeGraves) {
            CompoundTag playerTag = new CompoundTag();

            playerTag.putUUID( "Player", playerId );

            activeGraveList.add( playerTag );
        }

        tag.put( "ActiveGraves", activeGraveList );

        return tag;
    }
    // LOAD
    private static CemeterySavedData load( CompoundTag tag, HolderLookup.Provider registries ) {
        CemeterySavedData data = new CemeterySavedData();
        // CEMETERY ORIGIN
        if (tag.contains( "CemeteryOrigin", Tag.TAG_LONG )) {
            data.cemeteryOrigin = BlockPos.of( tag.getLong( "CemeteryOrigin" ) );
        }
        // GRAVE PLOTS
        ListTag gravePlotList = tag.getList( "GravePlots", Tag.TAG_COMPOUND );

        for (int i = 0; i < gravePlotList.size(); i++) {
            CompoundTag posTag = gravePlotList.getCompound( i );

            if (!posTag.contains( "Pos", Tag.TAG_LONG )) {
                continue;
            }

            BlockPos pos = BlockPos.of( posTag.getLong( "Pos" ) );

            if (data.gravePlots.contains( pos )) {
                continue;
            }

            data.gravePlots.add( pos );
        }
        // PLAYER PLOTS
        ListTag playerPlotList = tag.getList( "PlayerPlots", Tag.TAG_COMPOUND );

        for (int i = 0; i < playerPlotList.size(); i++) {
            CompoundTag playerTag = playerPlotList.getCompound( i );

            if (!playerTag.hasUUID( "Player" )) {
                continue;
            }

            if (!playerTag.contains( "Pos", Tag.TAG_LONG )) {
                continue;
            }

            UUID playerId = playerTag.getUUID( "Player" );

            BlockPos plotPos = BlockPos.of( playerTag.getLong( "Pos" ) );

            /*
             * Only restore assignments to valid cemetery plots.
             */
            if (!data.gravePlots.contains( plotPos )) {
                continue;
            }

            /*
             * Do not allow two players to restore ownership
             * of the same cemetery plot.
             */
            if (data.playerPlots.containsValue( plotPos )) {
                continue;
            }

            data.playerPlots.put( playerId, plotPos );
        }
        // ACTIVE GRAVES
        ListTag activeGraveList = tag.getList( "ActiveGraves", Tag.TAG_COMPOUND );

        for (int i = 0; i < activeGraveList.size(); i++) {
            CompoundTag playerTag = activeGraveList.getCompound( i );

            if (!playerTag.hasUUID( "Player" )) {
                continue;
            }

            UUID playerId = playerTag.getUUID( "Player" );

            /*
             * Only restore an active grave state when the
             * player still owns a valid cemetery plot.
             */
            if (!data.playerPlots.containsKey( playerId )) {
                continue;
            }

            data.activeGraves.add( playerId );
        }

        return data;
    }
}
