package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.DungeonSlotManager;
import net.epiac9.cobblemonnml.dimension.generation.DungeonGenerationQueue;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;

import net.epiac9.cobblemonnml.dimension.network.DungeonCleanupToastPayload;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.util.RandomSource;

import net.minecraft.world.entity.item.ItemEntity;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.Level;

import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungeonPortalSelectionManager {
    // ARM DELAY
    /*
     * 20 ticks ≈ 1 second.
     * Portal requires 1 uninterrupted second after the activator / latest Tier or Theme input.
     */
    private static final long ARM_DELAY_TICKS = 10L;
    // RANDOM TIER CHANCES
    /*
     * These weights are only used when the player does NOT
     * provide a Tier selector item.
     * Manual Tier selection always overrides these values.
     * Tier 1 = 95%
     * Tier 2 =  5%
     * Tier 3 =  0%
     * Tier 4 =  0%
     * Total = 100
     */
    private static final int TIER_1_CHANCE = 95;
    private static final int TIER_2_CHANCE = 5;
    // PENDING SELECTIONS
    private static final Map<BlockPos, PortalSelection> SELECTIONS = new HashMap<>();
    private DungeonPortalSelectionManager() {
    }
    // SELECT THEME
    public static boolean selectTheme(
            ServerLevel overworld,
            BlockPos portalCenter,
            DungeonTheme theme,
            ItemStack sourceStack
    ) {
        if (overworld == null
                || portalCenter == null
                || theme == null
                || sourceStack == null
                || sourceStack.isEmpty()
                || DungeonSession.isActive()) {
            return false;
        }
        PortalSelection selection = getSelection( portalCenter );
        // SAME THEME ALREADY SELECTED
        if (selection.theme == theme) {
            restartTimerIfArmed( overworld, selection );
            return false;
        }
        // RETURN PREVIOUS GEM
        returnStoredItem( overworld, portalCenter, selection.themeItem );
        // STORE NEW THEME
        selection.theme = theme;
        selection.themeItem = copyOne( sourceStack );
        // UPDATE COLOUR IMMEDIATELY
        refreshPortalVisuals( overworld, portalCenter, selection );
        restartTimerIfArmed( overworld, selection );
        DebugLog.log( "Dungeon portal theme selected: " + theme.getDisplayName() );
        return true;
    }
    // SELECT TIER
    public static boolean selectTier(
            ServerLevel overworld,
            BlockPos portalCenter,
            DungeonTier tier,
            ItemStack sourceStack
    ) {
        if (overworld == null
                || portalCenter == null
                || tier == null
                || sourceStack == null
                || sourceStack.isEmpty()
                || DungeonSession.isActive()) {
            return false;
        }
        PortalSelection selection = getSelection( portalCenter );
        // SAME TIER ALREADY SELECTED
        if (selection.tier == tier) {
            restartTimerIfArmed( overworld, selection );
            return false;
        }
        // RETURN PREVIOUS TIER ITEM
        returnStoredItem( overworld, portalCenter, selection.tierItem );
        // STORE NEW TIER
        selection.tier = tier;
        selection.tierItem = copyOne( sourceStack );
        // UPDATE VISUALS IMMEDIATELY
        refreshPortalVisuals( overworld, portalCenter, selection );
        restartTimerIfArmed( overworld, selection );
        DebugLog.log( "Dungeon portal tier selected: " + tier.getDisplayName() );
        return true;
    }
    // FORCE SPECIAL ROOM
    public static boolean selectSpecialRoomForce(
            ServerLevel overworld,
            BlockPos portalCenter,
            ItemStack sourceStack
    ) {
        if (overworld == null
                || portalCenter == null
                || sourceStack == null
                || sourceStack.isEmpty()
                || DungeonSession.isActive()) {
            return false;
        }

        PortalSelection selection = getSelection( portalCenter );

        // Only one force item can be stored per pending portal.
        if (selection.forceSpecialRoom) {
            restartTimerIfArmed( overworld, selection );
            return false;
        }

        selection.forceSpecialRoom = true;
        selection.specialRoomItem = copyOne( sourceStack );
        restartTimerIfArmed( overworld, selection );
        DebugLog.log( "Dungeon portal special room force selected." );
        return true;
    }
    // ARM PORTAL
    public static boolean arm( ServerLevel overworld, BlockPos portalCenter, UUID ownerUUID, ItemStack sourceStack ) {
        if (overworld == null
                || portalCenter == null
                || sourceStack == null
                || sourceStack.isEmpty()
                || DungeonSession.isActive()) {
            return false;
        }
        BlockPos key = portalCenter.immutable();
        PortalSelection selection = getSelection( key );
        // ALREADY ARMED
        if (selection.armed) {
            return false;
        }
        // ANOTHER PORTAL IS ARMED
        if (hasOtherArmedPortal( key )) {
            DebugLog.log( "Another dungeon portal is already armed." );
            return false;
        }
        // STORE ACTIVATOR
        selection.activationItem = copyOne( sourceStack );
        selection.ownerUUID = ownerUUID;
        selection.armed = true;
        selection.readyAtGameTime =
                overworld.getGameTime()
                        + ARM_DELAY_TICKS;
        // START ANIMATION IMMEDIATELY
        refreshPortalVisuals( overworld, portalCenter, selection );
        DebugLog.log( "Dungeon portal ARMED." );
        DebugLog.log( "Waiting for 1 second of no new dungeon inputs." );
        return true;
    }
    // SERVER TICK
    public static void tick( MinecraftServer server ) {
        if (server == null || SELECTIONS.isEmpty() || DungeonSession.isActive()) {
            return;
        }
        ServerLevel overworld = server.getLevel( Level.OVERWORLD );
        if (overworld == null) {
            return;
        }
        List<BlockPos> ready = getReady(overworld);
        for (BlockPos portalCenter : ready) {
            if (DungeonSession.isActive()) {
                break;
            }
            finalizePortal( server, overworld, portalCenter );
        }
    }
    private static @NotNull List<BlockPos> getReady(ServerLevel overworld) {
        long currentGameTime = overworld.getGameTime();
        List<BlockPos> ready = new ArrayList<>();
        for (Map.Entry<BlockPos, PortalSelection> entry : SELECTIONS.entrySet()) {
            PortalSelection selection = entry.getValue();
            if (!selection.armed) {
                continue;
            }
            if (currentGameTime < selection.readyAtGameTime) {
                continue;
            }
            ready.add( entry.getKey() );
        }
        return ready;
    }
    // FINALIZE PORTAL
    private static void finalizePortal( MinecraftServer server, ServerLevel overworld, BlockPos portalCenter ) {
        PortalSelection selection = SELECTIONS.get( portalCenter );
        if (selection == null || !selection.armed) {
            return;
        }
        // COMPLETE CORE STILL PRESENT?
        if (DungeonPortalManager .findCoreCenter( overworld, portalCenter ) == null) {
            returnEverything( overworld, portalCenter, selection );
            SELECTIONS.remove( portalCenter );
            return;
        }
        // FIND AVAILABLE DUNGEON SLOT
        if (!DungeonSlotManager .selectNextAvailableSlot()) {
            sendAllBusyToast( server );
            cancelArmingKeepSelectors( overworld, portalCenter, selection );
            return;
        }
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension.DUNGEON_DIMENSION );
        if (dungeonLevel == null) {
            cancelArmingKeepSelectors( overworld, portalCenter, selection );
            return;
        }
        RandomSource random = overworld.getRandom();
        // RESOLVE THEME
        DungeonTheme resolvedTheme = selection.theme;
        if (resolvedTheme == null) {
            resolvedTheme = DungeonTheme.getRandom( random );
            DebugLog.log( "No theme selected. Random theme: " + resolvedTheme.getDisplayName() );
        }
        // RESOLVE TIER
        DungeonTier resolvedTier = selection.tier;
        if (resolvedTier == null) {
            resolvedTier = getWeightedRandomTier( random );
            DebugLog.log( "No tier selected. Weighted random tier: " + resolvedTier.getDisplayName() );
        }
        // SHOW RESOLVED RESULT, BUT KEEP PORTAL LOCKED
        /*
         * Keep the portal as the non-teleporting core block while
         * dungeon generation is running. ACTIVATED=false prevents
         * the activation texture/animation from playing. The tier
         * and theme can still be shown as a static preview.
         */
        DungeonPortalManager
                .updateCoreVisuals( overworld, portalCenter, false, resolvedTier, resolvedTheme );
        // START SESSION
        DungeonSession.start( resolvedTheme, resolvedTier, selection.ownerUUID );
        // PASS SPECIAL ROOM FORCE INTO THIS GENERATION
        DungeonGenerationQueue.setForceSpecialRoomForNextGeneration( selection.forceSpecialRoom );
        // GENERATE DUNGEON
        boolean generated =
                DungeonDimension
                        .generateJigsawDungeon( dungeonLevel, overworld, portalCenter, resolvedTier );
        // GENERATION FAILED
        if (!generated) {
            DungeonGenerationQueue.setForceSpecialRoomForNextGeneration( false );
            DungeonSession.end();
            cancelArmingKeepSelectors( overworld, portalCenter, selection );
            return;
        }
        // INPUTS ARE NOW CONSUMED
        SELECTIONS.remove(portalCenter);
        DebugLog.log(
                "Dungeon generation started. Portal locked until ready: "
                        + resolvedTheme.getDisplayName()
                        + " / "
                        + resolvedTier.getDisplayName()
        );
    }
    // WEIGHTED RANDOM TIER
    private static DungeonTier getWeightedRandomTier( RandomSource random ) {

        /*
         * Roll range:
         *  0 - 94 = Tier 1 = 95%
         * 95 - 99 = Tier 2 =  5%
         *
         * Tier 3 and Tier 4 can still be selected manually,
         * but they are not available through random tier generation.
         */
        int roll = random.nextInt( 100 );
        if (roll < TIER_1_CHANCE) {
            return DungeonTier.TIER_1;
        }
        if (roll < TIER_1_CHANCE + TIER_2_CHANCE) {
            return DungeonTier.TIER_2;
        }

        /*
         * The configured random-tier weights total 100, so this
         * fallback should be unreachable. Keep Tier 2 as the safe
         * non-Tier-3/4 fallback if the constants are changed later.
         */
        return DungeonTier.TIER_2;
    }
    // REFRESH CORE VISUALS
    private static void refreshPortalVisuals(
            ServerLevel overworld,
            BlockPos portalCenter,
            PortalSelection selection
    ) {
        /*
         * Arming is a server-side preparation state, not a visual
         * portal-active state. Keep ACTIVATED=false for the entire
         * selection/arming/generation lifecycle. The real activation
         * animation begins only when DungeonGenerationQueue finishes
         * and replaces the core with DungeonPortalBlock.
         */
        DungeonPortalManager
                .updateCoreVisuals( overworld, portalCenter, false, selection.tier, selection.theme );
    }
    // GET SELECTION
    private static PortalSelection getSelection( BlockPos portalCenter ) {
        BlockPos key = portalCenter.immutable();
        return SELECTIONS.computeIfAbsent( key, ignored -> new PortalSelection() );
    }
    // RESTART ARM TIMER
    private static void restartTimerIfArmed( ServerLevel overworld, PortalSelection selection ) {
        if (!selection.armed) {
            return;
        }
        selection.readyAtGameTime =
                overworld.getGameTime()
                        + ARM_DELAY_TICKS;
        DebugLog.log( "Dungeon input received while armed." );
        DebugLog.log( "1-second quiet period restarted." );
    }
    // OTHER PORTAL ARMED?
    private static boolean hasOtherArmedPortal( BlockPos portalCenter ) {
        for (Map.Entry<BlockPos, PortalSelection> entry : SELECTIONS.entrySet()) {
            if (entry .getKey() .equals( portalCenter )) {
                continue;
            }
            if (entry .getValue() .armed) {
                return true;
            }
        }
        return false;
    }
    // CANCEL ARMING, KEEP TIER/THEME
    private static void cancelArmingKeepSelectors(
            ServerLevel overworld,
            BlockPos portalCenter,
            PortalSelection selection
    ) {
        returnStoredItem( overworld, portalCenter, selection.activationItem );
        selection.activationItem = ItemStack.EMPTY;
        selection.ownerUUID = null;
        selection.armed = false;
        selection.readyAtGameTime = 0L;
        // STOP ANIMATION, KEEP COLOUR/TIER
        refreshPortalVisuals( overworld, portalCenter, selection );
        DebugLog.log( "Dungeon activator returned." );
        DebugLog.log( "Pending Tier/Theme/Special Room selections preserved." );
    }
    // SERVER STOP
    public static void returnAllPendingItems( MinecraftServer server ) {
        if (server == null) {
            SELECTIONS.clear();
            return;
        }
        ServerLevel overworld = server.getLevel( Level.OVERWORLD );
        if (overworld == null) {
            SELECTIONS.clear();
            return;
        }
        for (Map.Entry<BlockPos, PortalSelection> entry : SELECTIONS.entrySet()) {
            BlockPos portalCenter = entry.getKey();
            PortalSelection selection = entry.getValue();
            returnEverything( overworld, portalCenter, selection );

            /*
             * Because the actual blockstate is persistent, clear its visual state before clearing the in-memory selection.
             */
            DungeonPortalManager
                    .updateCoreVisuals( overworld, portalCenter, false, null, null );
        }
        SELECTIONS.clear();
    }
    // RETURN EVERYTHING
    private static void returnEverything( ServerLevel overworld, BlockPos portalCenter, PortalSelection selection ) {
        returnStoredItem( overworld, portalCenter, selection.themeItem );
        returnStoredItem( overworld, portalCenter, selection.tierItem );
        returnStoredItem( overworld, portalCenter, selection.specialRoomItem );
        returnStoredItem( overworld, portalCenter, selection.activationItem );
        selection.themeItem = ItemStack.EMPTY;
        selection.tierItem = ItemStack.EMPTY;
        selection.specialRoomItem = ItemStack.EMPTY;
        selection.activationItem = ItemStack.EMPTY;
        selection.forceSpecialRoom = false;
    }
    // RETURN STORED ITEM
    private static void returnStoredItem( ServerLevel overworld, BlockPos portalCenter, ItemStack stack ) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemEntity itemEntity =
                new ItemEntity(
                        overworld,
                        portalCenter.getX()
                                + 2.5D,
                        portalCenter.getY()
                                + 1.25D,
                        portalCenter.getZ()
                                + 0.5D,
                        stack.copy()
                );
        itemEntity.setDeltaMovement( 0.15D, 0.25D, 0.0D );
        overworld.addFreshEntity( itemEntity );
    }
    // COPY ONE
    private static ItemStack copyOne( ItemStack stack ) {
        ItemStack copy = stack.copy();
        copy.setCount( 1 );
        return copy;
    }
    // ALL SLOTS BUSY
    private static void sendAllBusyToast( MinecraftServer server ) {
        DungeonCleanupToastPayload payload = new DungeonCleanupToastPayload( "", "ALL_BUSY" );
        for (ServerPlayer player : server .getPlayerList() .getPlayers()) {
            PacketDistributor.sendToPlayer( player, payload );
        }
    }
    // PENDING PORTAL STATE
    private static final class PortalSelection {
        private DungeonTheme theme = null;
        private DungeonTier tier = null;
        private ItemStack themeItem = ItemStack.EMPTY;
        private ItemStack tierItem = ItemStack.EMPTY;
        private ItemStack specialRoomItem = ItemStack.EMPTY;
        private ItemStack activationItem = ItemStack.EMPTY;
        private boolean forceSpecialRoom = false;
        private UUID ownerUUID = null;
        private boolean armed = false;
        private long readyAtGameTime = 0L;
    }
}
