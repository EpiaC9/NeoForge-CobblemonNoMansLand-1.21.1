package net.epiac9.cobblemonnml.events.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class QuestSavedData extends SavedData {
    private static final String DATA_NAME = "cobblemonnml_dungeon_quests";
    private static final Factory<QuestSavedData> FACTORY = new Factory<>(QuestSavedData::new, QuestSavedData::load);

    private final Map<UUID, QuestPlayerState> players = new HashMap<>();

    public static QuestSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static QuestSavedData get(ServerPlayer player) {
        return get(player.getServer());
    }

    public QuestPlayerState getOrCreate(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new QuestPlayerState());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, QuestPlayerState> playerEntry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("PlayerId", playerEntry.getKey());
            QuestPlayerState state = playerEntry.getValue();
            playerTag.put("Completions", saveCounts(state.completionCounts()));
            playerTag.put("Failures", saveCounts(state.failureCounts()));

            ListTag activeList = new ListTag();
            for (QuestRunState run : state.getActiveRuns()) {
                CompoundTag runTag = new CompoundTag();
                runTag.putString("Quest", run.getQuestId().toString());
                if (run.getDungeonSessionId() != null) {
                    runTag.putUUID("Session", run.getDungeonSessionId());
                }
                ListTag progressList = new ListTag();
                for (Map.Entry<String, Integer> progress : run.getProgressSnapshot().entrySet()) {
                    CompoundTag progressTag = new CompoundTag();
                    progressTag.putString("Objective", progress.getKey());
                    progressTag.putInt("Value", progress.getValue());
                    progressList.add(progressTag);
                }
                runTag.put("Progress", progressList);
                activeList.add(runTag);
            }
            playerTag.put("Active", activeList);
            playerList.add(playerTag);
        }
        tag.put("Players", playerList);
        return tag;
    }

    private static ListTag saveCounts(Map<ResourceLocation, Integer> counts) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
            CompoundTag countTag = new CompoundTag();
            countTag.putString("Quest", entry.getKey().toString());
            countTag.putInt("Count", entry.getValue());
            list.add(countTag);
        }
        return list;
    }

    private static QuestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestSavedData data = new QuestSavedData();
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            if (!playerTag.hasUUID("PlayerId")) {
                continue;
            }
            UUID playerId = playerTag.getUUID("PlayerId");
            QuestPlayerState state = new QuestPlayerState();
            loadCounts(playerTag.getList("Completions", Tag.TAG_COMPOUND), state.completionCounts());
            loadCounts(playerTag.getList("Failures", Tag.TAG_COMPOUND), state.failureCounts());

            ListTag activeList = playerTag.getList("Active", Tag.TAG_COMPOUND);
            for (int runIndex = 0; runIndex < activeList.size(); runIndex++) {
                CompoundTag runTag = activeList.getCompound(runIndex);
                ResourceLocation questId = ResourceLocation.tryParse(runTag.getString("Quest"));
                if (questId == null || !runTag.hasUUID("Session")) {
                    continue;
                }
                QuestRunState run = new QuestRunState(questId, runTag.getUUID("Session"));
                ListTag progressList = runTag.getList("Progress", Tag.TAG_COMPOUND);
                for (int progressIndex = 0; progressIndex < progressList.size(); progressIndex++) {
                    CompoundTag progressTag = progressList.getCompound(progressIndex);
                    run.setProgress(progressTag.getString("Objective"), progressTag.getInt("Value"));
                }
                state.putActive(run);
            }
            data.players.put(playerId, state);
        }
        return data;
    }

    private static void loadCounts(ListTag list, Map<ResourceLocation, Integer> destination) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag countTag = list.getCompound(i);
            ResourceLocation questId = ResourceLocation.tryParse(countTag.getString("Quest"));
            int count = countTag.getInt("Count");
            if (questId != null && count > 0) {
                destination.put(questId, count);
            }
        }
    }
}
