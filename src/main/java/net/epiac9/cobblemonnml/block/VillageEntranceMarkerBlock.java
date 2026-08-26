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

public final class VillageEntranceMarkerBlock extends DungeonMarkerBlock {
    // FACING
    /*
     * FACING is the direction that the generated village road exits
     * the structure from this exact marker position.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    // CONSTRUCTOR
    public VillageEntranceMarkerBlock(Properties properties) {
        super("village_entrance", properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    // BLOCK STATE PROPERTIES
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    // PLACEMENT DIRECTION
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    // STRUCTURE ROTATION
    @Override
    public @NotNull BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    // STRUCTURE MIRRORING
    @Override
    public @NotNull BlockState mirror(@NotNull BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }
    // COLLISION
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
