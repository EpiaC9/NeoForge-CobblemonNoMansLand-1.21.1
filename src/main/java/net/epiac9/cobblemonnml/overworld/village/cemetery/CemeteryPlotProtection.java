package net.epiac9.cobblemonnml.overworld.village.cemetery;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class CemeteryPlotProtection {
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        BlockPos placedPos = event.getPos();
        LevelAccessor level = event.getLevel();

        /*
         * Cancel if the player is trying to:
         * 1. place directly into the grave plot block space
         * 2. place on top of a grave plot
         */
        if (isGravePlot(level, placedPos) || isGravePlot(level, placedPos.below())) {
            event.setCanceled(true);
        }
    }
    private static boolean isGravePlot(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).is(ModBlocks.GRAVE_PLOT.get());
    }
}
