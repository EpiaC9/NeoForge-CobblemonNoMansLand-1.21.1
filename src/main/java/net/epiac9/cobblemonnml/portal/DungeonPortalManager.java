package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.registry.ModAttachments;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public final class DungeonPortalManager {
    private static final Set<BlockPos> ACTIVE_PORTAL_CENTERS = new HashSet<>();
    // FIND CORE CENTER
    public static BlockPos findCoreCenter( ServerLevel level, BlockPos touchedPos ) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                BlockPos candidate = touchedPos.offset( offsetX, 0, offsetZ );
                if (isCoreSquare( level, candidate )) {
                    return candidate.immutable();
                }
            }
        }
        return null;
    }
    private static boolean isCoreSquare( ServerLevel level, BlockPos center ) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos checkPos = center.offset( x, 0, z );
                if (!level.getBlockState( checkPos ).is( ModBlocks.DUNGEON_PORTAL_CORE.get() )) {
                    return false;
                }
            }
        }
        return true;
    }
    // UPDATE CORE VISUALS
    public static void updateCoreVisuals(
            ServerLevel level,
            BlockPos center,
            boolean activated,
            DungeonTier tier,
            DungeonTheme theme
    ) {
        if (level == null || center == null) {
            return;
        }
        int tierIndex = DungeonPortalVisualState.tierIndex( tier );
        int themeIndex = DungeonPortalVisualState.themeIndex( theme );
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos portalPos = center.offset( x, 0, z );
                BlockState state = level.getBlockState( portalPos );
                if (!state.is(ModBlocks.DUNGEON_PORTAL_CORE.get())) {
                    continue;
                }
                int cellIndex = DungeonPortalVisualState.cellIndex( x, z );
                BlockState newState =
                        state
                                .setValue( DungeonPortalVisualState.ACTIVATED, activated )
                                .setValue( DungeonPortalVisualState.TIER, tierIndex )
                                .setValue( DungeonPortalVisualState.THEME, themeIndex )
                                .setValue( DungeonPortalVisualState.CELL, cellIndex );
                level.setBlock( portalPos, newState, 3 );
            }
        }
    }
    // FIND ACTIVE PORTAL CENTER
    public static BlockPos findActivePortalCenter( ServerLevel level, BlockPos touchedPos ) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                BlockPos candidate = touchedPos.offset( offsetX, 0, offsetZ );
                if (isActivePortalSquare( level, candidate )) {
                    return candidate.immutable();
                }
            }
        }
        return null;
    }

    private static boolean isActivePortalSquare( ServerLevel level, BlockPos center ) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockState state = level.getBlockState( center.offset( x, 0, z ) );
                if (isPortalBlock(state)) {
                    return false;
                }
            }
        }
        return true;
    }
    private static boolean isPortalBlock(BlockState state) {
        return !state.is( ModBlocks.DUNGEON_PORTAL.get() );
    }
    // ACTIVATE PORTAL
    public static void activatePortal( ServerLevel level, BlockPos center, DungeonTier tier, DungeonTheme theme ) {
        if (level == null || center == null || tier == null || theme == null) {
            return;
        }
        int tierIndex =
                DungeonPortalVisualState
                        .tierIndex( tier );
        int themeIndex =
                DungeonPortalVisualState
                        .themeIndex( theme );
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos portalPos = center.offset( x, 0, z );
                int cellIndex =
                        DungeonPortalVisualState
                                .cellIndex( x, z );
                BlockState oldCoreState =
                        level.getBlockState( portalPos );

                boolean waterlogged =
                        oldCoreState.is( ModBlocks.DUNGEON_PORTAL_CORE.get() )
                                && oldCoreState.getValue( DungeonPortalCoreBlock.WATERLOGGED );

                BlockState portalState =
                        ModBlocks.DUNGEON_PORTAL
                                .get()
                                .defaultBlockState()
                                .setValue( DungeonPortalVisualState.ACTIVATED, true )
                                .setValue( DungeonPortalVisualState.TIER, tierIndex )
                                .setValue( DungeonPortalVisualState.THEME, themeIndex )
                                .setValue( DungeonPortalVisualState.CELL, cellIndex )
                                .setValue( DungeonPortalBlock.WATERLOGGED, waterlogged );
                level.setBlock( portalPos, portalState, 3 );
            }
        }
        ACTIVE_PORTAL_CENTERS.add( center.immutable() );
        DebugLog.log( "Activated " + theme.getDisplayName() + " " + tier.getDisplayName() + " portal at " + center );
    }
    // DEACTIVATE PORTAL
    public static void deactivatePortal( ServerLevel level, BlockPos center ) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos portalPos = center.offset( x, 0, z );
                BlockState state = level.getBlockState( portalPos );
                if (isPortalBlock(state)) {
                    continue;
                }
                int cellIndex = DungeonPortalVisualState.cellIndex( x, z );
                boolean waterlogged =
                        state.getValue( DungeonPortalBlock.WATERLOGGED );

                BlockState coreState =
                        ModBlocks.DUNGEON_PORTAL_CORE
                                .get()
                                .defaultBlockState()
                                .setValue( DungeonPortalVisualState.ACTIVATED, false )
                                .setValue( DungeonPortalVisualState.TIER, 0 )
                                .setValue( DungeonPortalVisualState.THEME, 0 )
                                .setValue( DungeonPortalVisualState.CELL, cellIndex )
                                .setValue( DungeonPortalCoreBlock.WATERLOGGED, waterlogged );
                level.setBlock( portalPos, coreState, 3 );
            }
        }
        ACTIVE_PORTAL_CENTERS.remove( center );
        DebugLog.log( "Deactivated dungeon portal at " + center );
    }
    // DEACTIVATE TRACKED PORTALS
    public static void deactivateTrackedPortals(MinecraftServer server) {
        ServerLevel overworld = server.getLevel( Level.OVERWORLD );
        if (overworld == null) {
            return;
        }
        Set<BlockPos> portalCenters = new HashSet<>( ACTIVE_PORTAL_CENTERS );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasData(ModAttachments.PORTAL_CENTER)) {
                portalCenters.add( player.getData( ModAttachments.PORTAL_CENTER ) );
            }
        }
        for (BlockPos center : portalCenters) {
            deactivatePortal( overworld, center );
        }
        ACTIVE_PORTAL_CENTERS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeData( ModAttachments.PORTAL_CENTER );
            player.removeData( ModAttachments.RETURN_POSITION );
        }
    }
    // CLEAR MEMORY
    public static void clearTrackedPortalMemory() {
        ACTIVE_PORTAL_CENTERS.clear();
    }
}
