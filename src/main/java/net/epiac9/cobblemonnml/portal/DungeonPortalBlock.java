package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DungeonPortalBlock extends Block implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED =
            BlockStateProperties.WATERLOGGED;

    private static final VoxelShape SHAPE =
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D);

    public DungeonPortalBlock() {
        super(
                Properties
                        .of()
                        .noCollission()
                        .strength(-1.0F)
                        .lightLevel(state -> 15)
                        .noOcclusion()
        );

        registerDefaultState(
                defaultBlockState()
                        .setValue(DungeonPortalVisualState.ACTIVATED, true)
                        .setValue(DungeonPortalVisualState.TIER, 0)
                        .setValue(DungeonPortalVisualState.THEME, 0)
                        .setValue(DungeonPortalVisualState.CELL, 0)
                        .setValue(WATERLOGGED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(
                DungeonPortalVisualState.ACTIVATED,
                DungeonPortalVisualState.TIER,
                DungeonPortalVisualState.THEME,
                DungeonPortalVisualState.CELL,
                WATERLOGGED
        );
    }

    @Override
    public @Nullable BlockState getStateForPlacement(
            @NotNull BlockPlaceContext context
    ) {
        FluidState fluidState =
                context.getLevel()
                        .getFluidState(context.getClickedPos());

        return defaultBlockState()
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected @NotNull FluidState getFluidState(
            @NotNull BlockState state
    ) {
        if (state.getValue(WATERLOGGED)) {
            return Fluids.WATER.getSource(false);
        }

        return super.getFluidState(state);
    }

    @Override
    protected @NotNull BlockState updateShape(
            @NotNull BlockState state,
            @NotNull Direction direction,
            @NotNull BlockState neighborState,
            @NotNull LevelAccessor level,
            @NotNull BlockPos pos,
            @NotNull BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(
                    pos,
                    Fluids.WATER,
                    Fluids.WATER.getTickDelay(level)
            );
        }

        return super.updateShape(
                state,
                direction,
                neighborState,
                level,
                pos,
                neighborPos
        );
    }

    @Override
    protected void onPlace(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (state.getValue(DungeonPortalVisualState.CELL) != 4) {
            return;
        }

        BlockPos portalCenter = pos.immutable();

        DungeonPortalItemEjector.ejectAll(serverLevel, portalCenter);
    }

    @Override
    public void animateTick(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull RandomSource random
    ) {
        DungeonPortalParticles.animate(state, level, pos, random);
    }

    @Override
    protected void entityInside(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Entity entity
    ) {
        super.entityInside(state, level, pos, entity);

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (entity instanceof ItemEntity itemEntity) {
            BlockPos portalCenter =
                    DungeonPortalManager
                            .findActivePortalCenter(serverLevel, pos);

            DungeonPortalItemEjector.eject(
                    itemEntity,
                    Objects.requireNonNullElse(portalCenter, pos)
            );

            return;
        }

        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        if (player.level()
                .dimension()
                .equals(DungeonDimension.DUNGEON_DIMENSION)) {

            ServerLevel overworld =
                    Objects.requireNonNull(player.getServer())
                            .getLevel(Level.OVERWORLD);

            if (overworld == null) {
                return;
            }

            BlockPos returnPos;

            if (player.hasData(ModAttachments.RETURN_POSITION)) {
                returnPos =
                        player.getData(ModAttachments.RETURN_POSITION);
            } else {
                returnPos = overworld.getSharedSpawnPos();
            }

            player.teleportTo(
                    overworld,
                    returnPos.getX() + 0.5D,
                    returnPos.getY() + 1.0D,
                    returnPos.getZ() + 0.5D,
                    player.getYRot(),
                    player.getXRot()
            );

            player.removeAllEffects();
            DungeonMapRestrictionManager.clear(player);

            return;
        }

        if (!player.level()
                .dimension()
                .equals(Level.OVERWORLD)) {
            return;
        }

        if (!state.getValue(DungeonPortalVisualState.ACTIVATED)) {
            return;
        }

        ServerLevel dungeonLevel =
                Objects.requireNonNull(player.getServer())
                        .getLevel(DungeonDimension.DUNGEON_DIMENSION);

        if (dungeonLevel == null) {
            return;
        }

        BlockPos portalCenter =
                DungeonPortalManager
                        .findActivePortalCenter(serverLevel, pos);

        if (portalCenter == null) {
            return;
        }

        BlockPos returnPos =
                findSafeReturnPosition(serverLevel, portalCenter);

        player.setData(
                ModAttachments.RETURN_POSITION,
                returnPos
        );

        player.setData(
                ModAttachments.PORTAL_CENTER,
                portalCenter
        );

        BlockPos dungeonOrigin =
                DungeonDimension
                        .getCurrentDungeonOrigin();

        player.teleportTo(
                dungeonLevel,
                dungeonOrigin.getX() + 0.5D,
                dungeonOrigin.getY() + 1.0D,
                dungeonOrigin.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );

        player.removeAllEffects();
        DungeonMapRestrictionManager.enforce(player);
    }

    private static BlockPos findSafeReturnPosition(
            ServerLevel level,
            BlockPos portalCenter
    ) {
        BlockPos[] candidates = {
                portalCenter.offset(0, 0, 3),
                portalCenter.offset(0, 0, -3),
                portalCenter.offset(3, 0, 0),
                portalCenter.offset(-3, 0, 0)
        };

        for (BlockPos candidate : candidates) {
            BlockPos feet = candidate.above();
            BlockPos head = feet.above();

            if (!level.getBlockState(candidate)
                    .getCollisionShape(level, candidate)
                    .isEmpty()
                    && level.getBlockState(feet)
                    .getCollisionShape(level, feet)
                    .isEmpty()
                    && level.getBlockState(head)
                    .getCollisionShape(level, head)
                    .isEmpty()) {

                return candidate.immutable();
            }
        }

        return portalCenter
                .offset(0, 0, 3)
                .immutable();
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
}
