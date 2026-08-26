package net.epiac9.cobblemonnml.dimension.theme;

import net.epiac9.cobblemonnml.registry.ModItemTags;

import net.minecraft.world.item.ItemStack;

public final class DungeonThemeResolver {
    public static DungeonTheme getTheme(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_BUG)) {
            return DungeonTheme.BUG;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_DARK)) {
            return DungeonTheme.DARK;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_DRAGON)) {
            return DungeonTheme.DRAGON;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_ELECTRIC)) {
            return DungeonTheme.ELECTRIC;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_FAIRY)) {
            return DungeonTheme.FAIRY;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_FIGHTING)) {
            return DungeonTheme.FIGHTING;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_FIRE)) {
            return DungeonTheme.FIRE;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_FLYING)) {
            return DungeonTheme.FLYING;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_GHOST)) {
            return DungeonTheme.GHOST;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_GRASS)) {
            return DungeonTheme.GRASS;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_GROUND)) {
            return DungeonTheme.GROUND;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_ICE)) {
            return DungeonTheme.ICE;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_NORMAL)) {
            return DungeonTheme.NORMAL;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_POISON)) {
            return DungeonTheme.POISON;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_PSYCHIC)) {
            return DungeonTheme.PSYCHIC;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_ROCK)) {
            return DungeonTheme.ROCK;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_STEEL)) {
            return DungeonTheme.STEEL;
        }
        if (stack.is(ModItemTags.PORTAL_THEME_WATER)) {
            return DungeonTheme.WATER;
        }
        return null;
    }
}
