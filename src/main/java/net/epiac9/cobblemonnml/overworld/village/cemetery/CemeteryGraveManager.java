package net.epiac9.cobblemonnml.overworld.village.cemetery;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public final class CemeteryGraveManager {
    // GET OR ALLOCATE PLAYER PLOT
    public static BlockPos getOrAllocatePlayerPlot( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return null;
        }

        return CemeteryManager.getOrAssignPlayerPlot( level, playerId );
    }
    // PREPARE PLAYER PLOT FOR NEW GRAVE
    public static BlockPos preparePlayerPlotForNewGrave( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return null;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        if (!data.hasCemetery()) {
            return null;
        }

        /*
         * Retry allocation when the player does not currently
         * have a registered cemetery plot.
         */
        BlockPos plotPos = getOrAllocatePlayerPlot( level, playerId );

        if (plotPos == null) {
            return null;
        }

        /*
         * This method should only be called after the death
         * handler has confirmed that there are items to save.
         *
         * If an old grave exists, clearing this position will
         * remove it before the new grave is created.
         */
        clearPlotForNewGrave( level, plotPos );

        /*
         * The old grave is no longer considered active.
         * The YAGM creation step will mark the new grave active
         * after it has been successfully created.
         */
        data.setActiveGrave( playerId, false );

        return plotPos;
    }
    // MARK GRAVE CREATED
    public static void markGraveCreated( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        if (!data.hasPlayerPlot( playerId )) {
            return;
        }

        data.setActiveGrave( playerId, true );
    }
    // HAS ACTIVE GRAVE
    public static boolean hasActiveGrave( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return false;
        }

        return CemeterySavedData.get( level.getServer() )
                .hasActiveGrave( playerId );
    }
    // RESTORE PLAYER PLOT
    public static void restorePlayerPlot( ServerLevel level, UUID playerId ) {
        if (level == null || playerId == null) {
            return;
        }

        CemeterySavedData data = CemeterySavedData.get( level.getServer() );

        BlockPos plotPos = data.getPlayerPlot( playerId );

        if (plotPos == null) {
            return;
        }

        CemeteryManager.restorePlot( level, plotPos );

        /*
         * The grave is gone, but the player's persistent
         * ownership of this cemetery plot remains unchanged.
         */
        data.setActiveGrave( playerId, false );
    }
    // CLEAR PLOT FOR NEW GRAVE
    private static void clearPlotForNewGrave( ServerLevel level, BlockPos plotPos ) {
        /*
         * Clear the grave position itself.
         */
        if (!level .getBlockState( plotPos ) .isAir()) {
            level.destroyBlock( plotPos, false );
        }

        /*
         * Also clear the block immediately above the grave
         * position so nothing can obstruct the new grave.
         */
        if (!level .getBlockState( plotPos.above() ) .isAir()) {
            level.destroyBlock( plotPos.above(), false );
        }
    }
}
