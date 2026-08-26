package net.epiac9.cobblemonnml.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Chunk generator used by the CobblemonNML dungeon dimension.
 *
 * <p>The dungeon dimension is intentionally physically solid bedrock, but this
 * generator does not place 65,536 blocks one at a time for a 256-block-tall
 * chunk. Instead, every 16x16x16 chunk section starts with a single-value
 * palette whose only value is bedrock. That keeps a completely solid chunk
 * compact until dungeon structure placement overwrites part of it.</p>
 *
 * <p>This generator also deliberately skips normal terrain surface work,
 * biome decoration, carvers, vanilla structures, and original mob spawning.
 * CobblemonNML places its dungeon jigsaw manually after the destination chunks
 * have been prepared.</p>
 */
public final class DungeonBedrockChunkGenerator extends ChunkGenerator {
    public static final MapCodec<DungeonBedrockChunkGenerator> CODEC =
            RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                                    BiomeSource.CODEC
                                            .fieldOf("biome_source")
                                            .forGetter( ChunkGenerator::getBiomeSource ),
                                    Codec.INT
                                            .fieldOf("min_y")
                                            .forGetter( DungeonBedrockChunkGenerator::getMinY ),
                                    Codec.intRange(16, 4064)
                                            .fieldOf("height")
                                            .forGetter( DungeonBedrockChunkGenerator::getGenDepth )
                    )
                            .apply( instance, DungeonBedrockChunkGenerator::new )
            );
    private static final BlockState BEDROCK_STATE = Blocks.BEDROCK.defaultBlockState();
    private final int minY;
    private final int height;
    public DungeonBedrockChunkGenerator( BiomeSource biomeSource, int minY, int height ) {
        super(biomeSource);
        if ((height & 15) != 0) {
            throw new IllegalArgumentException(
                    "Dungeon bedrock generator height must be a multiple of 16: "
                            + height
            );
        }
        this.minY = minY;
        this.height = height;
    }
    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * Do not allow normal Minecraft structure sets to generate in the dungeon
     * dimension. CobblemonNML's own jigsaw dungeon is placed manually and does
     * not use this structure-state pipeline.
     */
    @Override
    public @NotNull ChunkGeneratorStructureState createState(
            @NotNull HolderLookup<StructureSet> structureSetLookup,
            @NotNull RandomState randomState,
            long seed
    ) {
        return ChunkGeneratorStructureState.createForFlat( randomState, seed, getBiomeSource(), Stream.empty() );
    }

    /**
     * Populate a chunk as solid bedrock by replacing each chunk section with a
     * homogeneous single-value bedrock palette.
     */
    @Override
    public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(
            @NotNull Blender blender,
            @NotNull RandomState randomState,
            @NotNull StructureManager structureManager,
            ChunkAccess chunk
    ) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection bedrockSection = getBedrockSection(sections[sectionIndex]);
            sections[sectionIndex] = bedrockSection;
        }

        // Worldgen consumers expect the world-generation heightmaps to exist.
        // Because every column is solid, priming these are very cheap: each
        // column finds bedrock at the top of the highest section immediately.
        Heightmap.primeHeightmaps(
                chunk,
                EnumSet.of( Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG )
        );
        chunk.setUnsaved( true );
        return CompletableFuture.completedFuture( chunk );
    }
    private static @NotNull LevelChunkSection getBedrockSection(LevelChunkSection sections) {
        PalettedContainer<BlockState> bedrockStates =
                new PalettedContainer<>(
                        Block.BLOCK_STATE_REGISTRY,
                        BEDROCK_STATE,
                        PalettedContainer.Strategy.SECTION_STATES
                );
        LevelChunkSection bedrockSection = new LevelChunkSection( bedrockStates, sections.getBiomes() );

        // Keep LevelChunkSection's non-empty/ticking counters consistent
        // with the new single-value block-state palette.
        bedrockSection.recalcBlockCounts();
        return bedrockSection;
    }

    @Override
    public void buildSurface(
            @NotNull WorldGenRegion region,
            @NotNull StructureManager structureManager,
            @NotNull RandomState randomState,
            @NotNull ChunkAccess chunk
    ) {
    }
    @Override
    public void applyCarvers(
            @NotNull WorldGenRegion region,
            long seed,
            @NotNull RandomState randomState,
            @NotNull BiomeManager biomeManager,
            @NotNull StructureManager structureManager,
            @NotNull ChunkAccess chunk,
            GenerationStep.@NotNull Carving carvingStep
    ) {
    }
    @Override
    public void applyBiomeDecoration(
            @NotNull WorldGenLevel level,
            @NotNull ChunkAccess chunk,
            @NotNull StructureManager structureManager
    ) {
    }
    @Override
    public void spawnOriginalMobs( @NotNull WorldGenRegion region ) {
    }
    @Override
    public int getBaseHeight(
            int x,
            int z,
            Heightmap.@NotNull Types heightmapType,
            @NotNull LevelHeightAccessor heightAccessor,
            @NotNull RandomState randomState
    ) {
        // Heightmap values are the first free Y above the solid column.
        return minY + height;
    }
    @Override
    public @NotNull NoiseColumn getBaseColumn(
            int x,
            int z,
            @NotNull LevelHeightAccessor heightAccessor,
            @NotNull RandomState randomState
    ) {
        BlockState[] column = new BlockState[height];
        Arrays.fill( column, BEDROCK_STATE );
        return new NoiseColumn( minY, column );
    }
    @Override
    public int getGenDepth() {
        return height;
    }
    @Override
    public int getSeaLevel() {
        return minY;
    }
    @Override
    public int getMinY() {
        return minY;
    }
    @Override
    public void addDebugScreenInfo( List<String> debugInfo, @NotNull RandomState randomState, @NotNull BlockPos pos ) {
        debugInfo.add( "CobblemonNML solid-bedrock dungeon generator" );
    }
}
