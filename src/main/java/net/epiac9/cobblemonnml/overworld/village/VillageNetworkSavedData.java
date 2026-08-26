package net.epiac9.cobblemonnml.overworld.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VillageNetworkSavedData extends SavedData {
    // SAVED DATA
    private static final String DATA_NAME = "cobblemonnml_village_network";

    private static final Factory<VillageNetworkSavedData> FACTORY =
            new Factory<>(
                    VillageNetworkSavedData::new,
                    VillageNetworkSavedData::load
            );
    // NETWORK STATE
    private final Map<UUID, VillageStructureInstance> structures =
            new LinkedHashMap<>();

    private final Map<VillageConnectionKey, VillageConnectionState> connectionStates =
            new LinkedHashMap<>();

    private final Set<BlockPos> roadCells =
            new LinkedHashSet<>();
    // GET
    public static VillageNetworkSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();

        return overworld
                .getDataStorage()
                .computeIfAbsent(
                        FACTORY,
                        DATA_NAME
                );
    }
    // STRUCTURES
    public boolean registerStructure(VillageStructureInstance structure) {
        if (structure == null) {
            return false;
        }

        if (structures.containsKey(structure.id())) {
            return false;
        }

        structures.put(
                structure.id(),
                copyStructure( structure )
        );

        setDirty();
        return true;
    }

    public VillageStructureInstance getStructure(UUID structureId) {
        if (structureId == null) {
            return null;
        }

        VillageStructureInstance structure = structures.get( structureId );

        if (structure == null) {
            return null;
        }

        return copyStructure( structure );
    }

    public boolean hasStructure(UUID structureId) {
        if (structureId == null) {
            return false;
        }

        return structures.containsKey( structureId );
    }

    public List<VillageStructureInstance> getStructures() {
        List<VillageStructureInstance> result = new ArrayList<>();

        for (VillageStructureInstance structure : structures.values()) {
            result.add( copyStructure(structure) );
        }

        return Collections.unmodifiableList( result );
    }

    public int getStructureCount() {
        return structures.size();
    }

    public boolean removeStructure(UUID structureId) {
        if (structureId == null) {
            return false;
        }

        VillageStructureInstance removed = structures.remove( structureId );

        if (removed == null) {
            return false;
        }

        connectionStates.keySet().removeIf(
                connection ->
                        connection.first().equals(structureId)
                                || connection.second().equals(structureId)
        );

        setDirty();
        return true;
    }
    // CONNECTIONS
    public VillageConnectionState getConnectionState(
            UUID first,
            UUID second
    ) {
        if (!isValidPair(first, second)) {
            return VillageConnectionState.UNSEEN;
        }

        return getConnectionState(
                VillageConnectionKey.of(
                        first,
                        second
                )
        );
    }

    public VillageConnectionState getConnectionState(
            VillageConnectionKey key
    ) {
        if (key == null) {
            return VillageConnectionState.UNSEEN;
        }

        return connectionStates.getOrDefault(
                key,
                VillageConnectionState.UNSEEN
        );
    }

    public boolean hasConnection(UUID first, UUID second) {
        return getConnectionState(first, second)
                == VillageConnectionState.COMPLETED;
    }

    public boolean markConnectionQueued(
            VillageConnectionKey key
    ) {
        return setConnectionState(
                key,
                VillageConnectionState.QUEUED
        );
    }

    public boolean markConnectionBuilding(
            VillageConnectionKey key
    ) {
        return setConnectionState(
                key,
                VillageConnectionState.BUILDING
        );
    }

    public boolean markConnectionDeferred(
            VillageConnectionKey key
    ) {
        return setConnectionState(
                key,
                VillageConnectionState.DEFERRED
        );
    }

    public boolean completeConnection(UUID first, UUID second) {
        if (!isValidPair(first, second)) {
            return false;
        }

        return completeConnection(
                VillageConnectionKey.of(
                        first,
                        second
                )
        );
    }

    public boolean completeConnection(
            VillageConnectionKey key
    ) {
        if (!pairStructuresExist(key)) {
            return false;
        }

        VillageConnectionState previous =
                getConnectionState(key);

        if (previous == VillageConnectionState.COMPLETED) {
            return false;
        }

        connectionStates.put(
                key,
                VillageConnectionState.COMPLETED
        );

        setDirty();
        return true;
    }

    public boolean removeConnectionState(
            VillageConnectionKey key
    ) {
        if (key == null) {
            return false;
        }

        VillageConnectionState removed =
                connectionStates.remove(key);

        if (removed == null) {
            return false;
        }

        setDirty();
        return true;
    }

    public Set<VillageConnectionKey> getCompletedConnections() {
        Set<VillageConnectionKey> result =
                new LinkedHashSet<>();

        for (Map.Entry<VillageConnectionKey, VillageConnectionState> entry
                : connectionStates.entrySet()) {
            if (entry.getValue()
                    == VillageConnectionState.COMPLETED) {
                result.add(
                        entry.getKey()
                );
            }
        }

        return Collections.unmodifiableSet(
                result
        );
    }

    public Map<VillageConnectionKey, VillageConnectionState> getConnectionStates() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        connectionStates
                )
        );
    }

    public Set<VillageConnectionKey> getRecoverableConnections() {
        Set<VillageConnectionKey> result =
                new LinkedHashSet<>();

        for (Map.Entry<VillageConnectionKey, VillageConnectionState> entry
                : connectionStates.entrySet()) {
            if (!entry.getValue().isRecoverable()) {
                continue;
            }

            if (!pairStructuresExist(
                    entry.getKey()
            )) {
                continue;
            }

            result.add(
                    entry.getKey()
            );
        }

        return Collections.unmodifiableSet(
                result
        );
    }

    private boolean setConnectionState(
            VillageConnectionKey key,
            VillageConnectionState next
    ) {
        if (key == null
                || next == null
                || next == VillageConnectionState.UNSEEN
                || !pairStructuresExist(key)) {
            return false;
        }

        VillageConnectionState current =
                getConnectionState(key);

        /*
         * COMPLETED is terminal. Never allow restart recovery,
         * duplicate scans, or a stale transient job to downgrade it.
         */
        if (current == VillageConnectionState.COMPLETED) {
            return next == VillageConnectionState.COMPLETED;
        }

        if (current == next) {
            return false;
        }

        connectionStates.put(
                key,
                next
        );

        setDirty();
        return true;
    }

    private boolean pairStructuresExist(
            VillageConnectionKey key
    ) {
        return key != null
                && structures.containsKey(
                        key.first()
                )
                && structures.containsKey(
                        key.second()
                );
    }

    private static boolean isValidPair(
            UUID first,
            UUID second
    ) {
        return first != null
                && second != null
                && !first.equals(second);
    }
    // ROAD CELLS
    public boolean addRoadCell(VillageRoadCell roadCell) {
        if (roadCell == null) {
            return false;
        }

        boolean changed =
                roadCells.add(
                        roadCell.pos().immutable()
                );

        if (changed) {
            setDirty();
        }

        return changed;
    }

    public int addRoadCells(Collection<VillageRoadCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return 0;
        }

        int added = 0;

        for (VillageRoadCell roadCell : cells) {
            if (roadCell == null) {
                continue;
            }

            if (roadCells.add(roadCell.pos().immutable())) {
                added++;
            }
        }

        if (added > 0) {
            setDirty();
        }

        return added;
    }

    public boolean isRoadCell(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        return roadCells.contains( pos );
    }

    public Set<BlockPos> getRoadCells() {
        Set<BlockPos> result = new LinkedHashSet<>();

        for (BlockPos pos : roadCells) {
            result.add( pos.immutable() );
        }

        return Collections.unmodifiableSet( result );
    }

    public int getRoadCellCount() {
        return roadCells.size();
    }
    // CLEAR
    public void clearAll() {
        if (structures.isEmpty()
                && connectionStates.isEmpty()
                && roadCells.isEmpty()) {
            return;
        }

        structures.clear();
        connectionStates.clear();
        roadCells.clear();

        setDirty();
    }
    // SAVE
    @Override
    public @NotNull CompoundTag save(
            @NotNull CompoundTag tag,
            @NotNull HolderLookup.Provider registries
    ) {
        // STRUCTURES
        ListTag structureList = new ListTag();

        for (VillageStructureInstance structure : structures.values()) {
            CompoundTag structureTag = new CompoundTag();

            structureTag.putUUID( "Id", structure.id() );
            structureTag.putString( "Type", structure.type().toString() );

            BoundingBox bounds = structure.bounds();

            structureTag.putInt( "MinX", bounds.minX() );
            structureTag.putInt( "MinY", bounds.minY() );
            structureTag.putInt( "MinZ", bounds.minZ() );
            structureTag.putInt( "MaxX", bounds.maxX() );
            structureTag.putInt( "MaxY", bounds.maxY() );
            structureTag.putInt( "MaxZ", bounds.maxZ() );

            ListTag entranceList = new ListTag();

            for (VillageEntrance entrance : structure.entrances()) {
                CompoundTag entranceTag = new CompoundTag();

                entranceTag.putLong( "Pos", entrance.pos().asLong() );
                entranceTag.putString(
                        "Facing",
                        entrance.facing().getName()
                );

                entranceList.add( entranceTag );
            }

            structureTag.put( "Entrances", entranceList );

            structureList.add( structureTag );
        }

        tag.put( "Structures", structureList );
        // CONNECTION LIFECYCLE
        ListTag stateList =
                new ListTag();

        for (Map.Entry<VillageConnectionKey, VillageConnectionState> entry
                : connectionStates.entrySet()) {
            CompoundTag stateTag =
                    new CompoundTag();

            stateTag.putUUID(
                    "First",
                    entry.getKey().first()
            );

            stateTag.putUUID(
                    "Second",
                    entry.getKey().second()
            );

            stateTag.putString(
                    "State",
                    entry.getValue().name()
            );

            stateList.add(
                    stateTag
            );
        }

        tag.put(
                "ConnectionStates",
                stateList
        );

        /*
         * Keep writing the old completed-only list for safe backwards
         * compatibility with worlds created before Step 10.
         */
        ListTag connectionList =
                new ListTag();

        for (VillageConnectionKey connection : getCompletedConnections()) {
            CompoundTag connectionTag =
                    new CompoundTag();

            connectionTag.putUUID(
                    "First",
                    connection.first()
            );

            connectionTag.putUUID(
                    "Second",
                    connection.second()
            );

            connectionList.add(
                    connectionTag
            );
        }

        tag.put(
                "CompletedConnections",
                connectionList
        );
        // GENERATED ROAD CELLS
        long[] roadCellArray = new long[roadCells.size()];

        int roadCellIndex = 0;

        for (BlockPos pos : roadCells) {
            roadCellArray[roadCellIndex++] = pos.asLong();
        }

        tag.putLongArray( "RoadCells", roadCellArray );

        return tag;
    }
    // LOAD
    private static VillageNetworkSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        VillageNetworkSavedData data =
                new VillageNetworkSavedData();
        // STRUCTURES
        ListTag structureList =
                tag.getList(
                        "Structures",
                        Tag.TAG_COMPOUND
                );

        for (int i = 0; i < structureList.size(); i++) {
            CompoundTag structureTag =
                    structureList.getCompound( i );

            if (!structureTag.hasUUID("Id")) {
                continue;
            }

            if (!structureTag.contains("Type", Tag.TAG_STRING)) {
                continue;
            }

            ResourceLocation type =
                    ResourceLocation.tryParse(
                            structureTag.getString("Type")
                    );

            if (type == null) {
                continue;
            }

            if (!hasBounds(structureTag)) {
                continue;
            }

            UUID id = structureTag.getUUID( "Id" );

            BoundingBox bounds =
                    new BoundingBox(
                            structureTag.getInt("MinX"),
                            structureTag.getInt("MinY"),
                            structureTag.getInt("MinZ"),
                            structureTag.getInt("MaxX"),
                            structureTag.getInt("MaxY"),
                            structureTag.getInt("MaxZ")
                    );

            List<VillageEntrance> entrances =
                    new ArrayList<>();

            ListTag entranceList =
                    structureTag.getList(
                            "Entrances",
                            Tag.TAG_COMPOUND
                    );

            for (int entranceIndex = 0;
                 entranceIndex < entranceList.size();
                 entranceIndex++) {
                CompoundTag entranceTag =
                        entranceList.getCompound(entranceIndex);

                if (!entranceTag.contains("Pos", Tag.TAG_LONG)) {
                    continue;
                }

                if (!entranceTag.contains("Facing", Tag.TAG_STRING)) {
                    continue;
                }

                Direction facing =
                        Direction.byName(
                                entranceTag.getString("Facing")
                        );

                if (facing == null || facing.getAxis().isVertical()) {
                    continue;
                }

                entrances.add(
                        new VillageEntrance(
                                BlockPos.of(
                                        entranceTag.getLong("Pos")
                                ),
                                facing
                        )
                );
            }

            data.structures.put(
                    id,
                    new VillageStructureInstance(
                            id,
                            type,
                            bounds,
                            entrances
                    )
            );
        }
        // LEGACY COMPLETED CONNECTIONS
        ListTag connectionList =
                tag.getList(
                        "CompletedConnections",
                        Tag.TAG_COMPOUND
                );

        for (int i = 0;
             i < connectionList.size();
             i++) {
            CompoundTag connectionTag =
                    connectionList.getCompound(i);

            VillageConnectionKey key =
                    readConnectionKey(
                            connectionTag,
                            data
                    );

            if (key == null) {
                continue;
            }

            data.connectionStates.put(
                    key,
                    VillageConnectionState.COMPLETED
            );
        }
        // STEP 10 CONNECTION LIFECYCLE
        ListTag stateList =
                tag.getList(
                        "ConnectionStates",
                        Tag.TAG_COMPOUND
                );

        for (int i = 0;
             i < stateList.size();
             i++) {
            CompoundTag stateTag =
                    stateList.getCompound(i);

            VillageConnectionKey key =
                    readConnectionKey(
                            stateTag,
                            data
                    );

            if (key == null
                    || !stateTag.contains(
                            "State",
                            Tag.TAG_STRING
                    )) {
                continue;
            }

            VillageConnectionState state;

            try {
                state =
                        VillageConnectionState.valueOf(
                                stateTag.getString(
                                        "State"
                                )
                        );
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            if (state == VillageConnectionState.UNSEEN) {
                continue;
            }

            data.connectionStates.put(
                    key,
                    state
            );
        }
        // GENERATED ROAD CELLS
        long[] roadCellArray =
                tag.getLongArray( "RoadCells" );

        for (long packedPos : roadCellArray) {
            data.roadCells.add(
                    BlockPos.of(packedPos)
            );
        }

        return data;
    }
    // HELPERS
    private static VillageConnectionKey readConnectionKey(
            CompoundTag tag,
            VillageNetworkSavedData data
    ) {
        if (tag == null
                || data == null
                || !tag.hasUUID("First")
                || !tag.hasUUID("Second")) {
            return null;
        }

        UUID first =
                tag.getUUID(
                        "First"
                );

        UUID second =
                tag.getUUID(
                        "Second"
                );

        if (first.equals(second)
                || !data.structures.containsKey(first)
                || !data.structures.containsKey(second)) {
            return null;
        }

        return VillageConnectionKey.of(
                first,
                second
        );
    }

    private static boolean hasBounds(CompoundTag tag) {
        return tag.contains("MinX", Tag.TAG_INT)
                && tag.contains("MinY", Tag.TAG_INT)
                && tag.contains("MinZ", Tag.TAG_INT)
                && tag.contains("MaxX", Tag.TAG_INT)
                && tag.contains("MaxY", Tag.TAG_INT)
                && tag.contains("MaxZ", Tag.TAG_INT);
    }

    private static VillageStructureInstance copyStructure(
            VillageStructureInstance source
    ) {
        return new VillageStructureInstance(
                source.id(),
                source.type(),
                source.bounds(),
                source.entrances()
        );
    }
}
