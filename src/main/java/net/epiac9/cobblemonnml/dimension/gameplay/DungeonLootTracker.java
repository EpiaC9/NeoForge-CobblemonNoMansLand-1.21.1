package net.epiac9.cobblemonnml.dimension.gameplay;

import net.epiac9.cobblemonnml.dimension.DungeonDimensionEvents;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonLootTracker {
    private static final ResourceKey<Level> DUNGEON_DIMENSION =
            ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon_dimension" )
            );

    /*
     * One tracking state per player currently visiting the dungeon.
     */
    private static final Map<UUID, PlayerLootState> PLAYER_STATES = new HashMap<>();
    // PLAYER TICK
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();

        /*
         * Player is outside the dungeon.
         * If they successfully left with dungeon loot,
         * that loot is now theirs and should no longer
         * be considered temporary dungeon loot.
         */
        if (!isInDungeon( player )) {
            PLAYER_STATES.remove( playerId );
            return;
        }

        /*
         * First tick inside the dungeon.
         * Everything currently in the player's inventory
         * is treated as something they brought with them.
         */
        PlayerLootState state =
                PLAYER_STATES.computeIfAbsent( playerId, id -> new PlayerLootState( countInventory( player ) ) );

        updateState( player, state );
    }
    // PLAYER DEATH
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!isInDungeon( player )) {
            return;
        }

        UUID playerId = player.getUUID();

        DebugLog.log( "[CobblemonNML] Dungeon death detected for " + player.getGameProfile().getName() );

        PlayerLootState state = PLAYER_STATES.get( playerId );
        // NO TRACKING STATE
        if (state == null) {
            /*
             * Nothing was being tracked.
             * Do not delete anything because we cannot safely
             * determine what the player brought into the dungeon.
             *
             * The dungeon should still be reset because the player
             * died during an active dungeon run.
             */
            DebugLog.log(
                    "[CobblemonNML] WARNING: No dungeon loot tracking state found for "
                            + player.getGameProfile().getName()
                            + ". No inventory items will be removed."
            );

            DungeonDimensionEvents.requestReset();

            DebugLog.log(
                    "[CobblemonNML] Dungeon reset requested because "
                            + player.getGameProfile().getName()
                            + " died inside the dungeon."
            );
            return;
        }
        // CAPTURE FINAL INVENTORY STATE
        updateState( player, state );
        // EXTRACT DUNGEON LOOT
        List<ItemStack> lostLoot = extractDungeonLoot( player, state );

        int lostItemCount = 0;

        for (ItemStack stack : lostLoot) {
            lostItemCount += stack.getCount();
        }

        DebugLog.log(
                "[CobblemonNML] Extracted "
                        + lostLoot.size()
                        + " dungeon loot stack(s), "
                        + lostItemCount
                        + " total item(s), from "
                        + player.getGameProfile().getName()
        );
        // SAVE LOST LOOT
        if (!lostLoot.isEmpty()) {
            String themeId =
                    DungeonSession.getTheme() == null
                            ? ""
                            : DungeonSession
                            .getTheme()
                            .getId();

            String tierId =
                    DungeonSession.getTier() == null
                            ? ""
                            : DungeonSession
                            .getTier()
                            .name();

            DungeonLostLootData lostLootData = DungeonLostLootData.get( player );

            lostLootData.addLostLoot( player.getUUID(), player.getGameProfile().getName(), lostLoot, themeId, tierId );

            DebugLog.log(
                    "[CobblemonNML] Saved pending cemetery loot for "
                            + player.getGameProfile().getName()
                            + ". Theme="
                            + themeId
                            + ", Tier="
                            + tierId
            );
        } else {
            DebugLog.log(
                    "[CobblemonNML] "
                            + player.getGameProfile().getName()
                            + " died in the dungeon with no tracked dungeon-acquired loot."
            );
        }
        // CLEAR PLAYER TRACKING
        PLAYER_STATES.remove( playerId );
        // REQUEST DUNGEON RESET
        DungeonDimensionEvents.requestReset();

        DebugLog.log(
                "[CobblemonNML] Dungeon reset requested because "
                        + player.getGameProfile().getName()
                        + " died inside the dungeon."
        );
    }
    // UPDATE TRACKING STATE
    private static void updateState( ServerPlayer player, PlayerLootState state ) {
        Map<Item, Integer> currentCounts = countInventory( player );

        /*
         * First process everything currently present.
         */
        for (Map.Entry<Item, Integer> entry : currentCounts.entrySet()) {
            Item item = entry.getKey();
            int current = entry.getValue();
            int previous = state.lastCounts.getOrDefault( item, 0 );
            int difference = current - previous;

            if (difference > 0) {
                /*
                 * Inventory increased.
                 * Treat the increase as dungeon-acquired.
                 */
                state.dungeonCounts.merge( item, difference, Integer::sum );

            } else if (difference < 0) {
                /*
                 * Inventory decreased.
                 * Consume dungeon-acquired quantity first.
                 * This preserves items the player brought in
                 * whenever provenance is ambiguous.
                 */
                consumeDungeonQuantity( state, item, -difference );
            }
        }

        /*
         * Now process items that existed previously but are
         * completely absent from the current inventory.
         */
        for (Map.Entry<Item, Integer> entry : state.lastCounts.entrySet()) {
            Item item = entry.getKey();

            if (currentCounts.containsKey( item )) {
                continue;
            }

            int previous = entry.getValue();

            if (previous > 0) {
                consumeDungeonQuantity( state, item, previous );
            }
        }

        state.lastCounts.clear();
        state.lastCounts.putAll( currentCounts );
    }
    // CONSUME DUNGEON QUANTITY
    private static void consumeDungeonQuantity( PlayerLootState state, Item item, int amount ) {
        int dungeonAmount = state.dungeonCounts.getOrDefault( item, 0 );

        if (dungeonAmount <= 0) {
            return;
        }

        int remaining = dungeonAmount - amount;

        if (remaining <= 0) {
            state.dungeonCounts.remove( item );
        } else {
            state.dungeonCounts.put( item, remaining );
        }
    }
    // EXTRACT DUNGEON LOOT
    private static List<ItemStack> extractDungeonLoot( ServerPlayer player, PlayerLootState state ) {
        List<ItemStack> extracted = new ArrayList<>();

        for (Map.Entry<Item, Integer> entry : state.dungeonCounts.entrySet()) {
            Item item = entry.getKey();
            int remainingToRemove = entry.getValue();

            if (remainingToRemove <= 0) {
                continue;
            }

            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem( slot );

                if (stack.isEmpty()) {
                    continue;
                }

                if (stack.getItem() != item) {
                    continue;
                }

                int removeAmount = Math.min( remainingToRemove, stack.getCount() );

                /*
                 * Preserve the exact stack components.
                 */
                ItemStack removedStack = stack.copyWithCount( removeAmount );

                extracted.add( removedStack );

                stack.shrink( removeAmount );

                remainingToRemove -= removeAmount;

                if (remainingToRemove <= 0) {
                    break;
                }
            }
        }

        player.getInventory()
                .setChanged();

        return extracted;
    }
    // COUNT INVENTORY
    private static Map<Item, Integer> countInventory( ServerPlayer player ) {
        Map<Item, Integer> counts = new IdentityHashMap<>();

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem( slot );

            if (stack.isEmpty()) {
                continue;
            }

            counts.merge( stack.getItem(), stack.getCount(), Integer::sum );
        }

        return counts;
    }
    // IS IN DUNGEON
    private static boolean isInDungeon( ServerPlayer player ) {
        return player.level()
                .dimension()
                .equals( DUNGEON_DIMENSION );
    }
    // CLEAR PLAYER
    public static void clearPlayer( UUID playerId ) {
        PLAYER_STATES.remove( playerId );
    }
    // CLEAR ALL
    public static void clearAll() {
        PLAYER_STATES.clear();
    }
    // PLAYER LOOT STATE
    private static final class PlayerLootState {
        private final Map<Item, Integer> lastCounts = new IdentityHashMap<>();

        private final Map<Item, Integer> dungeonCounts = new IdentityHashMap<>();

        private PlayerLootState( Map<Item, Integer> initialCounts ) {
            lastCounts.putAll( initialCounts );
        }
    }
}
