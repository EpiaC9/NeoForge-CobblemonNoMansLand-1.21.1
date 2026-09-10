package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ActionBattleFairyIllusionBlock extends Block implements EntityBlock {
    public ActionBattleFairyIllusionBlock(Properties properties) { super(properties); }
    @Override public @NotNull RenderShape getRenderShape(@NotNull BlockState state) { return RenderShape.INVISIBLE; }
    @Override public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ActionBattleFairyIllusionBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            @NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.ACTION_BATTLE_FAIRY_ILLUSION.get() ? null
                : (tickerLevel, pos, tickerState, entity) -> {
                    if (entity instanceof ActionBattleFairyIllusionBlockEntity illusion) illusion.serverTick();
                };
    }
}
