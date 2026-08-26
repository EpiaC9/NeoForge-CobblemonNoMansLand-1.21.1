package net.epiac9.cobblemonnml.overworld.village.cemetery;

import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CemeteryManager {
    // PROCESS GENERATED CEMETERY
    public static int processGeneratedCemetery( ServerLevel level, BlockPos cemeteryOrigin, BoundingBox bounds ) {
        if (level == null || cemeteryOrigin == null || bounds == null) {
            return 0;
        }

        List<BlockPos> gravePlots = findGraveMarkers( level, bounds );

        if (gravePlots.isEmpty()) {
            return 0;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );
        // REGISTER CEMETERY
        data.setCemetery( cemeteryOrigin, gravePlots );
        // REPLACE MARKERS WITH EMPTY PLOTS
        for (BlockPos plotPos : gravePlots) {
            level.setBlock( plotPos, ModBlocks.GRAVE_PLOT .get() .defaultBlockState(), 3 );
        }
        // ASSIGN ONLINE PLAYERS
        registerOnlinePlayers( level );

        return gravePlots.size();
    }
    // REGISTER ONLINE PLAYERS
    public static void registerOnlinePlayers( ServerLevel level ) {
        if (level == null) {
            return;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        if (!data.hasCemetery()) {
            return;
        }

        for (ServerPlayer player : level.getServer() .getPlayerList() .getPlayers()) {
            data.assignPlayerPlot( player.getUUID() );
        }
    }
    // GET OR ASSIGN PLAYER PLOT
    public static BlockPos getOrAssignPlayerPlot( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return null;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        if (!data.hasCemetery()) {
            return null;
        }

        /*
         * First try the player's persistent assignment.
         */
        BlockPos plotPos = data.getPlayerPlot( playerId );

        if (plotPos != null) {
            return plotPos;
        }

        /*
         * The player does not currently own a plot.
         * Retry allocation from any unassigned cemetery plot.
         */
        return data.assignPlayerPlot( playerId );
    }
    // HAS CEMETERY
    public static boolean hasCemetery( ServerLevel level ) {
        if (level == null) {
            return false;
        }

        return CemeterySavedData.get( level.getServer() )
                .hasCemetery();
    }
    // FIND GRAVE MARKERS
    private static List<BlockPos> findGraveMarkers( ServerLevel level, BoundingBox bounds ) {
        List<BlockPos> result = new ArrayList<>();

        BlockPos min = new BlockPos( bounds.minX(), bounds.minY(), bounds.minZ() );

        BlockPos max = new BlockPos( bounds.maxX(), bounds.maxY(), bounds.maxZ() );

        for (BlockPos mutablePos : BlockPos.betweenClosed( min, max )) {
            if (!level .getBlockState( mutablePos ) .is( ModBlocks.GRAVE_MARKER.get() )) {
                continue;
            }

            result.add( mutablePos.immutable() );
        }

        return result;
    }
    // RESTORE EMPTY PLOT
    public static void restorePlot( ServerLevel level, BlockPos plotPos ) {
        if (level == null || plotPos == null) {
            return;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        /*
         * Only restore positions that belong to the
         * registered cemetery.
         */
        if (!data.hasGravePlot( plotPos )) {
            return;
        }
        // CLEAR GRAVE POSITION
        if (!level .getBlockState( plotPos ) .isAir()) {
            level.destroyBlock( plotPos, false );
        }
        // CLEAR BLOCK ABOVE
        if (!level .getBlockState( plotPos.above() ) .isAir()) {
            level.destroyBlock( plotPos.above(), false );
        }
        // RESTORE EMPTY GRAVE PLOT
        level.setBlock( plotPos, ModBlocks.GRAVE_PLOT .get() .defaultBlockState(), 3 );
    }
}
