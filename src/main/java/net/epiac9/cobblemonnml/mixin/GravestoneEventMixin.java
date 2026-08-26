package net.epiac9.cobblemonnml.mixin;

import it.hurts.sskirillss.yagm.api.variant.IGraveVariant;
import it.hurts.sskirillss.yagm.api.variant.registry.GraveVariantRegistry;
import it.hurts.sskirillss.yagm.block.entity.GraveStoneBlockEntity;
import it.hurts.sskirillss.yagm.component.level.GraveStoneLevels;
import it.hurts.sskirillss.yagm.data.gravedata.GraveDataManager;
import it.hurts.sskirillss.yagm.data.gravedata.GraveSaveManager;
import it.hurts.sskirillss.yagm.event.GraveStoneEvent;
import it.hurts.sskirillss.yagm.util.InventoryUtils;
import it.hurts.sskirillss.yagm.util.NbtKeys;
import it.hurts.sskirillss.yagm.util.PlaceableUtils;
import it.hurts.sskirillss.yagm.util.VariantUtils;

import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeteryGraveManager;
import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeteryManager;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin( value = GraveStoneEvent.class, remap = false )
public abstract class GravestoneEventMixin {
    private static final NbtKeys COBBLEMONNML$KEYS = NbtKeys.INSTANCE;
    // CHECK CEMETERY BEFORE YAGM HANDLES DEATH
    @Inject( method = "handlePlayerDeath", at = @At("HEAD"), cancellable = true )
    private static void cobblemonnml$checkCemeteryBeforeDeath( ServerPlayer player, CallbackInfo ci ) {
        if (player == null) {
            ci.cancel();
            return;
        }

        ServerLevel overworld =
                player.getServer()
                        .overworld();

        /*
         * No registered cemetery means YAGM is not allowed
         * to intercept this death.
         *
         * Minecraft will therefore handle the player's
         * inventory normally.
         */
        if (!CemeteryManager.hasCemetery( overworld )) {
            ci.cancel();
            return;
        }

        /*
         * Existing assignment is returned immediately.
         *
         * If the player has no assignment yet, this retries
         * allocation from the remaining unassigned plots.
         */
        BlockPos plotPos = CemeteryManager.getOrAssignPlayerPlot( overworld, player.getUUID() );

        /*
         * Cemetery exists but no plot could be allocated.
         * Let Minecraft handle the death normally.
         */
        if (plotPos == null) {
            DebugLog.log(
                    "No cemetery grave plot available for "
                            + player.getGameProfile()
                            .getName()
                            + ". YAGM grave creation skipped."
            );

            ci.cancel();
        }
    }
    // IGNORE YAGM KEEP INVENTORY CHECK
    @Redirect(
            method = "handlePlayerDeath",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"
            )
    )
    private static boolean cobblemonnml$useCemeteryRule(
            GameRules gameRules,
            GameRules.Key<GameRules.BooleanValue> rule
    ) {
        /*
         * Cemetery allocation is now the rule that decides
         * whether YAGM handles the death.
         *
         * Returning false here makes YAGM behave as though
         * keepInventory is disabled after our cemetery check
         * has already approved the player.
         */
        if (rule == GameRules.RULE_KEEPINVENTORY) {
            return false;
        }

        return gameRules.getBoolean( rule );
    }
    // REDIRECT YAGM GRAVE TO CEMETERY
    @Inject( method = "onPlayerDeath", at = @At("HEAD"), cancellable = true )
    private static void cobblemonnml$placeCemeteryGrave( ServerPlayer player, CompoundTag graveData, CallbackInfo ci ) {
        if (player == null || graveData == null) {
            ci.cancel();
            return;
        }

        ServerLevel overworld =
                player.getServer()
                        .overworld();

        BlockPos plotPos = CemeteryManager.getOrAssignPlayerPlot( overworld, player.getUUID() );

        /*
         * This should normally already have succeeded in
         * handlePlayerDeath, but retry here in case cemetery
         * state changed between the two calls.
         */
        if (plotPos == null) {
            ci.cancel();
            return;
        }

        /*
         * Ensure the cemetery chunk is available before
         * touching the assigned plot.
         */
        overworld.getChunkAt( plotPos );
        // REMOVE PREVIOUS ACTIVE GRAVE
        BlockEntity existingBlockEntity = overworld.getBlockEntity( plotPos );

        if (existingBlockEntity instanceof GraveStoneBlockEntity existingGrave) {
            /*
             * The old grave contents must be discarded rather
             * than dropped when a new death replaces it.
             */
            existingGrave.setSuppressDropsOnRemove( true );

            UUID oldGraveId =
                    existingGrave
                            .getGraveData()
                            .getGraveId();

            if (oldGraveId != null) {
                GraveDataManager.get( overworld )
                        .removeGrave( oldGraveId );
            }

            overworld.destroyBlock( plotPos, false );
        }
        // PREPARE ASSIGNED PLOT
        BlockPos preparedPlot = CemeteryGraveManager.preparePlayerPlotForNewGrave( overworld, player.getUUID() );

        if (preparedPlot == null) {
            ci.cancel();
            return;
        }
        // CREATE NEW YAGM GRAVE ID
        if (!graveData.hasUUID( COBBLEMONNML$KEYS.getId() )) {
            graveData.putUUID( COBBLEMONNML$KEYS.getId(), UUID.randomUUID() );
        }

        UUID graveId = graveData.getUUID( COBBLEMONNML$KEYS.getId() );

        GraveDataManager graveDataManager = GraveDataManager.get( overworld );

        /*
         * Remove any stale record using the same ID before
         * registering the new physical grave.
         */
        if (graveDataManager.hasGrave( graveId )) {
            graveDataManager.removeGrave( graveId );
        }

        graveDataManager.addGrave( graveData );

        long deathTime = System.currentTimeMillis();

        GraveSaveManager.saveGraveData( overworld, player.getUUID(), deathTime, graveData );
        // RESOLVE YAGM GRAVE APPEARANCE
        GraveStoneLevels graveLevel = InventoryUtils.calculateGraveLevel( player );

        ResourceLocation variantId = null;

        if (graveData.contains( COBBLEMONNML$KEYS.getVariantId() )) {
            variantId = ResourceLocation.tryParse( graveData.getString( COBBLEMONNML$KEYS.getVariantId() ) );
        }

        if (variantId == null) {
            IGraveVariant variant = GraveVariantRegistry.getFor( overworld, preparedPlot );

            if (variant != null && variant.getId() != null) {
                variantId = variant.getId();

                graveData.putString( COBBLEMONNML$KEYS.getVariantId(), variantId.toString() );
            }
        }

        Block graveBlock = VariantUtils.getVariantId( variantId != null ? variantId.toString() : null, graveLevel );

        Direction facing =
                player.getDirection()
                        .getOpposite();

        BlockState graveState =
                graveBlock
                        .defaultBlockState()
                        .setValue( BlockStateProperties.HORIZONTAL_FACING, facing )
                        .setValue(
                                BlockStateProperties.WATERLOGGED,
                                overworld
                                        .getFluidState( preparedPlot )
                                        .isSourceOfType( Fluids.WATER )
                        );
        // PLACE EXACTLY ON ASSIGNED PLOT
        BlockPos placedPos = PlaceableUtils.placeGraveStoneExact( overworld, preparedPlot, graveState );

        if (placedPos == null) {
            graveDataManager.removeGrave( graveId );

            /*
             * Grave placement failed.
             *
             * Drop the saved grave contents rather than
             * silently deleting the player's inventory.
             */
            InventoryUtils.dropFullGrave( player.serverLevel(), player.blockPosition(), graveData );

            DebugLog.log(
                    "Failed to place cemetery grave for "
                            + player.getGameProfile()
                            .getName()
                            + ". Grave contents dropped at death position."
            );

            ci.cancel();
            return;
        }
        // INITIALIZE YAGM BLOCK ENTITY
        BlockEntity placedBlockEntity = overworld.getBlockEntity( placedPos );

        if (!(placedBlockEntity instanceof GraveStoneBlockEntity graveBlockEntity)) {
            graveDataManager.removeGrave( graveId );

            InventoryUtils.dropFullGrave( player.serverLevel(), player.blockPosition(), graveData );

            overworld.destroyBlock( placedPos, false );

            ci.cancel();
            return;
        }

        graveBlockEntity.loadGraveData( graveData, overworld.registryAccess() );

        /*
         * Prevent YAGM from deciding that the cemetery grave
         * should fall because of its terrain checks.
         */
        graveBlockEntity.setVoidRecovery( true );

        graveBlockEntity.initializeGrave(
                player.getUUID(),
                player.getGameProfile()
                        .getName(),
                deathTime,
                null,
                null,
                graveLevel
        );

        if (variantId != null) {
            graveBlockEntity.setVariant( GraveVariantRegistry.get( variantId ) );
        }

        graveDataManager.setGravePos( graveId, placedPos );

        CemeteryGraveManager.markGraveCreated( overworld, player.getUUID() );

        DebugLog.log( "Created cemetery grave for " + player.getGameProfile() .getName() + " at " + placedPos );

        /*
         * Stop YAGM's original onPlayerDeath placement code.
         *
         * We only cancel this inner placement method.
         * The outer handlePlayerDeath method is still allowed
         * to continue so YAGM clears the inventory/accessories
         * that were successfully stored in this grave.
         */
        ci.cancel();
    }
}
