package net.epiac9.cobblemonnml.events.trainer;

import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.ArrayList;
import java.util.List;

public final class DungeonTrainerRewardManager {
    // SHARED TRAINER REWARD LOOT TABLES
    private static final ResourceLocation TIER_1_REWARD_TABLE =
            ResourceLocation.fromNamespaceAndPath(
                    "cobblemonnml",
                    "trainer_rewards/tier1"
            );

    private static final ResourceLocation TIER_2_REWARD_TABLE =
            ResourceLocation.fromNamespaceAndPath(
                    "cobblemonnml",
                    "trainer_rewards/tier2"
            );

    private static final ResourceLocation TIER_3_REWARD_TABLE =
            ResourceLocation.fromNamespaceAndPath(
                    "cobblemonnml",
                    "trainer_rewards/tier3"
            );

    private static final ResourceLocation TIER_4_REWARD_TABLE =
            ResourceLocation.fromNamespaceAndPath(
                    "cobblemonnml",
                    "trainer_rewards/tier4"
            );
    // PUBLIC GRANT
    public static void grantNormalTrainerRewards(
            ServerPlayer player,
            DungeonTier tier
    ) {
        if (player == null || tier == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        ResourceLocation lootTableId = getRewardLootTable(tier);
        int rolls = getRollCount(tier);

        ResourceKey<LootTable> lootTableKey =
                ResourceKey.create(
                        Registries.LOOT_TABLE,
                        lootTableId
                );

        LootTable lootTable;
        try {
            lootTable =
                    level
                            .getServer()
                            .reloadableRegistries()
                            .getLootTable(lootTableKey);
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Failed to resolve normal trainer "
                            + "reward loot table "
                            + lootTableId
                            + " for "
                            + tier.getDisplayName()
                            + ". Rewards will be skipped."
            );
            exception.printStackTrace();
            return;
        }

        /*
         * Minecraft returns LootTable.EMPTY when a requested table does not
         * exist (or was replaced with an empty table during loading).
         * Never substitute a hardcoded fallback reward.
         */
        if (lootTable == LootTable.EMPTY) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Normal trainer reward loot table "
                            + lootTableId
                            + " is missing or empty for "
                            + tier.getDisplayName()
                            + ". Rewards will be skipped."
            );
            return;
        }

        LootParams lootParams;
        try {
            lootParams =
                    new LootParams.Builder(level)
                            .withParameter(
                                    LootContextParams.ORIGIN,
                                    player.position()
                            )
                            .withParameter(
                                    LootContextParams.THIS_ENTITY,
                                    player
                            )
                            .create(LootContextParamSets.GIFT);
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Failed to build normal trainer "
                            + "reward loot context for "
                            + player.getGameProfile().getName()
                            + ". Rewards will be skipped."
            );
            exception.printStackTrace();
            return;
        }

        List<ItemStack> grantedStacks = new ArrayList<>();
        // FIXED TIER ROLL COUNTS
        for (int roll = 0; roll < rolls; roll++) {
            List<ItemStack> generatedStacks;

            try {
                generatedStacks = lootTable.getRandomItems(lootParams);
            } catch (Exception exception) {
                DebugLog.log(
                        "[CobblemonNML] WARNING: Failed to roll normal trainer "
                                + "reward table "
                                + lootTableId
                                + " on roll "
                                + (roll + 1)
                                + "/"
                                + rolls
                                + ". This roll will be skipped."
                );
                exception.printStackTrace();
                continue;
            }

            if (generatedStacks == null || generatedStacks.isEmpty()) {
                continue;
            }

            for (ItemStack generatedStack : generatedStacks) {
                if (generatedStack == null || generatedStack.isEmpty()) {
                    continue;
                }

                ItemStack stackToGive = generatedStack.copy();
                ItemStack logCopy = generatedStack.copy();

                giveOrDrop(player, stackToGive);
                grantedStacks.add(logCopy);
            }
        }
        // SUMMARY
        DebugLog.log(
                "[CobblemonNML] Normal trainer rewards granted from "
                        + lootTableId
                        + ". Player="
                        + player.getGameProfile().getName()
                        + ", tier="
                        + tier.getDisplayName()
                        + ", rolls="
                        + rolls
                        + ", rewards="
                        + describeStacks(grantedStacks)
        );
    }
    // TIER -> TABLE
    private static ResourceLocation getRewardLootTable(DungeonTier tier) {
        return switch (tier) {
            case TIER_1 -> TIER_1_REWARD_TABLE;
            case TIER_2 -> TIER_2_REWARD_TABLE;
            case TIER_3 -> TIER_3_REWARD_TABLE;
            case TIER_4 -> TIER_4_REWARD_TABLE;
        };
    }
    // ROLL COUNT
    private static int getRollCount(DungeonTier tier) {
        return switch (tier) {
            case TIER_1 -> 2;
            case TIER_2 -> 3;
            case TIER_3 -> 4;
            case TIER_4 -> 5;
        };
    }
    // INVENTORY / OVERFLOW
    private static void giveOrDrop(
            ServerPlayer player,
            ItemStack stack
    ) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        player.getInventory().add(stack);

        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }
    // LOG DESCRIPTION
    private static String describeStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "[]";
        }

        List<String> descriptions = new ArrayList<>();

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            descriptions.add(
                    stack.getItem()
                            + " x"
                            + stack.getCount()
            );
        }

        return descriptions.toString();
    }
}
