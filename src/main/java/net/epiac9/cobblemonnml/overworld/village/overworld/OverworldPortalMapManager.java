package net.epiac9.cobblemonnml.overworld.village.overworld;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.Objects;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class OverworldPortalMapManager {
    private static final String RECEIVED_MAP_KEY = "CobblemonNMLReceivedPortalMap";
    /*
     * Scale:
     * 0 = 128 x 128
     * 1 = 256 x 256
     * 2 = 512 x 512
     * 3 = 1024 x 1024
     * 4 = 2048 x 2048
     * Scale 3 is a good explorer-map style size.
     */
    private static final byte MAP_SCALE = 3;
    // PLAYER LOGIN
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        givePortalMapIfNeeded( player );
    }
    // GIVE PORTAL MAP
    public static void givePortalMapIfNeeded(ServerPlayer player) {
        if (player == null) {
            return;
        }
        // ALREADY RECEIVED
        if (player.getPersistentData().getBoolean( RECEIVED_MAP_KEY )) {
            DebugLog.log(
                    "[CobblemonNML] "
                            + player.getGameProfile().getName()
                            + " has already received the Overworld portal map."
            );
            return;
        }
        ServerLevel overworld =
                Objects.requireNonNull(player .getServer())
                        .overworld();
        OverworldPortalSavedData portalData = OverworldPortalSavedData.get( overworld );
        // PORTAL MUST EXIST FIRST
        if (!portalData.isGenerated()) {
            DebugLog.log(
                    "[CobblemonNML] Portal map not given to "
                            + player.getGameProfile().getName()
                            + " because the Overworld portal has not generated yet."
            );
            return;
        }
        BlockPos portalPos = portalData.getPortalPos();
        if (portalPos == null) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Overworld portal is marked generated "
                            + "but has no saved position."
            );
            return;
        }
        // CREATE FILLED MAP
        ItemStack map = MapItem.create( overworld, portalPos.getX(), portalPos.getZ(), MAP_SCALE, true, true );
        map.set( DataComponents.CUSTOM_NAME, Component.literal( "Dungeon Portal Map" ) );
        // ADD PORTAL COORDINATES
        map.set(
                DataComponents.LORE,
                new ItemLore(
                        List.of(
                                Component.literal(
                                        "Portal Coordinates: "
                                                + portalPos.getX()
                                                + ", "
                                                + portalPos.getY()
                                                + ", "
                                                + portalPos.getZ()
                                )
                        )
                )
        );
        // ADD PORTAL MARKER
        MapItemSavedData.addTargetDecoration(
                map,
                portalPos,
                "cobblemonnml_dungeon_portal",
                MapDecorationTypes.TARGET_X
        );
        // GIVE TO PLAYER
        boolean added =
                player
                        .getInventory()
                        .add( map );
        if (!added) {
            player.drop( map, false );
        }
        // MARK PLAYER AS HAVING RECEIVED IT
        player.getPersistentData()
                .putBoolean( RECEIVED_MAP_KEY, true );
        DebugLog.log(
                "[CobblemonNML] Gave Overworld portal map to "
                        + player.getGameProfile().getName()
                        + " pointing to "
                        + portalPos
        );

    }
}
