package net.epiac9.cobblemonnml.registry;

import com.mojang.serialization.MapCodec;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.worldgen.DungeonBedrockChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModChunkGenerators {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>>
            CHUNK_GENERATORS = DeferredRegister.create( Registries.CHUNK_GENERATOR, CobblemonNML.MOD_ID );
    public static final Supplier<MapCodec<DungeonBedrockChunkGenerator>>
            DUNGEON_BEDROCK = CHUNK_GENERATORS.register( "dungeon_bedrock", () -> DungeonBedrockChunkGenerator.CODEC );
}
