package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    // ITEM REGISTRY
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems( CobblemonNML.MOD_ID );
    // DUNGEON ACTIVATOR
    public static final DeferredItem<Item> DUNGEON_ACTIVATOR =
            ITEMS.registerSimpleItem( "dungeon_activator", new Item.Properties() );
    // SPECIAL ROOM FORCE
    public static final DeferredItem<Item> SPECIAL_ROOM_KEY =
            ITEMS.registerSimpleItem( "special_room_key", new Item.Properties() );
    // DUNGEON THEME SELECTORS
    public static final DeferredItem<Item> BUG_THEME_KEY = ITEMS.registerSimpleItem( "bug_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> DARK_THEME_KEY = ITEMS.registerSimpleItem( "dark_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> DRAGON_THEME_KEY = ITEMS.registerSimpleItem( "dragon_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> ELECTRIC_THEME_KEY = ITEMS.registerSimpleItem( "electric_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> FAIRY_THEME_KEY = ITEMS.registerSimpleItem( "fairy_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> FIGHTING_THEME_KEY = ITEMS.registerSimpleItem( "fighting_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> FIRE_THEME_KEY = ITEMS.registerSimpleItem( "fire_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> FLYING_THEME_KEY = ITEMS.registerSimpleItem( "flying_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> GHOST_THEME_KEY = ITEMS.registerSimpleItem( "ghost_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> GRASS_THEME_KEY = ITEMS.registerSimpleItem( "grass_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> GROUND_THEME_KEY = ITEMS.registerSimpleItem( "ground_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> ICE_THEME_KEY = ITEMS.registerSimpleItem( "ice_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> NORMAL_THEME_KEY = ITEMS.registerSimpleItem( "normal_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> POISON_THEME_KEY = ITEMS.registerSimpleItem( "poison_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> PSYCHIC_THEME_KEY = ITEMS.registerSimpleItem( "psychic_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> ROCK_THEME_KEY = ITEMS.registerSimpleItem( "rock_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> STEEL_THEME_KEY = ITEMS.registerSimpleItem( "steel_theme_key", new Item.Properties() );
    public static final DeferredItem<Item> WATER_THEME_KEY = ITEMS.registerSimpleItem( "water_theme_key", new Item.Properties() );
    // TOWN RECRUITMENT
    public static final DeferredItem<Item> TOWN_INVITATION =
            ITEMS.registerSimpleItem( "town_invitation", new Item.Properties().stacksTo(16) );
    // DUNGEON TIER SELECTORS
    public static final DeferredItem<Item> TIER_1_KEY = ITEMS.registerSimpleItem( "tier_1_key", new Item.Properties() );
    public static final DeferredItem<Item> TIER_2_KEY = ITEMS.registerSimpleItem( "tier_2_key", new Item.Properties() );
    public static final DeferredItem<Item> TIER_3_KEY = ITEMS.registerSimpleItem( "tier_3_key", new Item.Properties() );
    public static final DeferredItem<Item> TIER_4_KEY = ITEMS.registerSimpleItem( "tier_4_key", new Item.Properties() );
}
