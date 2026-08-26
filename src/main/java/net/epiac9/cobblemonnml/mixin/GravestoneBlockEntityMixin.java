package net.epiac9.cobblemonnml.mixin;

import it.hurts.sskirillss.yagm.block.entity.GraveStoneBlockEntity;

import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeteryGraveManager;
import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeterySavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin( value = GraveStoneBlockEntity.class, remap = false )
public abstract class GravestoneBlockEntityMixin {
    // RESTORE CEMETERY PLOT AFTER GRAVE CLAIM
    @Inject( method = "giveInventoryToPlayer", at = @At("RETURN") )
    private void cobblemonnml$restorePlotAfterClaim( ServerPlayer player, CallbackInfo ci ) {
        GraveStoneBlockEntity grave = (GraveStoneBlockEntity) (Object) this;

        if (!(grave.getLevel() instanceof ServerLevel graveLevel)) {
            return;
        }

        UUID ownerId =
                grave.getGraveData()
                        .getOwnerUUID();

        if (ownerId == null) {
            return;
        }

        BlockPos gravePos =
                grave.getBlockPos()
                        .immutable();

        MinecraftServer server = graveLevel.getServer();

        CemeterySavedData cemeteryData = CemeterySavedData.get( server );

        BlockPos assignedPlot = cemeteryData.getPlayerPlot( ownerId );

        /*
         * Ignore ordinary YAGM graves.
         *
         * Only graves physically occupying the owner's
         * CobblemonNML cemetery plot trigger restoration.
         */
        if (assignedPlot == null || !assignedPlot.equals( gravePos )) {
            return;
        }

        /*
         * GraveStoneBlock destroys the YAGM block immediately
         * after giveInventoryToPlayer returns.
         *
         * Therefore restoring grave_plot here synchronously
         * would make YAGM destroy our new plot on its next line.
         *
         * Queue restoration until after the current interaction
         * has completed.
         */
        server.execute(
                () -> {
                    BlockPos currentPlot =
                            CemeterySavedData.get( server )
                                    .getPlayerPlot( ownerId );

                    if (currentPlot == null || !currentPlot.equals( gravePos )) {
                        return;
                    }

                    CemeteryGraveManager.restorePlayerPlot( graveLevel, ownerId );
                }
        );
    }
}
