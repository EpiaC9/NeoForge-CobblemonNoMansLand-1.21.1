package net.epiac9.cobblemonnml.overworld.village.cemetery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public final class GravePlotBlock extends Block {
    /*
     * One pixel tall.
     */
    private static final VoxelShape SHAPE = Block.box( 0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D );
    public GravePlotBlock(Properties properties) {
        super(properties);
    }
    @Override
    protected @NotNull VoxelShape getShape(
            @NotNull BlockState state,
            @NotNull BlockGetter level,
            @NotNull BlockPos pos,
            @NotNull CollisionContext context
    ) {
        return SHAPE;
    }
    @Override
    public boolean canBeReplaced( @NotNull BlockState state, @NotNull BlockPlaceContext context ) {
        /*
         * Do NOT allow replacing the grave plot directly.
         * We will block "placing on top" separately with a placement event.
         */
        return false;
    }
}
