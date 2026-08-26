package net.epiac9.cobblemonnml.dimension.tier;

import net.epiac9.cobblemonnml.registry.ModItemTags;
import net.minecraft.world.item.ItemStack;

public final class DungeonTierResolver {
    public static DungeonTier getTier(ItemStack stack) {
        /*
         * Check highest tier first.
         * This prevents an item that accidentally appears in multiple tags from being interpreted as a lower tier.
         */
        if (stack.is(ModItemTags.PORTAL_TIER_4)) {
            return DungeonTier.TIER_4;
        }
        if (stack.is(ModItemTags.PORTAL_TIER_3)) {
            return DungeonTier.TIER_3;
        }
        if (stack.is(ModItemTags.PORTAL_TIER_2)) {
            return DungeonTier.TIER_2;
        }
        if (stack.is(ModItemTags.PORTAL_TIER_1)) {
            return DungeonTier.TIER_1;
        }
        return null;
    }
}
