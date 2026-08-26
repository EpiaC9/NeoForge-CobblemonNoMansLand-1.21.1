package net.epiac9.cobblemonnml.events.quest;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.events.quest.item.DungeonQuestItemMarkerManager;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class QuestRuntimeManager {
    public static final int MAX_ACTIVE_DUNGEON_QUESTS = 3;

    private QuestRuntimeManager() {
    }

    public record Result(boolean success, String message) {
    }

    public enum ItemHandInOutcome {
        NONE,
        COMPLETED,
        WRONG_ITEM,
        NOT_ENOUGH
    }

    public record ItemHandInResult(
            ItemHandInOutcome outcome,
            Result result
    ) {
    }

    private record ItemRequirement(
            QuestObjectiveDefinition objective,
            Item item,
            int count
    ) {
    }

    public static Result start(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null) {
            return new Result(false, "Invalid player or quest id.");
        }
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION) || !DungeonSession.isActive()) {
            return new Result(false, "Dungeon quests can only be started inside an active dungeon.");
        }

        QuestDefinition definition = QuestDataManager.get(questId);
        if (definition == null) {
            return new Result(false, "Unknown quest: " + questId);
        }

        ItemRequirement requirement = resolveItemRequirement(definition);
        if (requirement == null) {
            return new Result(false, "Only single-objective item quests are enabled right now.");
        }

        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);

        if (state.getActive(questId) != null) {
            return new Result(false, "Quest is already active: " + questId);
        }
        if (!definition.repeatable() && state.getCompletionCount(questId) > 0) {
            return new Result(false, "Quest has already been completed.");
        }
        if (state.getActiveCount() >= MAX_ACTIVE_DUNGEON_QUESTS) {
            return new Result(false, "You already have 3 active dungeon quests.");
        }

        UUID sessionId = DungeonSession.getSessionId();
        if (sessionId == null) {
            return new Result(false, "Dungeon session identity is unavailable.");
        }

        DungeonTheme theme = DungeonSession.getTheme();
        if (theme == null) {
            return new Result(false, "Dungeon theme is unavailable.");
        }

        ServerLevel dungeonLevel = player.serverLevel();
        DungeonQuestItemMarkerManager.ActivationResult activation =
                DungeonQuestItemMarkerManager.activate(
                        dungeonLevel,
                        theme,
                        requirement.item(),
                        requirement.count()
                );
        if (!activation.success()) {
            return new Result(false, activation.failureReason());
        }

        state.putActive(new QuestRunState(questId, sessionId));
        data.setDirty();
        DebugLog.log(
                "[CobblemonNML] Quest started: "
                        + questId
                        + " for "
                        + player.getGameProfile().getName()
                        + ". Quest Gilded Chest="
                        + activation.chestPos()
        );
        return new Result(true, "Quest started: " + definition.title());
    }

    public static Result progress(
            ServerPlayer player,
            ResourceLocation questId,
            String objectiveId,
            int amount
    ) {
        if (player == null || questId == null || objectiveId == null || objectiveId.isBlank() || amount <= 0) {
            return new Result(false, "Invalid quest progress request.");
        }

        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);
        QuestRunState run = state.getActive(questId);
        QuestDefinition definition = QuestDataManager.get(questId);
        if (run == null || definition == null) {
            return new Result(false, "Quest is not active: " + questId);
        }

        QuestObjectiveDefinition objective = findObjective(definition, objectiveId);
        if (objective == null) {
            return new Result(false, "Unknown objective: " + objectiveId);
        }
        if ("item".equals(objective.type())) {
            return new Result(false, "Item objectives must be handed to the Quest NPC.");
        }

        int newValue = Math.min(objective.target(), run.getProgress(objective.id()) + amount);
        run.setProgress(objective.id(), newValue);
        data.setDirty();

        if (run.isComplete(definition)) {
            return completeInternal(player, questId, state, data, definition);
        }
        return new Result(true, objective.id() + ": " + newValue + "/" + objective.target());
    }

    public static int progressByType(ServerPlayer player, String objectiveType, int amount) {
        if (player == null || objectiveType == null || objectiveType.isBlank() || amount <= 0) {
            return 0;
        }

        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);
        List<ResourceLocation> activeQuestIds = new ArrayList<>();
        for (QuestRunState run : state.getActiveRuns()) {
            activeQuestIds.add(run.getQuestId());
        }

        int progressedObjectives = 0;
        List<ResourceLocation> completed = new ArrayList<>();
        for (ResourceLocation questId : activeQuestIds) {
            QuestRunState run = state.getActive(questId);
            QuestDefinition definition = QuestDataManager.get(questId);
            if (run == null || definition == null) {
                continue;
            }
            for (QuestObjectiveDefinition objective : definition.objectives()) {
                if (!objective.type().equals(objectiveType) || "item".equals(objective.type())) {
                    continue;
                }
                int current = run.getProgress(objective.id());
                if (current >= objective.target()) {
                    continue;
                }
                run.setProgress(objective.id(), Math.min(objective.target(), current + amount));
                progressedObjectives++;
            }
            if (run.isComplete(definition)) {
                completed.add(questId);
            }
        }

        if (progressedObjectives > 0) {
            data.setDirty();
        }
        for (ResourceLocation questId : completed) {
            QuestDefinition definition = QuestDataManager.get(questId);
            if (definition != null && state.getActive(questId) != null) {
                completeInternal(player, questId, state, data, definition);
            }
        }
        return progressedObjectives;
    }

    /**
     * Kept as a compatibility no-op for callers from older builds. Item quests
     * are no longer completed or consumed by passive inventory scanning.
     */
    public static void scanItemObjectives(ServerLevel dungeonLevel) {
    }

    public static ItemHandInResult tryHandInItemQuest(ServerPlayer player, ItemStack heldStack) {
        if (player == null) {
            return new ItemHandInResult(
                    ItemHandInOutcome.NONE,
                    new Result(false, "Invalid player.")
            );
        }
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION) || !DungeonSession.isActive()) {
            return new ItemHandInResult(
                    ItemHandInOutcome.NONE,
                    new Result(false, "Item quests can only be handed in during the active dungeon run.")
            );
        }

        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);

        boolean hasActiveItemQuest = false;
        ItemStack stack = heldStack == null ? ItemStack.EMPTY : heldStack;

        for (QuestRunState run : new ArrayList<>(state.getActiveRuns())) {
            QuestDefinition definition = QuestDataManager.get(run.getQuestId());
            ItemRequirement requirement = resolveItemRequirement(definition);
            if (definition == null || requirement == null) {
                continue;
            }

            hasActiveItemQuest = true;
            if (stack.isEmpty() || stack.getItem() != requirement.item()) {
                continue;
            }
            if (stack.getCount() < requirement.count()) {
                return new ItemHandInResult(
                        ItemHandInOutcome.NOT_ENOUGH,
                        new Result(
                                false,
                                "You need " + requirement.count() + " of that quest item."
                        )
                );
            }

            stack.shrink(requirement.count());
            run.setProgress(requirement.objective().id(), requirement.objective().target());
            data.setDirty();

            DebugLog.log(
                    "[CobblemonNML] Quest item handed in: "
                            + requirement.count()
                            + " x "
                            + requirement.objective().itemId()
                            + " for "
                            + player.getGameProfile().getName()
            );
            Result completion = completeInternal(player, run.getQuestId(), state, data, definition);
            return new ItemHandInResult(ItemHandInOutcome.COMPLETED, completion);
        }

        if (hasActiveItemQuest) {
            return new ItemHandInResult(
                    ItemHandInOutcome.WRONG_ITEM,
                    new Result(false, "That is not the required item for an active quest.")
            );
        }

        return new ItemHandInResult(
                ItemHandInOutcome.NONE,
                new Result(false, "There is no active item quest to hand in.")
        );
    }

    public static Result complete(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null) {
            return new Result(false, "Invalid quest completion request.");
        }
        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);
        QuestRunState run = state.getActive(questId);
        QuestDefinition definition = QuestDataManager.get(questId);
        if (run == null || definition == null) {
            return new Result(false, "Quest is not active: " + questId);
        }
        if (!run.isComplete(definition)) {
            return new Result(false, "Quest objectives are not complete.");
        }
        return completeInternal(player, questId, state, data, definition);
    }

    public static Result end(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null) {
            return new Result(false, "Invalid quest end request.");
        }

        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);

        QuestDefinition definition = QuestDataManager.get(questId);
        if (definition == null) {
            return new Result(false, "Unknown quest: " + questId);
        }
        if (state.getActive(questId) == null) {
            return new Result(false, "Quest is not active: " + questId);
        }

        return completeInternal(player, questId, state, data, definition);
    }

    public static Result fail(ServerPlayer player, ResourceLocation questId) {
        if (player == null || questId == null) {
            return new Result(false, "Invalid quest failure request.");
        }
        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        if (state.removeActive(questId) == null) {
            return new Result(false, "Quest is not active: " + questId);
        }
        state.incrementFailure(questId);
        data.setDirty();
        DebugLog.log("[CobblemonNML] Quest failed: " + questId + " for " + player.getGameProfile().getName());
        return new Result(true, "Quest failed: " + questId);
    }

    public static int failAllDungeonQuests(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        List<ResourceLocation> active = new ArrayList<>();
        for (QuestRunState run : state.getActiveRuns()) {
            active.add(run.getQuestId());
        }
        for (ResourceLocation questId : active) {
            state.removeActive(questId);
            state.incrementFailure(questId);
        }
        if (!active.isEmpty()) {
            data.setDirty();
            DebugLog.log(
                    "[CobblemonNML] Failed "
                            + active.size()
                            + " active dungeon quest(s) for "
                            + player.getGameProfile().getName()
            );
        }
        return active.size();
    }

    public static int getActiveCount(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        QuestSavedData data = QuestSavedData.get(player);
        QuestPlayerState state = data.getOrCreate(player.getUUID());
        failMismatchedRuns(state, data);
        return state.getActiveCount();
    }

    private static ItemRequirement resolveItemRequirement(QuestDefinition definition) {
        if (definition == null || definition.objectives().size() != 1) {
            return null;
        }

        QuestObjectiveDefinition objective = definition.objectives().getFirst();
        if (!"item".equals(objective.type()) || objective.itemId() == null || objective.target() <= 0) {
            return null;
        }

        Item item = BuiltInRegistries.ITEM.get(objective.itemId());
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemRequirement(objective, item, objective.target());
    }

    private static Result completeInternal(
            ServerPlayer player,
            ResourceLocation questId,
            QuestPlayerState state,
            QuestSavedData data,
            QuestDefinition definition
    ) {
        state.removeActive(questId);
        state.incrementCompletion(questId);
        data.setDirty();
        QuestRewardManager.grantRewards(player, definition);
        DebugLog.log("[CobblemonNML] Quest completed: " + questId + " for " + player.getGameProfile().getName());
        return new Result(true, "Quest completed: " + definition.title());
    }

    private static QuestObjectiveDefinition findObjective(QuestDefinition definition, String objectiveId) {
        for (QuestObjectiveDefinition objective : definition.objectives()) {
            if (objective.id().equals(objectiveId)) {
                return objective;
            }
        }
        return null;
    }

    private static void failMismatchedRuns(QuestPlayerState state, QuestSavedData data) {
        UUID currentSession = DungeonSession.getSessionId();
        List<ResourceLocation> stale = new ArrayList<>();
        for (QuestRunState run : state.getActiveRuns()) {
            if (currentSession == null || !currentSession.equals(run.getDungeonSessionId())) {
                stale.add(run.getQuestId());
            }
        }
        for (ResourceLocation questId : stale) {
            state.removeActive(questId);
            state.incrementFailure(questId);
        }
        if (!stale.isEmpty()) {
            data.setDirty();
        }
    }
}
