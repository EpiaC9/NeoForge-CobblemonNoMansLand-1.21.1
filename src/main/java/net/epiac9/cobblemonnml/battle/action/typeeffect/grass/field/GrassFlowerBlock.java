package net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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

public final class GrassFlowerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);
    public GrassFlowerBlock(Properties properties) { super(properties); }
    @Override public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.MODEL; }
    @Override protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                     @NotNull BlockPos pos, @NotNull CollisionContext context) { return SHAPE; }
    @Override public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) { return new GrassFlowerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
            @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickerLevel, pos, tickerState, blockEntity) -> {
            if (blockEntity instanceof GrassFlowerBlockEntity flower) flower.serverTick();
        };
    }
    @Override protected void entityInside(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide && entity instanceof PokemonEntity pokemon
                && level.getBlockEntity(pos) instanceof GrassFlowerBlockEntity flower) flower.onPokemonTouch(pokemon);
    }
    @Override protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GrassFlowerBlockEntity flower) {
            net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController.unregisterFlower(flower);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
