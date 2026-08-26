package net.epiac9.cobblemonnml.overworld.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownRecruitmentSavedData extends SavedData {
    private static final String DATA_NAME = "cobblemonnml_town_recruitment";
    private static final Factory<TownRecruitmentSavedData> FACTORY =
            new Factory<>(TownRecruitmentSavedData::new, TownRecruitmentSavedData::load);

    private final Map<String, RecruitmentRecord> recruited = new HashMap<>();

    public static TownRecruitmentSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean isRecruited(String recruitmentId) {
        String normalized = normalize(recruitmentId);
        return normalized != null && recruited.containsKey(normalized);
    }

    public RecruitmentRecord getRecord(String recruitmentId) {
        String normalized = normalize(recruitmentId);
        return normalized == null ? null : recruited.get(normalized);
    }

    public void markRecruited(String recruitmentId, UUID npcUuid, BlockPos structureOrigin) {
        String normalized = normalize(recruitmentId);
        if (normalized == null || npcUuid == null || structureOrigin == null) {
            return;
        }
        recruited.put(normalized, new RecruitmentRecord(npcUuid, structureOrigin.immutable()));
        setDirty();
    }

    @Override
    public @NotNull CompoundTag save(
            @NotNull CompoundTag tag,
            @NotNull HolderLookup.Provider registries
    ) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, RecruitmentRecord> entry : recruited.entrySet()) {
            RecruitmentRecord record = entry.getValue();
            if (record == null || record.npcUuid() == null || record.structureOrigin() == null) {
                continue;
            }
            CompoundTag recordTag = new CompoundTag();
            recordTag.putString("Id", entry.getKey());
            recordTag.putUUID("NpcUuid", record.npcUuid());
            recordTag.putLong("StructureOrigin", record.structureOrigin().asLong());
            entries.add(recordTag);
        }
        tag.put("Recruitments", entries);
        return tag;
    }

    private static TownRecruitmentSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        TownRecruitmentSavedData data = new TownRecruitmentSavedData();
        ListTag entries = tag.getList("Recruitments", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag recordTag = entries.getCompound(i);
            if (!recordTag.contains("Id", Tag.TAG_STRING)
                    || !recordTag.hasUUID("NpcUuid")
                    || !recordTag.contains("StructureOrigin", Tag.TAG_LONG)) {
                continue;
            }
            String id = normalize(recordTag.getString("Id"));
            if (id == null) {
                continue;
            }
            data.recruited.put(
                    id,
                    new RecruitmentRecord(
                            recordTag.getUUID("NpcUuid"),
                            BlockPos.of(recordTag.getLong("StructureOrigin"))
                    )
            );
        }
        return data;
    }

    private static String normalize(String recruitmentId) {
        if (recruitmentId == null) {
            return null;
        }
        String normalized = recruitmentId.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public record RecruitmentRecord(UUID npcUuid, BlockPos structureOrigin) {
    }
}
