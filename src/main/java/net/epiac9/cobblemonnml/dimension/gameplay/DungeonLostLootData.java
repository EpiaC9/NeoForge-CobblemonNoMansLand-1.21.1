package net.epiac9.cobblemonnml.dimension.gameplay;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungeonLostLootData extends SavedData {
    private static final String DATA_NAME = "cobblemonnml_dungeon_lost_loot";
    private static final Factory<DungeonLostLootData> FACTORY =
            new Factory<>( DungeonLostLootData::new, DungeonLostLootData::load );

    /*
     * One outstanding cemetery grave per player.
     * If the player dies in another dungeon before recovering their previous loot, the new loot is merged into the existing pending record.
     */
    private final Map<UUID, LostLootRecord> records = new HashMap<>();
    public static DungeonLostLootData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                        .computeIfAbsent( FACTORY, DATA_NAME );
    }
    public static DungeonLostLootData get(ServerPlayer player) {
        return get( player.getServer() );
    }

    /*
     * Adds lost dungeon loot for a player.
     * Existing pending loot is retained and the new items are appended to it.
     */
    public void addLostLoot( UUID playerId, String playerName, List<ItemStack> items, String themeId, String tierId ) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LostLootRecord record = records.computeIfAbsent( playerId, id -> new LostLootRecord( playerId, playerName ) );
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            /*
             * Always store a copy.
             * ItemStacks are mutable, so keeping the player's original instance would be unsafe.
             */
            record.items.add( stack.copy() );
        }
        record.playerName = playerName;
        record.themeId = themeId == null ? "" : themeId;
        record.tierId = tierId == null ? "" : tierId;

        /*
         * A new dungeon death requires recovery access again.
         */
        record.paid = false;
        setDirty();
    }
    public LostLootRecord getRecord(UUID playerId) {
        return records.get(playerId);
    }
    public boolean hasPendingLoot(UUID playerId) {
        LostLootRecord record = records.get(playerId);
        return record != null && !record.items.isEmpty();
    }
    public List<ItemStack> getLootCopy(UUID playerId) {
        LostLootRecord record = records.get(playerId);
        if (record == null) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : record.items) {
            if (!stack.isEmpty()) {
                result.add( stack.copy() );
            }
        }
        return result;
    }
    public void markPaid(UUID playerId, boolean paid) {
        LostLootRecord record = records.get(playerId);
        if (record == null) {
            return;
        }
        record.paid = paid;
        setDirty();
    }
    public boolean isPaid(UUID playerId) {
        LostLootRecord record = records.get(playerId);
        return record != null && record.paid;
    }
    public void markGraveCreated(UUID playerId, boolean created) {
        LostLootRecord record = records.get(playerId);
        if (record == null) {
            return;
        }
        record.graveCreated = created;
        setDirty();
    }
    public boolean isGraveCreated(UUID playerId) {
        LostLootRecord record = records.get(playerId);
        return record != null
                && record.graveCreated;
    }

    /*
     * Called only after the player has successfully recovered the grave.
     */
    public void removeRecord(UUID playerId) {
        if (records.remove(playerId) != null) {
            setDirty();
        }
    }
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag recordList = new ListTag();
        for (LostLootRecord record : records.values()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID( "PlayerId", record.playerId );
            recordTag.putString( "PlayerName", record.playerName );
            recordTag.putString( "Theme", record.themeId );
            recordTag.putString( "Tier", record.tierId );
            recordTag.putBoolean( "Paid", record.paid );
            recordTag.putBoolean( "GraveCreated", record.graveCreated );
            ListTag itemList = new ListTag();
            for (ItemStack stack : record.items) {
                if (stack.isEmpty()) {
                    continue;
                }
                Tag itemTag = stack.save(registries);
                itemList.add(itemTag);
            }
            recordTag.put( "Items", itemList );
            recordList.add( recordTag );
        }
        tag.put( "Records", recordList );
        return tag;
    }
    private static DungeonLostLootData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonLostLootData data = new DungeonLostLootData();
        ListTag recordList = tag.getList( "Records", Tag.TAG_COMPOUND );
        for (int i = 0; i < recordList.size(); i++) {
            CompoundTag recordTag = recordList.getCompound(i);
            if (!recordTag.hasUUID("PlayerId")) {
                continue;
            }
            UUID playerId = recordTag.getUUID( "PlayerId" );
            String playerName = recordTag.getString( "PlayerName" );
            LostLootRecord record = new LostLootRecord( playerId, playerName );
            record.themeId = recordTag.getString( "Theme" );
            record.tierId = recordTag.getString( "Tier" );
            record.paid = recordTag.getBoolean( "Paid" );
            record.graveCreated = recordTag.getBoolean( "GraveCreated" );
            ListTag itemList = recordTag.getList( "Items", Tag.TAG_COMPOUND );
            for (int itemIndex = 0; itemIndex < itemList.size(); itemIndex++) {
                CompoundTag itemTag = itemList.getCompound( itemIndex );
                ItemStack stack = ItemStack.parseOptional( registries, itemTag );
                if (!stack.isEmpty()) {
                    record.items.add(stack);
                }
            }
            if (!record.items.isEmpty()) {
                data.records.put( playerId, record );
            }
        }
        return data;
    }
    public static final class LostLootRecord {
        private final UUID playerId;
        private String playerName;
        private final List<ItemStack> items = new ArrayList<>();
        private String themeId = "";
        private String tierId = "";
        private boolean paid = false;
        private boolean graveCreated = false;
        private LostLootRecord(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
        }
        public UUID getPlayerId() {
            return playerId;
        }
        public String getPlayerName() {
            return playerName;
        }
        public List<ItemStack> getItems() {
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack stack : items) {
                result.add( stack.copy() );
            }
            return result;
        }
        public String getThemeId() {
            return themeId;
        }
        public String getTierId() {
            return tierId;
        }
        public boolean isPaid() {
            return paid;
        }
        public boolean isGraveCreated() {
            return graveCreated;
        }
    }
}
