package net.epiac9.cobblemonnml.dimension;

import net.epiac9.cobblemonnml.dimension.reset.DungeonResetQueue;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;

public final class DungeonSlotManager {
    // SLOTS
    public enum Slot {
        A,
        B,
        C,
        D
    }
    // SLOT ORIGINS
    /*
     * Each dungeon slot is separated by 2048 blocks.
     * A =     0
     * B =  2048
     * C =  4096
     * D =  6144
     * This keeps every dungeon far enough away from the others while allowing old slots to clean up in the background.
     */
    private static final BlockPos SLOT_A_ORIGIN = new BlockPos( 0, 60, 0 );
    private static final BlockPos SLOT_B_ORIGIN = new BlockPos( 2048, 60, 0 );
    private static final BlockPos SLOT_C_ORIGIN = new BlockPos( 4096, 60, 0 );
    private static final BlockPos SLOT_D_ORIGIN = new BlockPos( 6144, 60, 0 );
    // CURRENT SLOT
    private static Slot currentSlot = Slot.A;
    // GET CURRENT SLOT
    public static Slot getCurrentSlot() {
        return currentSlot;
    }
    // GET CURRENT ORIGIN
    public static BlockPos getCurrentOrigin() {
        return getOrigin( currentSlot );
    }
    // GET ORIGIN FOR SLOT
    public static BlockPos getOrigin( Slot slot ) {
        return switch (slot) {
            case A -> SLOT_A_ORIGIN;
            case B -> SLOT_B_ORIGIN;
            case C -> SLOT_C_ORIGIN;
            case D -> SLOT_D_ORIGIN;
        };
    }
    // GET NEXT SLOT
    public static Slot getNextSlot(Slot slot) {
        return switch (slot) {
            case A -> Slot.B;
            case B -> Slot.C;
            case C -> Slot.D;
            case D -> Slot.A;
        };
    }
    // LEGACY NAME
    /*
     * Keep this method for compatibility with any existing classes that still call getOtherSlot().
     * It now means "get the next slot in the rotation".
     */
    public static Slot getOtherSlot(Slot slot) {
        return getNextSlot( slot );
    }
    // SWITCH TO NEXT SLOT
    /*
     * Rotation:
     * A -> B -> C -> D -> A
     * If the next slot is still resetting,
     * DungeonPortalCoreBlock can prevent activation
     * until that slot becomes ready.
     */
    public static void switchToOtherSlot() {
        currentSlot = getNextSlot( currentSlot );
        DebugLog.log( "Next dungeon slot: " + currentSlot );
    }
    public static Slot getNextAvailableSlot() {
        Slot candidate = currentSlot;

        /*
         * Check at most all four slots.
         * Start with the currently selected slot because it may already be ready.
         */
        for (int i = 0; i < Slot.values().length; i++) {
            if (!DungeonResetQueue.isResetting(candidate)) {
                return candidate;
            }
            candidate = getNextSlot( candidate );
        }
        /*
         * Every slot is currently resetting.
         */
        return null;
    }
// SELECT NEXT AVAILABLE SLOT
    public static boolean selectNextAvailableSlot() {
        Slot available = getNextAvailableSlot();
        if (available == null) {
            DebugLog.log( "No dungeon slots are currently available." );
            return false;
        }
        if (available != currentSlot) {
            DebugLog.log(
                    "Skipping unavailable dungeon slot "
                            + currentSlot
                            + ". Using slot "
                            + available
                            + " instead."
            );
        }
        currentSlot = available;
        return true;
    }
}
