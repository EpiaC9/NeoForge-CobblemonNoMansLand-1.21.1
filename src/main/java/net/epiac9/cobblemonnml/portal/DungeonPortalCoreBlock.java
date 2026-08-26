package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.theme.DungeonThemeResolver;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTierResolver;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.registry.ModItemTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

import java.util.UUID;

public class DungeonPortalCoreBlock extends Block implements SimpleWaterloggedBlock {
    // WATERLOGGING
    public static final BooleanProperty WATERLOGGED =
            BlockStateProperties.WATERLOGGED;
    // SHAPE
    private static final VoxelShape SHAPE =
            Block.box( 0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D );
    // CELL INITIALIZATION GUARD
    private static boolean assigningCells = false;

    public DungeonPortalCoreBlock() {
        super(
                Properties
                        .of()
                        .strength( -1.0F )
                        .noOcclusion()
                        .noCollission()
                        .lightLevel(
                                state -> {
                                    if (state.getValue(
                                            DungeonPortalVisualState.ACTIVATED
                                    )) {
                                        return 15;
                                    }

                                    if (state.getValue(
                                            DungeonPortalVisualState.THEME
                                    ) > 0) {
                                        return 12;
                                    }

                                    if (state.getValue(
                                            DungeonPortalVisualState.TIER
                                    ) > 0) {
                                        return 8;
                                    }

                                    return 4;
                                }
                        )
        );

        registerDefaultState(
                defaultBlockState()
                        .setValue(
                                DungeonPortalVisualState.ACTIVATED,
                                false
                        )
                        .setValue(
                                DungeonPortalVisualState.TIER,
                                0
                        )
                        .setValue(
                                DungeonPortalVisualState.THEME,
                                0
                        )
                        .setValue(
                                DungeonPortalVisualState.CELL,
                                0
                        )
                        .setValue(
                                WATERLOGGED,
                                false
                        )
        );
    }
    // BLOCKSTATE
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
    // PLACEMENT
    @Override
    public @Nullable BlockState getStateForPlacement(
            @NotNull BlockPlaceContext context
    ) {
        FluidState fluidState =
                context.getLevel()
                        .getFluidState( context.getClickedPos() );

        return defaultBlockState()
                .setValue(
                        WATERLOGGED,
                        fluidState.getType() == Fluids.WATER
                );
    }
    // WATERLOGGED FLUID STATE
    @Override
    protected @NotNull FluidState getFluidState(
            @NotNull BlockState state
    ) {
        if (state.getValue( WATERLOGGED )) {
            return Fluids.WATER.getSource( false );
        }

        return super.getFluidState( state );
    }
    // WATER TICK
    @Override
    protected @NotNull BlockState updateShape(
            @NotNull BlockState state,
            @NotNull Direction direction,
            @NotNull BlockState neighborState,
            @NotNull LevelAccessor level,
            @NotNull BlockPos pos,
            @NotNull BlockPos neighborPos
    ) {
        if (state.getValue( WATERLOGGED )) {
            level.scheduleTick(
                    pos,
                    Fluids.WATER,
                    Fluids.WATER.getTickDelay( level )
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
    // BLOCK PLACED
    @Override
    protected void onPlace(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull BlockState oldState,
            boolean movedByPiston
    ) {
        super.onPlace(
                state,
                level,
                pos,
                oldState,
                movedByPiston
        );

        if (level.isClientSide()) {
            return;
        }

        if (assigningCells) {
            return;
        }

        initializePortalCells(
                level,
                pos
        );
    }
    // INITIALIZE 3x3 CELLS
    private static void initializePortalCells(
            Level level,
            BlockPos placedPos
    ) {
        BlockPos center =
                findCompleteCoreCenter(
                        level,
                        placedPos
                );

        if (center == null) {
            return;
        }

        assigningCells = true;

        try {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {

                    BlockPos portalPos =
                            center.offset(
                                    x,
                                    0,
                                    z
                            );

                    BlockState currentState =
                            level.getBlockState(
                                    portalPos
                            );

                    if (!currentState.is(
                            ModBlocks.DUNGEON_PORTAL_CORE.get()
                    )) {
                        continue;
                    }

                    int expectedCell =
                            DungeonPortalVisualState
                                    .cellIndex(
                                            x,
                                            z
                                    );

                    if (currentState.getValue(
                            DungeonPortalVisualState.CELL
                    ) == expectedCell) {
                        continue;
                    }

                    level.setBlock(
                            portalPos,
                            currentState.setValue(
                                    DungeonPortalVisualState.CELL,
                                    expectedCell
                            ),
                            3
                    );
                }
            }
        } finally {
            assigningCells = false;
        }
    }
    // FIND COMPLETE CORE
    private static BlockPos findCompleteCoreCenter(
            Level level,
            BlockPos placedPos
    ) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {

                BlockPos candidate =
                        placedPos.offset(
                                offsetX,
                                0,
                                offsetZ
                        );

                if (isCompleteCoreSquare(
                        level,
                        candidate
                )) {
                    return candidate
                            .immutable();
                }
            }
        }

        return null;
    }
    // COMPLETE CORE?
    private static boolean isCompleteCoreSquare(
            Level level,
            BlockPos center
    ) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {

                if (!level.getBlockState(
                                center.offset(
                                        x,
                                        0,
                                        z
                                )
                        )
                        .is(
                                ModBlocks.DUNGEON_PORTAL_CORE.get()
                        )) {
                    return false;
                }
            }
        }

        return true;
    }
    // CLIENT PARTICLES
    @Override
    public void animateTick(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull RandomSource random
    ) {
        DungeonPortalParticles.animate(
                state,
                level,
                pos,
                random
        );
    }
    // ENTITY INSIDE
    @Override
    protected void entityInside(
            @NotNull BlockState state,
            @NotNull Level level,
            @NotNull BlockPos pos,
            @NotNull Entity entity
    ) {
        super.entityInside(
                state,
                level,
                pos,
                entity
        );
        // SERVER ONLY
        if (!(level instanceof ServerLevel overworld)) {
            return;
        }
        // ITEMS ONLY
        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack stack =
                itemEntity.getItem();

        if (stack.isEmpty()) {
            return;
        }
        // FIND CORE CENTER
        BlockPos portalCenter =
                DungeonPortalManager
                        .findCoreCenter(
                                overworld,
                                pos
                        );

        if (portalCenter == null) {
            return;
        }
        // DUNGEON ALREADY ACTIVE
        /*
         * This normally won't happen because the active overworld portal
         * changes to DungeonPortalBlock. However, another inactive core
         * could exist elsewhere while one global dungeon session is active.
         * Reject everything from it.
         */
        if (DungeonSession.isActive()) {
            DungeonPortalItemEjector.eject(
                    itemEntity,
                    portalCenter
            );

            return;
        }
        // THEME ITEM
        DungeonTheme theme =
                DungeonThemeResolver
                        .getTheme(
                                stack
                        );

        if (theme != null) {
            boolean accepted =
                    DungeonPortalSelectionManager
                            .selectTheme(
                                    overworld,
                                    portalCenter,
                                    theme,
                                    stack
                            );

            if (accepted) {
                consumeOne(
                        itemEntity
                );
            }

            DungeonPortalItemEjector
                    .eject(
                            itemEntity,
                            portalCenter
                    );

            return;
        }
        // TIER ITEM
        DungeonTier tier =
                DungeonTierResolver
                        .getTier(
                                stack
                        );

        if (tier != null) {
            boolean accepted =
                    DungeonPortalSelectionManager
                            .selectTier(
                                    overworld,
                                    portalCenter,
                                    tier,
                                    stack
                            );

            if (accepted) {
                consumeOne(
                        itemEntity
                );
            }

            DungeonPortalItemEjector
                    .eject(
                            itemEntity,
                            portalCenter
                    );

            return;
        }
        // SPECIAL ROOM FORCE ITEM
        if (stack.is( ModItemTags.PORTAL_SPECIAL_ROOM )) {
            boolean accepted =
                    DungeonPortalSelectionManager
                            .selectSpecialRoomForce(
                                    overworld,
                                    portalCenter,
                                    stack
                            );
            if (accepted) {
                consumeOne( itemEntity );
            }
            DungeonPortalItemEjector.eject( itemEntity, portalCenter );
            return;
        }
        // ACTIVATION ITEM
        if (stack.is(
                ModItemTags.PORTAL_ACTIVATION
        )) {
            UUID ownerUUID =
                    null;

            Entity owner =
                    itemEntity.getOwner();

            if (owner instanceof ServerPlayer player) {
                ownerUUID =
                        player.getUUID();
            }

            boolean accepted =
                    DungeonPortalSelectionManager
                            .arm(
                                    overworld,
                                    portalCenter,
                                    ownerUUID,
                                    stack
                            );

            if (accepted) {
                consumeOne(
                        itemEntity
                );
            }

            DungeonPortalItemEjector.eject(
                    itemEntity,
                    portalCenter
            );

            return;
        }
        // INVALID ITEM
        /*
         * It isn't:
         * - a Theme selector
         * - a Tier selector
         * - a Special Room Key
         * - a Dungeon Activator
         *
         * Do not consume anything.
         * Reject the entire stack.
         */
        DungeonPortalItemEjector.eject(
                itemEntity,
                portalCenter
        );
    }
    // CONSUME ONE
    private static void consumeOne(
            ItemEntity itemEntity
    ) {
        ItemStack stack =
                itemEntity.getItem();

        stack.shrink( 1 );

        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }
    // SHAPE
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
