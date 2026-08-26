package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    // ACTIVATION ITEM
    public static final TagKey<Item> PORTAL_ACTIVATION = create( "portal_activation" );
    // SPECIAL ROOM FORCE ITEM
    public static final TagKey<Item> PORTAL_SPECIAL_ROOM = create( "portal_special_room" );
    // TIER SELECTORS
    public static final TagKey<Item> PORTAL_TIER_1 = create( "portal_tier_1" );
    public static final TagKey<Item> PORTAL_TIER_2 = create( "portal_tier_2" );
    public static final TagKey<Item> PORTAL_TIER_3 = create( "portal_tier_3" );
    public static final TagKey<Item> PORTAL_TIER_4 = create( "portal_tier_4" );
    // THEME SELECTORS
    public static final TagKey<Item> PORTAL_THEME_BUG = create("portal_theme_bug");
    public static final TagKey<Item> PORTAL_THEME_DARK = create("portal_theme_dark");
    public static final TagKey<Item> PORTAL_THEME_DRAGON = create("portal_theme_dragon");
    public static final TagKey<Item> PORTAL_THEME_ELECTRIC = create("portal_theme_electric");
    public static final TagKey<Item> PORTAL_THEME_FAIRY = create("portal_theme_fairy");
    public static final TagKey<Item> PORTAL_THEME_FIGHTING = create("portal_theme_fighting");
    public static final TagKey<Item> PORTAL_THEME_FIRE = create("portal_theme_fire");
    public static final TagKey<Item> PORTAL_THEME_FLYING = create("portal_theme_flying");
    public static final TagKey<Item> PORTAL_THEME_GHOST = create("portal_theme_ghost");
    public static final TagKey<Item> PORTAL_THEME_GRASS = create("portal_theme_grass");
    public static final TagKey<Item> PORTAL_THEME_GROUND = create("portal_theme_ground");
    public static final TagKey<Item> PORTAL_THEME_ICE = create("portal_theme_ice");
    public static final TagKey<Item> PORTAL_THEME_NORMAL = create("portal_theme_normal");
    public static final TagKey<Item> PORTAL_THEME_POISON = create("portal_theme_poison");
    public static final TagKey<Item> PORTAL_THEME_PSYCHIC = create("portal_theme_psychic");
    public static final TagKey<Item> PORTAL_THEME_ROCK = create("portal_theme_rock");
    public static final TagKey<Item> PORTAL_THEME_STEEL = create("portal_theme_steel");
    public static final TagKey<Item> PORTAL_THEME_WATER = create("portal_theme_water");
    // CREATE TAG
    private static TagKey<Item> create(String name) {
        return TagKey.create( Registries.ITEM, ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, name ) );
    }
}
