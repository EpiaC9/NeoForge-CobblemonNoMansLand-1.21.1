package net.epiac9.cobblemonnml.events.quest;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class QuestRewardManager {
    private QuestRewardManager() {
    }

    public static void grantRewards(ServerPlayer player, QuestDefinition definition) {
        if (player == null || definition == null) {
            return;
        }
        for (QuestRewardDefinition reward : definition.rewards()) {
            try {
                switch (reward.type()) {
                    case "item" -> grantItem(player, reward);
                    case "village_unlock" -> applyVillageUnlock(player, reward.unlockId());
                    default -> DebugLog.log("[CobblemonNML] Unsupported quest reward type: " + reward.type());
                }
            } catch (Exception exception) {
                DebugLog.log(
                        "[CobblemonNML] Quest reward failed without aborting other rewards. Type="
                                + reward.type()
                );
                exception.printStackTrace();
            }
        }
    }

    private static void grantItem(ServerPlayer player, QuestRewardDefinition reward) {
        Item item = BuiltInRegistries.ITEM.get(reward.item());
        if (item == null || item == Items.AIR) {
            DebugLog.log("[CobblemonNML] Invalid quest item reward: " + reward.item());
            return;
        }

        int remaining = reward.count();
        int maxStack = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, amount);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= amount;
        }
    }

    private static void applyVillageUnlock(ServerPlayer player, String unlockId) {
        /*
         * The event-driven village placement manager is a separately approved subsystem
         * and has not been implemented in the current project source yet. Keep the quest
         * reward type stable now without inventing a compile-time API that does not exist.
         * When VillageStructurePlacementManager is added, this single method is the only
         * adapter that needs to call its unlock(...) entry point.
         */
        DebugLog.log(
                "[CobblemonNML] Village unlock reward requested for '"
                        + unlockId
                        + "' by "
                        + player.getGameProfile().getName()
                        + ", but the village placement unlock manager is not implemented yet."
        );
    }
}
