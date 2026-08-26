package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    // REGISTRY
    public static final DeferredRegister<CreativeModeTab>
            CREATIVE_MODE_TABS = DeferredRegister.create( Registries.CREATIVE_MODE_TAB, CobblemonNML.MOD_ID );
    // COBBLEMON NML TAB
    public static final Supplier<CreativeModeTab>
            COBBLEMON_NML_TAB =
            CREATIVE_MODE_TABS.register(
                    "cobblemon_nml",
                    () -> CreativeModeTab.builder()
                            // TAB NAME
                            .title( Component.translatable( "itemGroup.cobblemonnml.cobblemon_nml" ) )
                            // TAB ICON
                            .icon(() -> new ItemStack( ModBlocks.ROOM_MARKER.get() ) )
                            // TAB CONTENTS
                            .displayItems(
                                    (parameters, output) -> {
                                        // ENCOUNTER MARKERS
                                        output.accept( ModBlocks.TRAINER_MARKER.get() );
                                        output.accept( ModBlocks.ALPHA_MARKER.get() );
                                        output.accept( ModBlocks.RAID_MARKER.get() );
                                        // TRIAL SPAWNER MARKERS
                                        output.accept( ModBlocks.TRIAL_SPAWNER_MARKER.get() );
                                        output.accept( ModBlocks.ELITE_SPAWNER_MARKER.get() );
                                        output.accept( ModBlocks.BOSS_SPAWNER_MARKER.get() );
                                        // VAULT MARKERS
                                        output.accept( ModBlocks.VAULT_MARKER.get() );
                                        output.accept( ModBlocks.OMINOUS_VAULT_MARKER.get() );
                                        output.accept( ModBlocks.QUEST_ITEM_MARKER.get() );
                                        // STRUCTURAL MARKERS
                                        /*
                                         * Center point for the 3x3 dungeon-side return portal.
                                         */
                                        output.accept( ModBlocks.PORTAL_MARKER.get() );

                                        /*
                                         * Logical encounter-room anchor.
                                         */
                                        output.accept( ModBlocks.ROOM_MARKER.get() );

                                        /*
                                         * Optional structural connection
                                         * point for rare special rooms.
                                         */
                                        output.accept( ModBlocks.SPECIAL_ROOM_MARKER.get() );

                                        /*
                                         * Entrance connection point for
                                         * overworld village-network roads.
                                         */
                                        output.accept( ModBlocks.VILLAGE_ENTRANCE_MARKER.get() );
                                        // CEMETERY MARKERS
                                        output.accept( ModBlocks.GRAVE_MARKER.get() );
                                        // DUNGEON ACTIVATOR
                                        output.accept( ModItems.DUNGEON_ACTIVATOR.get() );
                                        // SPECIAL ROOM FORCE
                                        output.accept( ModItems.SPECIAL_ROOM_KEY.get() );
                                        // DUNGEON THEME SELECTORS
                                        output.accept( ModItems.BUG_THEME_KEY.get() );
                                        output.accept( ModItems.DARK_THEME_KEY.get() );
                                        output.accept( ModItems.DRAGON_THEME_KEY.get() );
                                        output.accept( ModItems.ELECTRIC_THEME_KEY.get() );
                                        output.accept( ModItems.FAIRY_THEME_KEY.get() );
                                        output.accept( ModItems.FIGHTING_THEME_KEY.get() );
                                        output.accept( ModItems.FIRE_THEME_KEY.get() );
                                        output.accept( ModItems.FLYING_THEME_KEY.get() );
                                        output.accept( ModItems.GHOST_THEME_KEY.get() );
                                        output.accept( ModItems.GRASS_THEME_KEY.get() );
                                        output.accept( ModItems.GROUND_THEME_KEY.get() );
                                        output.accept( ModItems.ICE_THEME_KEY.get() );
                                        output.accept( ModItems.NORMAL_THEME_KEY.get() );
                                        output.accept( ModItems.POISON_THEME_KEY.get() );
                                        output.accept( ModItems.PSYCHIC_THEME_KEY.get() );
                                        output.accept( ModItems.ROCK_THEME_KEY.get() );
                                        output.accept( ModItems.STEEL_THEME_KEY.get() );
                                        output.accept( ModItems.WATER_THEME_KEY.get() );
                                        // TOWN RECRUITMENT
                                        output.accept( ModItems.TOWN_INVITATION.get() );
                                        // DUNGEON TIER SELECTORS
                                        output.accept( ModItems.TIER_1_KEY.get() );
                                        output.accept( ModItems.TIER_2_KEY.get() );
                                        output.accept( ModItems.TIER_3_KEY.get() );
                                        output.accept( ModItems.TIER_4_KEY.get() );
                                    }
                            )
                            .build()
            );
}
