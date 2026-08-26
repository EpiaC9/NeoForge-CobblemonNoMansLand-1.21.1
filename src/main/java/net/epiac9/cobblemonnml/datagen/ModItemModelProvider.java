package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(
            PackOutput output,
            ExistingFileHelper existingFileHelper
    ) {
        super(
                output,
                CobblemonNML.MOD_ID,
                existingFileHelper
        );
    }
    // ITEM MODELS
    @Override
    protected void registerModels() {
        basicItem(ModItems.DUNGEON_ACTIVATOR.get());
        basicItem(ModItems.TIER_1_KEY.get());
        basicItem(ModItems.TIER_2_KEY.get());
        basicItem(ModItems.TIER_3_KEY.get());
        basicItem(ModItems.TIER_4_KEY.get());
        // DUNGEON THEME SELECTORS
        /*
         * basicItem() routes each item to a texture with the same registry
         * path under assets/cobblemonnml/textures/item/.
         *
         * Example:
         * cobblemonnml:bug_theme_key -> item/bug_theme_key.png
         */
        basicItem(ModItems.BUG_THEME_KEY.get());
        basicItem(ModItems.DARK_THEME_KEY.get());
        basicItem(ModItems.DRAGON_THEME_KEY.get());
        basicItem(ModItems.ELECTRIC_THEME_KEY.get());
        basicItem(ModItems.FAIRY_THEME_KEY.get());
        basicItem(ModItems.FIGHTING_THEME_KEY.get());
        basicItem(ModItems.FIRE_THEME_KEY.get());
        basicItem(ModItems.FLYING_THEME_KEY.get());
        basicItem(ModItems.GHOST_THEME_KEY.get());
        basicItem(ModItems.GRASS_THEME_KEY.get());
        basicItem(ModItems.GROUND_THEME_KEY.get());
        basicItem(ModItems.ICE_THEME_KEY.get());
        basicItem(ModItems.NORMAL_THEME_KEY.get());
        basicItem(ModItems.POISON_THEME_KEY.get());
        basicItem(ModItems.PSYCHIC_THEME_KEY.get());
        basicItem(ModItems.ROCK_THEME_KEY.get());
        basicItem(ModItems.STEEL_THEME_KEY.get());
        basicItem(ModItems.WATER_THEME_KEY.get());
        // SPECIAL ROOM KEY
        basicItem(ModItems.SPECIAL_ROOM_KEY.get());
        // TOWN RECRUITMENT
        basicItem(ModItems.TOWN_INVITATION.get());
    }
}
