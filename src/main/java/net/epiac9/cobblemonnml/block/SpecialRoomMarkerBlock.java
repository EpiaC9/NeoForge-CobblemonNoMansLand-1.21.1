package net.epiac9.cobblemonnml.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class SpecialRoomMarkerBlock extends DungeonMarkerBlock {
    // FACING
    /*
     * IMPORTANT:
     * FACING is the direction that the special room should
     * extend FROM this marker.
     * Example: existing room
     * +-----------+
     * |           |
     * |         [S] -----> special room
     * |           |
     * +-----------+
     * If S faces EAST, the special room generation will start east of the marker.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // CONSTRUCTOR
    public SpecialRoomMarkerBlock( Properties properties ) {
        super( "special_room", properties );
        registerDefaultState( stateDefinition .any() .setValue( FACING, Direction.NORTH ) );
    }
    // BLOCK STATE PROPERTIES
    @Override
    protected void createBlockStateDefinition( StateDefinition.Builder<Block, BlockState> builder ) {
        builder.add( FACING );
    }
    // PLACEMENT DIRECTION
    @Override
    public BlockState getStateForPlacement( BlockPlaceContext context ) {

        /*
         * Unlike a furnace/chest, the marker faces in the same direction the player is looking.
         * This makes structure editing intuitive:
         * stand inside the room
         * look toward where the special room should extend
         * place the marker
         * The stored FACING value then points toward the special room connection.
         */
        return defaultBlockState()
                .setValue( FACING, context.getHorizontalDirection() );
    }
    // STRUCTURE ROTATION
    /*
     * Jigsaw structures can rotate their template pieces.
     * The marker's facing direction therefore also needs to
     * rotate with the structure.
     */
    @Override
    public @NotNull BlockState rotate( BlockState state, Rotation rotation ) {
        return state.setValue( FACING, rotation.rotate( state.getValue( FACING ) ) );
    }
    // STRUCTURE MIRRORING
    @Override
    public @NotNull BlockState mirror( @NotNull BlockState state, Mirror mirror ) {
        return rotate( state, mirror.getRotation( state.getValue( FACING ) ) );
    }
    // COLLISION
    /*
     * This is an editor/generation marker rather than a real dungeon block.
     * It has no collision so it cannot obstruct the connector while working on structure templates.
     */
    @Override
    protected @NotNull VoxelShape getCollisionShape(
            @NotNull BlockState state,
            @NotNull BlockGetter level,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return Shapes.empty();
    }
}
