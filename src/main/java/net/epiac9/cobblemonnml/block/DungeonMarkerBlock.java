package net.epiac9.cobblemonnml.block;

import net.epiac9.cobblemonnml.dimension.encounter.DungeonMarkerCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class DungeonMarkerBlock extends Block {
    private final String markerId;

    public DungeonMarkerBlock( String markerId, Properties properties ) {
        super(properties);
        if (markerId == null || markerId.isBlank()) {
            throw new IllegalArgumentException( "Dungeon marker ID cannot be null or blank." );
        }
        this.markerId =
                markerId
                        .trim()
                        .toLowerCase();
    }
    public String getMarkerId() {
        return markerId;
    }
    // STRUCTURE-PLACEMENT MARKER CAPTURE
    /**
     * While DungeonGenerationQueue is actively placing a jigsaw piece, marker blocks register their exact world position here. This lets encounter
     * preparation operate on the small set of marker positions instead of rescanning every block in every piece bounding box.
     */
    @Override
    protected void onPlace(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace( state, level, pos, oldState, movedByPiston );
        if (level.isClientSide()) {
            return;
        }
        DungeonMarkerCapture.record( level, pos );
    }
}
