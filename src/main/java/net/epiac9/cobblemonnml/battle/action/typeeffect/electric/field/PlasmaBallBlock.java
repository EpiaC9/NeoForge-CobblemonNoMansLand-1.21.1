package net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PlasmaBallBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(4.0D, 4.0D, 4.0D, 12.0D, 12.0D, 12.0D);

    public PlasmaBallBlock(Properties properties) { super(properties); }
    @Override public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }
    @Override protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                     @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }
    @Override public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PlasmaBallBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickerLevel, pos, tickerState, entity) -> {
            if (entity instanceof PlasmaBallBlockEntity ball) ball.serverTick();
        };
    }
    @Override protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                      @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PlasmaBallBlockEntity ball) {
            ActionBattleElectricController.unregister(ball);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
