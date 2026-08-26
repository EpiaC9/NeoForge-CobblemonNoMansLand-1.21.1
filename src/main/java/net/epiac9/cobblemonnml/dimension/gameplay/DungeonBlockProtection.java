package net.epiac9.cobblemonnml.dimension.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.Set;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonBlockProtection {

    private static final ResourceKey<Level> DUNGEON_DIMENSION =
            ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon_dimension" )
            );

    /*
     * Positions of blocks placed by players during the current dungeon session.
     * BlockPos#asLong is used so we don't need to keep mutable BlockPos objects in the set.
     */
    private static final Set<Long> PLAYER_PLACED_BLOCKS = new HashSet<>();

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isInDungeon(player)) {
            return;
        }
        BlockPos pos = event.getPos();
        PLAYER_PLACED_BLOCKS.add( pos.asLong() );
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!isInDungeon(player)) {
            return;
        }

        /*
         * Creative and Spectator are unrestricted.
         * Protection only applies to Survival and Adventure.
         */
        if (!shouldProtect(player)) {
            return;
        }
        long packedPos = event.getPos().asLong();

        /*
         * Player placed this block during the current dungeon session,
         * so allow it to be broken.
         */
        if (PLAYER_PLACED_BLOCKS.remove(packedPos)) {
            return;
        }

        /*
         * Anything else is part of the dungeon and cannot be broken.
         */
        event.setCanceled(true);
    }
    public static void clearPlacedBlocks() {
        PLAYER_PLACED_BLOCKS.clear();
    }
    private static boolean isInDungeon(Player player) {
        return player.level()
                .dimension()
                .equals(DUNGEON_DIMENSION);
    }
    private static boolean shouldProtect(Player player) {
        return !player.isCreative() && !player.isSpectator();
    }
}
