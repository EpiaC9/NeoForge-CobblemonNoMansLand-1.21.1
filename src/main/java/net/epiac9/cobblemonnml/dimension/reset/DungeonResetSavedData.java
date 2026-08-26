package net.epiac9.cobblemonnml.dimension.reset;

import net.epiac9.cobblemonnml.dimension.DungeonSlotManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DungeonResetSavedData extends SavedData {
    // FILE NAME
    private static final String DATA_NAME = "cobblemonnml_dungeon_reset";
    // SAVED RESET STATES
    /*
     * One independent persisted reset cursor per logical dungeon slot.
     * The old implementation stored only one reset globally. A later slot beginning cleanup overwrote the previous slot's
     * persisted boxes/cursor, which meant only one of several simultaneous reset jobs could reliably survive a shutdown.
     */
    private final Map<DungeonSlotManager.Slot, ResetState> resets = new EnumMap<>( DungeonSlotManager.Slot.class );
    // CREATE
    public static DungeonResetSavedData create() {
        return new DungeonResetSavedData();
    }
    // LOAD
    public static DungeonResetSavedData load( CompoundTag tag, HolderLookup.Provider registries ) {
        DungeonResetSavedData data = new DungeonResetSavedData();
        // CURRENT MULTI-SLOT FORMAT
        ListTag resetList = tag.getList( "Resets", CompoundTag.TAG_COMPOUND );
        for (int i = 0; i < resetList.size(); i++) {
            CompoundTag resetTag = resetList.getCompound(i);
            DungeonSlotManager.Slot slot = readSlot( resetTag );
            if (slot == null) {
                continue;
            }
            ResetState state = readResetState( resetTag, slot );
            if (state == null || state.boxes.isEmpty()) {
                continue;
            }
            data.resets.put( slot, state );
        }
        // LEGACY SINGLE-RESET MIGRATION
        /*
         * Existing worlds may still contain the old top-level
         * format:
         * ResetPending
         * Slot
         * CurrentBoxIndex
         * CurrentX
         * Boxes
         * If no multi-slot entries were loaded, migrate that one
         * reset into the new map in memory. The next normal world
         * save writes only the new Resets list.
         */
        if (data.resets.isEmpty() && tag.getBoolean("ResetPending")) {
            DungeonSlotManager.Slot legacySlot = readSlot( tag );
            if (legacySlot != null) {
                ResetState legacyState = readResetState( tag, legacySlot );
                if (legacyState != null && !legacyState.boxes.isEmpty()) {
                    data.resets.put( legacySlot, legacyState );
                    data.setDirty();
                }
            }
        }
        return data;
    }
    // SAVE
    @Override
    public @NotNull CompoundTag save( @NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries ) {
        ListTag resetList = new ListTag();

        /*
         * Write in stable enum order so the file remains easy to inspect while debugging.
         */
        for (DungeonSlotManager.Slot slot : DungeonSlotManager.Slot.values()) {
            ResetState state = resets.get( slot );
            if (state == null || state.boxes.isEmpty()) {
                continue;
            }
            CompoundTag resetTag = createResetTag(slot, state);
            resetTag.put( "Boxes", writeBoxes( state.boxes ) );
            resetList.add( resetTag );
        }
        tag.put( "Resets", resetList );

        /*
         * Do not continue writing the legacy single-reset fields.
         * load() still understands them so existing worlds migrate safely, but all future saves use the multi-slot format.
         */
        return tag;
    }
    private static @NotNull CompoundTag createResetTag(DungeonSlotManager.Slot slot, ResetState state) {
        CompoundTag resetTag = new CompoundTag();
        resetTag.putString( "Slot", slot.name() );
        resetTag.putInt( "CurrentBoxIndex", state.currentBoxIndex );
        resetTag.putInt( "CurrentX", state.currentX );
        // SLOT ORIGIN
        if (state.slotOrigin != null) {
            resetTag.putBoolean( "HasSlotOrigin", true );
            resetTag.putInt( "SlotOriginX", state.slotOrigin.getX() );
            resetTag.putInt( "SlotOriginY", state.slotOrigin.getY() );
            resetTag.putInt( "SlotOriginZ", state.slotOrigin.getZ() );
        }
        return resetTag;
    }
    // GET DATA
    public static DungeonResetSavedData get( MinecraftServer server ) {

        /*
         * Store global dungeon data on the Overworld.
         * NeoForge recommends the Overworld for SavedData that is not specific to one dimension.
         */
        return server
                .overworld()
                .getDataStorage()
                .computeIfAbsent(
                        new Factory<>( DungeonResetSavedData::create, DungeonResetSavedData::load ),
                        DATA_NAME
                );
    }
    // BEGIN RESET
    public void beginReset( DungeonSlotManager.Slot slot, Iterable<BoundingBox> bounds, BlockPos slotOrigin ) {
        if (slot == null || bounds == null) {
            return;
        }
        ResetState state = new ResetState();
        for (BoundingBox box : bounds) {
            if (box != null) {
                state.boxes.add( box );
            }
        }
        if (state.boxes.isEmpty()) {
            resets.remove( slot );
            setDirty();
            return;
        }
        state.currentBoxIndex = 0;
        state.currentX =
                state.boxes.getFirst()
                        .minX();
        state.slotOrigin = slotOrigin == null ? null : slotOrigin.immutable();
        resets.put( slot, state );
        setDirty();
    }
    // UPDATE PROGRESS
    public void updateProgress( DungeonSlotManager.Slot slot, int currentBoxIndex, int currentX ) {
        if (slot == null) {
            return;
        }
        ResetState state = resets.get( slot );
        if (state == null) {
            return;
        }
        state.currentBoxIndex = currentBoxIndex;
        state.currentX = currentX;
        setDirty();
    }
    // FINISH ONE SLOT
    public void finishReset( DungeonSlotManager.Slot slot ) {
        if (slot == null) {
            return;
        }
        if (resets.remove( slot ) != null) {
            setDirty();
        }
    }
    // PENDING STATE
    public boolean hasPendingResets() {
        return !resets.isEmpty();
    }
    public boolean isResetPending( DungeonSlotManager.Slot slot ) {
        return slot != null
                && resets.containsKey( slot );
    }
    public List<DungeonSlotManager.Slot> getPendingSlots() {
        List<DungeonSlotManager.Slot> pending = new ArrayList<>();
        for (DungeonSlotManager.Slot slot : DungeonSlotManager.Slot.values()) {
            if (resets.containsKey(slot)) {
                pending.add( slot );
            }
        }
        return pending;
    }
    // SLOT-SPECIFIC GETTERS
    public int getCurrentBoxIndex(DungeonSlotManager.Slot slot) {
        ResetState state = resets.get( slot );
        return state == null ? 0 : state.currentBoxIndex;
    }
    public int getCurrentX(DungeonSlotManager.Slot slot) {
        ResetState state = resets.get( slot );
        return state == null
                ? 0
                : state.currentX;
    }
    public List<BoundingBox> getBoxes(DungeonSlotManager.Slot slot) {
        ResetState state = resets.get( slot );
        if (state == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>( state.boxes );
    }
    public BlockPos getSlotOrigin(DungeonSlotManager.Slot slot) {
        ResetState state = resets.get( slot );
        if (state == null || state.slotOrigin == null) {
            return null;
        }
        return state.slotOrigin.immutable();
    }
    // READ RESET STATE
    private static ResetState readResetState( CompoundTag tag, DungeonSlotManager.Slot slot ) {
        if (tag == null || slot == null) {
            return null;
        }
        ResetState state = new ResetState();
        state.currentBoxIndex = tag.getInt( "CurrentBoxIndex" );
        state.currentX = tag.getInt( "CurrentX" );
        state.boxes.addAll( readBoxes( tag ) );
        // SLOT ORIGIN
        if (tag.getBoolean( "HasSlotOrigin" )) {
            state.slotOrigin =
                    new BlockPos(
                            tag.getInt( "SlotOriginX" ),
                            tag.getInt( "SlotOriginY" ),
                            tag.getInt( "SlotOriginZ" )
                    );
        } else {

            /*
             * Legacy saves never stored the origin. Current slot origins are deterministic, so derive it during
             * migration. This also upgrades the resumed entity sweep from the old expanded-bounds fallback to the
             * normal broad slot sweep.
             */
            BlockPos defaultOrigin = DungeonSlotManager.getOrigin( slot );
            state.slotOrigin = defaultOrigin == null ? null : defaultOrigin.immutable();
        }
        return state;
    }
    // READ SLOT
    private static DungeonSlotManager.Slot readSlot(CompoundTag tag) {
        if (tag == null || !tag.contains( "Slot" )) {
            return null;
        }
        String slotName = tag.getString( "Slot" );
        try {
            return DungeonSlotManager.Slot.valueOf( slotName );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
    // READ BOXES
    private static List<BoundingBox> readBoxes(CompoundTag tag) {
        List<BoundingBox> boxes = new ArrayList<>();
        ListTag boxList = tag.getList( "Boxes", CompoundTag.TAG_COMPOUND );
        for (int i = 0; i < boxList.size(); i++) {
            CompoundTag boxTag = boxList.getCompound( i );
            boxes.add(
                    new BoundingBox(
                            boxTag.getInt("MinX"),
                            boxTag.getInt("MinY"),
                            boxTag.getInt("MinZ"),
                            boxTag.getInt("MaxX"),
                            boxTag.getInt("MaxY"),
                            boxTag.getInt("MaxZ")
                    )
            );
        }
        return boxes;
    }
    // WRITE BOXES
    private static ListTag writeBoxes( List<BoundingBox> boxes ) {
        ListTag boxList = new ListTag();
        if (boxes == null) {
            return boxList;
        }
        for (BoundingBox box : boxes) {
            if (box == null) {
                continue;
            }
            CompoundTag boxTag = new CompoundTag();
            boxTag.putInt( "MinX", box.minX() );
            boxTag.putInt( "MinY", box.minY() );
            boxTag.putInt( "MinZ", box.minZ() );
            boxTag.putInt( "MaxX", box.maxX() );
            boxTag.putInt( "MaxY", box.maxY() );
            boxTag.putInt( "MaxZ", box.maxZ() );
            boxList.add( boxTag );
        }
        return boxList;
    }
    // RESET STATE
    private static final class ResetState {
        private final List<BoundingBox> boxes = new ArrayList<>();
        private int currentBoxIndex = 0;
        private int currentX = 0;
        private BlockPos slotOrigin = null;
    }
}
