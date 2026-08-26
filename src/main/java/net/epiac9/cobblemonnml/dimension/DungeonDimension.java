package net.epiac9.cobblemonnml.dimension;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.epiac9.cobblemonnml.dimension.generation.DungeonGenerationQueue;
import net.epiac9.cobblemonnml.dimension.reset.DungeonResetQueue;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DungeonDimension {
    // GENERATION STATE
    private static boolean generated = false;
    private static DungeonTier currentTier = null;
    private static DungeonTheme currentTheme = null;
    // DUNGEON DIMENSION
    public static final ResourceKey<Level> DUNGEON_DIMENSION =
            ResourceKey.create(
                    Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "dungeon_dimension" )
            );
    // GENERATE DUNGEON
    public static boolean generateJigsawDungeon(
            ServerLevel dungeonLevel,
            ServerLevel overworld,
            BlockPos portalCenter,
            DungeonTier tier
    ) {
        // VALIDATE TIER
        if (tier == null) {
            DebugLog.log( "[CobblemonNML] Cannot generate dungeon: " + "resolved Tier is null." );
            return false;
        }
        // RESOLVED THEME
        DungeonTheme theme = DungeonSession.getTheme();
        if (theme == null) {
            DebugLog.log( "[CobblemonNML] Cannot generate dungeon: " + "resolved Theme is null." );
            return false;
        }
        // CURRENT SLOT
        DungeonSlotManager.Slot slot =
                DungeonSlotManager
                        .getCurrentSlot();
        // ALREADY GENERATED
        if (generated) {
            return true;
        }
        // SLOT MUST BE READY
        if (DungeonResetQueue.isResetting(slot)) {
            DebugLog.log( "Cannot generate dungeon: slot " + slot + " is still resetting." );
            return false;
        }
        // DON'T START ANOTHER GENERATION
        if (DungeonGenerationQueue.isGenerating()) {
            DebugLog.log( "Cannot generate dungeon: " + "another dungeon is still generating." );
            return false;
        }
        // STORE CURRENT SETTINGS
        currentTier = tier;
        currentTheme = theme;
        // THEME START POOL
        /*
         * Examples: cobblemonnml:dungeon/<theme>/start
         */
        ResourceLocation poolId = ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, theme.getStartPool() );
        ResourceKey<StructureTemplatePool> poolKey = ResourceKey.create( Registries.TEMPLATE_POOL, poolId );
        // GET TEMPLATE POOL SAFELY
        Holder<StructureTemplatePool> pool;
        try {
            pool = dungeonLevel
                    .registryAccess()
                    .registryOrThrow(Registries.TEMPLATE_POOL)
                            .getHolderOrThrow(poolKey);
        } catch (IllegalStateException exception) {
            DebugLog.log( "========================================" );
            DebugLog.log( "[CobblemonNML] Dungeon generation failed." );
            DebugLog.log( "Missing themed template pool:" );
            DebugLog.log( "  " + poolId );
            DebugLog.log( "Theme: " + theme.getDisplayName() );
            DebugLog.log( "Tier: " + tier.getDisplayName() );
            DebugLog.log( "========================================" );
            currentTier = null;
            currentTheme = null;
            return false;
        }
        // DUNGEON START POSITION
        BlockPos startPos =
                DungeonSlotManager
                        .getCurrentOrigin();
        // DEBUG
        DebugLog.log( "========================================" );
        DebugLog.log( "Preparing themed dungeon generation." );
        DebugLog.log( "Theme: " + theme.getDisplayName() );
        DebugLog.log( "Tier: " + tier.getDisplayName() );
        DebugLog.log( "Start pool: " + poolId );
        DebugLog.log( "Max depth: " + tier.getMaxDepth() );
        DebugLog.log( "Max distance: " + tier.getMaxDistance() );
        DebugLog.log( "========================================" );
        // GENERATE
        generated =
                generateLargeJigsaw(
                        dungeonLevel,
                        overworld,
                        portalCenter,
                        pool,
                        poolId,
                        theme,
                        tier,
                        tier.getMaxDepth(),
                        tier.getMaxDistance(),
                        startPos
                );
        // FAILED
        if (!generated) {
            currentTier = null;
            currentTheme = null;
            DebugLog.log( "[CobblemonNML] Dungeon generation did not start." );
            return false;
        }
        // SUCCESS
        DebugLog.log( "Dungeon generation started successfully." );
        DebugLog.log( "Theme: " + theme.getDisplayName() );
        DebugLog.log( "Tier: " + tier.getDisplayName() );
        DebugLog.log( "Timer: " + tier.getTimerSeconds() + " seconds" );
        return true;
    }
    // CUSTOM LARGE JIGSAW
    private static boolean generateLargeJigsaw(
            ServerLevel dungeonLevel,
            ServerLevel overworld,
            BlockPos portalCenter,
            Holder<StructureTemplatePool> startPool,
            ResourceLocation poolId,
            DungeonTheme theme,
            DungeonTier tier,
            int maxDepth,
            int maxDistanceFromCenter,
            BlockPos startPos
    ) {
        // CHUNK GENERATOR
        ChunkGenerator chunkGenerator =
                dungeonLevel
                        .getChunkSource()
                        .getGenerator();
        // STRUCTURE TEMPLATE MANAGER
        StructureTemplateManager structureTemplateManager =
                dungeonLevel
                        .getStructureManager();
        // GENERATION CONTEXT
        Structure.GenerationContext generationContext =
                new Structure.GenerationContext(
                        dungeonLevel.registryAccess(),
                        chunkGenerator,
                        chunkGenerator.getBiomeSource(),
                        dungeonLevel
                                .getChunkSource()
                                .randomState(),
                        structureTemplateManager,
                        dungeonLevel.getSeed(),
                        new ChunkPos( startPos ),
                        dungeonLevel,
                        biome -> true
                );
        // CALCULATE JIGSAW PIECES
        Optional<Structure.GenerationStub> result;
        try {
            result =
                    JigsawPlacement.addPieces(
                            generationContext,
                            startPool,
                            Optional.of( poolId ),
                            maxDepth,
                            startPos,
                            false,
                            Optional.empty(),
                            maxDistanceFromCenter,
                            PoolAliasLookup.EMPTY,
                            JigsawStructure
                                    .DEFAULT_DIMENSION_PADDING,
                            JigsawStructure
                                    .DEFAULT_LIQUID_SETTINGS
                    );
        } catch (RuntimeException exception) {
            DebugLog.log( "========================================" );
            DebugLog.log( "[CobblemonNML] Jigsaw generation failed." );
            DebugLog.log( "Theme: " + theme.getDisplayName() );
            DebugLog.log( "Tier: " + tier.getDisplayName() );
            DebugLog.log( "Start jigsaw: " + poolId );
            DebugLog.log( "Reason:" );
            DebugLog.log(exception.getClass().getSimpleName() + ": " + exception.getMessage());
            DebugLog.log( "========================================" );
            return false;
        }
        // NO STRUCTURE
        if (result.isEmpty()) {
            DebugLog.log( "========================================" );
            DebugLog.log( "Jigsaw generation returned no structure." );
            DebugLog.log( "Theme: " + theme.getDisplayName() );
            DebugLog.log( "Tier: " + tier.getDisplayName() );
            DebugLog.log( "Requested starting jigsaw: " + poolId );
            DebugLog.log( "========================================" );
            return false;
        }
        // EXTRACT PIECES
        StructurePiecesBuilder piecesBuilder =
                result
                        .get()
                        .getPiecesBuilder();
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        for (StructurePiece piece : piecesBuilder .build() .pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                pieces.add( poolPiece );
            }
        }
        // NO PIECES
        if (pieces.isEmpty()) {
            DebugLog.log( "Jigsaw generation produced no pool pieces." );
            return false;
        }
        // DEBUG
        DebugLog.log( "Queued " + pieces.size() + " dungeon pieces." );
        DebugLog.log( "Generation slot: " + DungeonSlotManager.getCurrentSlot());
        DebugLog.log("Generation theme: " + theme.getDisplayName());
        DebugLog.log("Generation tier: " + tier.getDisplayName());
        // START PROGRESSIVE GENERATION
        DungeonGenerationQueue.start(
                dungeonLevel,
                overworld,
                portalCenter,
                DungeonSlotManager
                        .getCurrentSlot(),
                tier,
                startPos,
                pieces
        );
        return true;
    }
    // CURRENT DUNGEON ORIGIN
    public static BlockPos getCurrentDungeonOrigin() {
        return DungeonSlotManager
                .getCurrentOrigin();
    }
    // CURRENT TIER
    public static DungeonTier getCurrentTier() {
        return currentTier;
    }
    // RESET SESSION
    public static void resetGeneratedState() {
        generated = false;
        currentTier = null;
        currentTheme = null;
        DebugLog.log( "Dungeon generation state reset." );
    }
    // GENERATED?
    public static boolean isGenerated() {
        return generated;
    }
}
