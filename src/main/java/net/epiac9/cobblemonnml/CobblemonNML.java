package net.epiac9.cobblemonnml;

import net.epiac9.cobblemonnml.dimension.gameplay.DungeonBattleLifeTransfer;
import net.epiac9.cobblemonnml.registry.ModAttachments;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.registry.ModChunkGenerators;
import net.epiac9.cobblemonnml.registry.ModCreativeTabs;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(CobblemonNML.MOD_ID)
public class CobblemonNML {
    public static final String MOD_ID = "cobblemonnml";
    public CobblemonNML(IEventBus modEventBus, ModContainer modContainer) {
        // Blocks
        ModBlocks.BLOCKS.register( modEventBus );

        // Block Items
        ModBlocks.ITEMS.register( modEventBus );

        // Items
        ModItems.ITEMS.register( modEventBus );

        // Creative tabs
        ModCreativeTabs.CREATIVE_MODE_TABS.register( modEventBus );

        // Chunk generators
        ModChunkGenerators.CHUNK_GENERATORS.register( modEventBus );

        // Attachments
        ModAttachments.ATTACHMENT_TYPES.register( modEventBus );

        // Config
        modContainer.registerConfig( ModConfig.Type.SERVER, Config.SPEC, "cobblemonnml-dungeon.toml" );

        // Damage Event
        DungeonBattleLifeTransfer.register();
    }
}
