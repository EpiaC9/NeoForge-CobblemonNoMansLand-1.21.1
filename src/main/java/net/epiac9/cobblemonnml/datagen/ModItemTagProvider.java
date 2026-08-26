package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModItemTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends TagsProvider<Item> {
    public ModItemTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper
    ) {
        super( output, Registries.ITEM, lookupProvider, CobblemonNML.MOD_ID, existingFileHelper );
    }
    // TAGS
    @Override
    protected void addTags(HolderLookup.@NotNull Provider lookupProvider) {
        // ACTIVATOR
        tag(ModItemTags.PORTAL_ACTIVATION).add( item( CobblemonNML.MOD_ID, "dungeon_activator" ) );
        // SPECIAL ROOM FORCE
        tag(ModItemTags.PORTAL_SPECIAL_ROOM).add( item( CobblemonNML.MOD_ID, "special_room_key" ) );
        // TIERS
        tag(ModItemTags.PORTAL_TIER_1).add( item( CobblemonNML.MOD_ID, "tier_1_key" ) );
        tag(ModItemTags.PORTAL_TIER_2).add( item( CobblemonNML.MOD_ID, "tier_2_key" ) );
        tag(ModItemTags.PORTAL_TIER_3).add( item( CobblemonNML.MOD_ID, "tier_3_key" ) );
        tag(ModItemTags.PORTAL_TIER_4).add( item( CobblemonNML.MOD_ID, "tier_4_key" ) );
        // DUNGEON THEME SELECTORS
        tag(ModItemTags.PORTAL_THEME_BUG).add( item( CobblemonNML.MOD_ID, "bug_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_DARK).add( item( CobblemonNML.MOD_ID, "dark_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_DRAGON).add( item( CobblemonNML.MOD_ID, "dragon_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_ELECTRIC).add( item( CobblemonNML.MOD_ID, "electric_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_FAIRY).add( item( CobblemonNML.MOD_ID, "fairy_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_FIGHTING).add( item( CobblemonNML.MOD_ID, "fighting_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_FIRE).add( item( CobblemonNML.MOD_ID, "fire_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_FLYING).add( item( CobblemonNML.MOD_ID, "flying_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_GHOST).add( item( CobblemonNML.MOD_ID, "ghost_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_GRASS).add( item( CobblemonNML.MOD_ID, "grass_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_GROUND).add( item( CobblemonNML.MOD_ID, "ground_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_ICE).add( item( CobblemonNML.MOD_ID, "ice_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_NORMAL).add( item( CobblemonNML.MOD_ID, "normal_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_POISON).add( item( CobblemonNML.MOD_ID, "poison_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_PSYCHIC).add( item( CobblemonNML.MOD_ID, "psychic_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_ROCK).add( item( CobblemonNML.MOD_ID, "rock_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_STEEL).add( item( CobblemonNML.MOD_ID, "steel_theme_key" ) );
        tag(ModItemTags.PORTAL_THEME_WATER).add( item( CobblemonNML.MOD_ID, "water_theme_key" ) );
    }
    // ITEM RESOURCE KEY
    private static ResourceKey<Item> item(String namespace, String path) {
        return ResourceKey.create( Registries.ITEM, ResourceLocation.fromNamespaceAndPath( namespace, path ) );
    }
}
