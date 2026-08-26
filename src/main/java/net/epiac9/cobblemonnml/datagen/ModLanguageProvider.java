package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, CobblemonNML.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // ITEMS
        add(ModItems.DUNGEON_ACTIVATOR.get(), "Dungeon Activator");
        add(ModItems.SPECIAL_ROOM_KEY.get(), "Special Room Key");
        add(ModItems.TOWN_INVITATION.get(), "Town Invitation");
        add(ModItems.BUG_THEME_KEY.get(), "Bug Theme Key");
        add(ModItems.DARK_THEME_KEY.get(), "Dark Theme Key");
        add(ModItems.DRAGON_THEME_KEY.get(), "Dragon Theme Key");
        add(ModItems.ELECTRIC_THEME_KEY.get(), "Electric Theme Key");
        add(ModItems.FAIRY_THEME_KEY.get(), "Fairy Theme Key");
        add(ModItems.FIGHTING_THEME_KEY.get(), "Fighting Theme Key");
        add(ModItems.FIRE_THEME_KEY.get(), "Fire Theme Key");
        add(ModItems.FLYING_THEME_KEY.get(), "Flying Theme Key");
        add(ModItems.GHOST_THEME_KEY.get(), "Ghost Theme Key");
        add(ModItems.GRASS_THEME_KEY.get(), "Grass Theme Key");
        add(ModItems.GROUND_THEME_KEY.get(), "Ground Theme Key");
        add(ModItems.ICE_THEME_KEY.get(), "Ice Theme Key");
        add(ModItems.NORMAL_THEME_KEY.get(), "Normal Theme Key");
        add(ModItems.POISON_THEME_KEY.get(), "Poison Theme Key");
        add(ModItems.PSYCHIC_THEME_KEY.get(), "Psychic Theme Key");
        add(ModItems.ROCK_THEME_KEY.get(), "Rock Theme Key");
        add(ModItems.STEEL_THEME_KEY.get(), "Steel Theme Key");
        add(ModItems.WATER_THEME_KEY.get(), "Water Theme Key");
        add(ModItems.TIER_1_KEY.get(), "Tier 1 Dungeon Key");
        add(ModItems.TIER_2_KEY.get(), "Tier 2 Dungeon Key");
        add(ModItems.TIER_3_KEY.get(), "Tier 3 Dungeon Key");
        add(ModItems.TIER_4_KEY.get(), "Tier 4 Dungeon Key");
        // PORTAL BLOCKS
        add(ModBlocks.DUNGEON_PORTAL_CORE.get(), "Dungeon Portal Core");
        add(ModBlocks.DUNGEON_PORTAL.get(), "Dungeon Portal");
        // MARKERS
        add(ModBlocks.TRAINER_MARKER.get(), "Trainer Marker");
        add(ModBlocks.ALPHA_MARKER.get(), "Alpha Marker");
        add(ModBlocks.RAID_MARKER.get(), "Raid Marker");
        add(ModBlocks.TRIAL_SPAWNER_MARKER.get(), "Trial Spawner Marker");
        add(ModBlocks.ELITE_SPAWNER_MARKER.get(), "Elite Spawner Marker");
        add(ModBlocks.BOSS_SPAWNER_MARKER.get(), "Boss Spawner Marker");
        add(ModBlocks.VAULT_MARKER.get(), "Vault Marker");
        add(ModBlocks.OMINOUS_VAULT_MARKER.get(), "Ominous Vault Marker");
        add(ModBlocks.QUEST_ITEM_MARKER.get(), "Quest Item Marker");
        add(ModBlocks.PORTAL_MARKER.get(), "Dungeon Return Portal Marker");
        add(ModBlocks.ROOM_MARKER.get(), "Room Marker");
        add(ModBlocks.SPECIAL_ROOM_MARKER.get(), "Special Room Marker");
        add(ModBlocks.GRAVE_MARKER.get(), "Cemetery Grave Marker");
        add(ModBlocks.GRAVE_PLOT.get(), "Empty Grave Plot");
        // DAMAGE DEATH MESSAGES
        add("death.attack.life_transfer", "%1$s gave their life for their Pokemon");
        add("death.attack.life_transfer.player", "%1$s gave their life for their Pokemon");
    }
}
