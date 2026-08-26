package net.epiac9.cobblemonnml.dimension.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects dungeon marker positions while a jigsaw structure piece is being
 * placed.
 *
 * <p>The old dungeon preparation path found marker blocks by scanning every
 * block position inside every generated piece bounding box. That makes marker
 * preparation scale with total dungeon volume. This tracker instead lets the
 * marker blocks announce themselves when Minecraft places them, so the final
 * room preparation step only needs to inspect known marker positions.</p>
 *
 * <p>Capture is deliberately enabled only around
 * {@code PoolElementStructurePiece.place(...)}. Marker blocks placed manually
 * while editing structures, or by unrelated gameplay, are therefore ignored.</p>
 */
public final class DungeonMarkerCapture {
    private static final Set<BlockPos> CAPTURED_POSITIONS = new LinkedHashSet<>();
    private static ServerLevel activeLevel;
    private static boolean capturing;

    /**
     * Clears marker state for a brand-new dungeon generation.
     */
    public static void reset() {
        capturing = false;
        activeLevel = null;
        CAPTURED_POSITIONS.clear();
    }

    /**
     * Enables capture for one structure-piece placement call.
     */
    public static void beginPiece( ServerLevel level ) {
        if (level == null) {
            return;
        }
        activeLevel = level;
        capturing = true;
    }

    /**
     * Stops capture after the current structure piece finishes placing.
     */
    public static void endPiece() {
        capturing = false;
        activeLevel = null;
    }

    /**
     * Called by DungeonMarkerBlock.onPlace().
     */
    public static void record( Level level, BlockPos pos ) {
        if (!capturing || level == null || pos == null || level != activeLevel) {
            return;
        }
        CAPTURED_POSITIONS.add( pos.immutable() );
    }

    /**
     * Returns a stable copy of every unique marker position captured during
     * the current dungeon generation.
     */
    public static List<BlockPos> snapshot() {
        return new ArrayList<>( CAPTURED_POSITIONS );
    }
    public static int size() {
        return CAPTURED_POSITIONS.size();
    }
}
