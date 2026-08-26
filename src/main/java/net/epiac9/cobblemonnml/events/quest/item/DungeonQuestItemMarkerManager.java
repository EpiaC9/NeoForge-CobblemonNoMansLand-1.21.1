package net.epiac9.cobblemonnml.events.quest.item;

import com.cobblemon.mod.common.CobblemonBlocks;

import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class DungeonQuestItemMarkerManager {
    private static final String MOD_ID = "cobblemonnml";
    private static final Map<ServerLevel, RunState> RUNS = new IdentityHashMap<>();

    private DungeonQuestItemMarkerManager() {
    }

    public record ActivationResult(
            boolean success,
            BlockPos chestPos,
            int gimmighoulSpawned,
            String failureReason
    ) {
        public static ActivationResult failure(String reason) {
            return new ActivationResult(false, null, 0, reason);
        }
    }

    static final class MarkerAllocation {
        private final BlockPos chestPos;
        private final List<BlockPos> gimmighoulPositions;

        private MarkerAllocation(BlockPos chestPos, List<BlockPos> gimmighoulPositions) {
            this.chestPos = chestPos;
            this.gimmighoulPositions = List.copyOf(gimmighoulPositions);
        }

        BlockPos chestPos() {
            return chestPos;
        }

        List<BlockPos> gimmighoulPositions() {
            return gimmighoulPositions;
        }
    }

    private static final class RunState {
        private final LinkedHashSet<BlockPos> markers = new LinkedHashSet<>();
        private boolean activated;
        private BlockPos selectedChestPos;
    }

    public static void recordMarker(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return;
        }
        RunState state = RUNS.computeIfAbsent(level, ignored -> new RunState());
        BlockPos immutablePos = pos.immutable();
        if (state.markers.add(immutablePos)) {
            DebugLog.log("[CobblemonNML] Captured quest-item marker at " + immutablePos + ".");
        }
    }

    public static List<BlockPos> getAvailableMarkers(ServerLevel level) {
        RunState state = RUNS.get(level);
        if (state == null || state.activated) {
            return List.of();
        }
        return List.copyOf(state.markers);
    }

    public static boolean hasAvailableMarkers(ServerLevel level) {
        return !getAvailableMarkers(level).isEmpty();
    }

    public static boolean isActivated(ServerLevel level) {
        RunState state = RUNS.get(level);
        return state != null && state.activated;
    }

    public static BlockPos getSelectedChestPos(ServerLevel level) {
        RunState state = RUNS.get(level);
        return state == null || state.selectedChestPos == null ? null : state.selectedChestPos.immutable();
    }

    public static int getRecordedMarkerCount(ServerLevel level) {
        RunState state = RUNS.get(level);
        return state == null ? 0 : state.markers.size();
    }

    public static void clear(ServerLevel level) {
        if (level != null) {
            RUNS.remove(level);
        }
    }

    public static void clearAll() {
        RUNS.clear();
    }

    public static ActivationResult activate(
            ServerLevel level,
            DungeonTheme theme,
            Item requiredItem,
            int requiredCount
    ) {
        if (level == null || theme == null || requiredItem == null || requiredCount <= 0) {
            return ActivationResult.failure("Invalid quest-item activation request.");
        }

        RunState state = RUNS.get(level);
        if (state == null || state.markers.isEmpty()) {
            return ActivationResult.failure("This dungeon has no quest-item markers.");
        }
        if (state.activated) {
            return ActivationResult.failure("The quest-item locations in this dungeon have already been activated.");
        }

        MarkerAllocation allocation = allocate(new ArrayList<>(state.markers), level.getRandom());
        if (allocation == null || allocation.chestPos() == null) {
            return ActivationResult.failure("This dungeon has no available quest-item marker.");
        }

        Container questChest = placeGildedChest(level, allocation.chestPos());
        if (questChest == null) {
            return ActivationResult.failure("The Gilded Chest could not be placed.");
        }

        if (!fillQuestChest(level, theme, allocation.chestPos(), questChest, requiredItem, requiredCount)) {
            level.setBlock(allocation.chestPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return ActivationResult.failure("The Gilded Chest could not hold the full quest item requirement.");
        }

        int spawned = 0;
        for (BlockPos gimmighoulPos : allocation.gimmighoulPositions()) {
            if (placeGimmighoulChest(level, gimmighoulPos)) {
                spawned++;
            }
        }

        state.activated = true;
        state.selectedChestPos = allocation.chestPos().immutable();

        DebugLog.log(
                "[CobblemonNML] Quest-item locations activated. Gilded Chest="
                        + state.selectedChestPos
                        + ", Gimmighoul Chest="
                        + spawned
                        + "/"
                        + allocation.gimmighoulPositions().size()
                        + ", theme="
                        + theme.getDisplayName()
                        + "."
        );

        return new ActivationResult(true, state.selectedChestPos, spawned, "");
    }

    static MarkerAllocation allocate(List<BlockPos> markers, RandomSource random) {
        if (markers == null || markers.isEmpty() || random == null) {
            return null;
        }

        int chestIndex = random.nextInt(markers.size());
        BlockPos chestPos = markers.get(chestIndex).immutable();
        List<BlockPos> decoys = new ArrayList<>();

        for (int index = 0; index < markers.size(); index++) {
            if (index == chestIndex) {
                continue;
            }
            BlockPos pos = markers.get(index);
            if (pos != null) {
                decoys.add(pos.immutable());
            }
        }

        return new MarkerAllocation(chestPos, decoys);
    }

    static ResourceLocation junkLootTable(DungeonTheme theme) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "dungeon/quest/gilded_chest_junk/" + theme.getId()
        );
    }

    private static Container placeGildedChest(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, CobblemonBlocks.GILDED_CHEST.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) {
            DebugLog.log("[CobblemonNML] Gilded Chest at " + pos + " did not expose a container inventory.");
            return null;
        }
        return container;
    }

    private static boolean fillQuestChest(
            ServerLevel level,
            DungeonTheme theme,
            BlockPos pos,
            Container container,
            Item requiredItem,
            int requiredCount
    ) {
        int containerSize = container.getContainerSize();
        int maxStack = Math.max(1, requiredItem.getDefaultInstance().getMaxStackSize());
        int requiredSlots = (requiredCount + maxStack - 1) / maxStack;
        if (requiredSlots > containerSize) {
            return false;
        }

        container.clearContent();

        List<Integer> randomizedSlots = randomizedSlots(containerSize, level.getRandom());
        int remaining = requiredCount;
        for (int index = 0; index < requiredSlots; index++) {
            int amount = Math.min(maxStack, remaining);
            int slot = randomizedSlots.get(index);
            container.setItem(slot, new ItemStack(requiredItem, amount));
            remaining -= amount;
        }
        if (remaining > 0) {
            return false;
        }

        List<ItemStack> junk = rollJunkLoot(level, theme, pos);
        int nextFreeSlotIndex = requiredSlots;
        for (ItemStack stack : junk) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (nextFreeSlotIndex >= randomizedSlots.size()) {
                break;
            }
            int slot = randomizedSlots.get(nextFreeSlotIndex++);
            container.setItem(slot, stack.copy());
        }

        container.setChanged();
        return true;
    }

    static List<Integer> randomizedSlots(int containerSize, RandomSource random) {
        if (containerSize <= 0 || random == null) {
            return List.of();
        }

        List<Integer> slots = new ArrayList<>(containerSize);
        for (int slot = 0; slot < containerSize; slot++) {
            slots.add(slot);
        }

        for (int index = slots.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            int value = slots.get(index);
            slots.set(index, slots.get(swapIndex));
            slots.set(swapIndex, value);
        }

        return slots;
    }

    private static List<ItemStack> rollJunkLoot(ServerLevel level, DungeonTheme theme, BlockPos pos) {
        ResourceLocation tableId = junkLootTable(theme);
        ResourceKey<LootTable> tableKey = ResourceKey.create(Registries.LOOT_TABLE, tableId);

        try {
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(tableKey);
            if (lootTable == LootTable.EMPTY) {
                DebugLog.log("[CobblemonNML] Quest Gilded Chest junk table is missing or empty: " + tableId);
                return List.of();
            }

            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .create(LootContextParamSets.CHEST);
            return lootTable.getRandomItems(params);
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to roll quest Gilded Chest junk table " + tableId + ".");
            exception.printStackTrace();
            return List.of();
        }
    }

    private static boolean placeGimmighoulChest(ServerLevel level, BlockPos pos) {
        try {
            boolean placed = level.setBlock(
                    pos,
                    CobblemonBlocks.GIMMIGHOUL_CHEST.defaultBlockState(),
                    Block.UPDATE_ALL
            );

            if (!placed || !level.getBlockState(pos).is(CobblemonBlocks.GIMMIGHOUL_CHEST)) {
                DebugLog.log(
                        "[CobblemonNML] Failed to place Gimmighoul Chest at "
                                + pos
                                + "."
                );
                return false;
            }

            DebugLog.log(
                    "[CobblemonNML] Placed Gimmighoul Chest at "
                            + pos
                            + "."
            );
            return true;
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] Failed to place Gimmighoul Chest at "
                            + pos
                            + "."
            );
            exception.printStackTrace();
            return false;
        }
    }
}
