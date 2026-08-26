package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ModDataGenerators {
    // GATHER DATA
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        // CLIENT DATA
        generator.addProvider( event.includeClient(), new ModBlockStateProvider( output, existingFileHelper ) );
        generator.addProvider( event.includeClient(), new ModItemModelProvider( output, existingFileHelper ) );
        generator.addProvider( event.includeClient(), new ModLanguageProvider( output ) );
        // SERVER DATA
        generator.addProvider(
                event.includeServer(),
                new ModItemTagProvider( output, event.getLookupProvider(), existingFileHelper )
        );
        generator.addProvider( event.includeServer(), new ModNormalTrainerProvider( output ) );
    }
}
