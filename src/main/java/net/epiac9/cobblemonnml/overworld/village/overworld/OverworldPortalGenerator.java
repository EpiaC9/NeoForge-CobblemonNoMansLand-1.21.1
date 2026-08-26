package net.epiac9.cobblemonnml.overworld.village.overworld;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.Config;
import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeteryManager;
import net.epiac9.cobblemonnml.overworld.village.cemetery.CemeterySavedData;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkManager;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.Optional;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class OverworldPortalGenerator {
    private static final ResourceLocation PORTAL_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "portal/overworld_portal" );
    private static final ResourceLocation CEMETERY_STRUCTURE =
            ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "town/cemetery" );
    private static final int SEARCH_RADIUS_CHUNKS = 8;
    private static final int CEMETERY_SEARCH_RADIUS_CHUNKS = 1;
    private static final int CEMETERY_MAX_HEIGHT_DIFFERENCE = 2;
    private static final int CEMETERY_MIN_EDGE_GAP = 20;
    private static final int CEMETERY_MAX_EDGE_GAP = 30;
    // SERVER STARTED
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        OverworldPortalSavedData data = OverworldPortalSavedData.get( overworld );
        // ALREADY GENERATED
        if (data.isGenerated()) {
            DebugLog.log( "[CobblemonNML] Overworld dungeon portal already exists at " + data.getPortalPos() );
            return;
        }
        // GENERATE
        generatePortal( overworld, data );
    }
    // GENERATE PORTAL
    private static void generatePortal( ServerLevel level, OverworldPortalSavedData data ) {
        Optional<StructureTemplate> templateOptional =
                level
                        .getStructureManager()
                        .get( PORTAL_STRUCTURE );
        if (templateOptional.isEmpty()) {
            DebugLog.log( "[CobblemonNML] ERROR: Could not find Overworld portal structure: " + PORTAL_STRUCTURE );
            return;
        }
        StructureTemplate template = templateOptional.get();
        BlockPos spawn = level.getSharedSpawnPos();
        int minimumDistance =
                Config
                        .OVERWORLD_PORTAL_MIN_DISTANCE
                        .get();
        int maximumDistance =
                Config
                        .OVERWORLD_PORTAL_MAX_DISTANCE
                        .get();
        if (maximumDistance < minimumDistance) {
            int swap = minimumDistance;
            minimumDistance = maximumDistance;
            maximumDistance = swap;
        }
        RandomSource random = RandomSource.create( level.getSeed() ^ 0x434F42424C454E4DL );
        // CHOOSE ONE TARGET REGION
        double angle =
                random.nextDouble()
                        * Math.PI
                        * 2.0D;
        int distance;
        if (maximumDistance == minimumDistance) {
            distance = minimumDistance;
        } else {
            distance = minimumDistance + random.nextInt( maximumDistance - minimumDistance + 1 );
        }
        int targetX = spawn.getX() + (int) Math.round( Math.cos(angle) * distance );
        int targetZ = spawn.getZ() + (int) Math.round( Math.sin(angle) * distance );
        int centerChunkX = targetX >> 4;
        int centerChunkZ = targetZ >> 4;
        DebugLog.log( "[CobblemonNML] Searching for Overworld portal location near " + targetX + ", " + targetZ );
        // SEARCH NEARBY CHUNKS
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    /*
                     * Only process the current outer ring.
                     */
                    if (radius > 0 && Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    int chunkX = centerChunkX + offsetX;
                    int chunkZ = centerChunkZ + offsetZ;
                    BlockPos candidate = findCandidateInChunk( level, template, chunkX, chunkZ );
                    if (candidate == null) {
                        continue;
                    }
                    boolean placed = placePortal( level, template, candidate, random );
                    if (!placed) {
                        continue;
                    }

                    BoundingBox generatedPortalBounds =
                            createStructureBounds(
                                    candidate,
                                    template
                            );

                    VillageNetworkManager.registerPlacedStructure(
                            level,
                            PORTAL_STRUCTURE,
                            candidate,
                            generatedPortalBounds
                    );

                    data.setPortal( candidate );
                    // GIVE PORTAL MAP TO ONLINE PLAYERS
                    for (ServerPlayer player : level.getServer() .getPlayerList() .getPlayers()) {
                        OverworldPortalMapManager.givePortalMapIfNeeded( player );
                    }
                    DebugLog.log( "[CobblemonNML] Generated the one-time Overworld dungeon portal at " + candidate );
                    return;
                }
            }
        }
        DebugLog.log(
                "[CobblemonNML] WARNING: Could not find a suitable location "
                        + "for the Overworld dungeon portal within "
                        + SEARCH_RADIUS_CHUNKS
                        + " chunks of the selected target region."
        );
    }
    // GENERATE CEMETERY
    // CEMETERY RECRUITMENT API
    public static CemeteryGenerationResult ensureCemeteryForRecruitment(ServerLevel level) {
        if (level == null) {
            return CemeteryGenerationResult.failure();
        }

        CemeterySavedData cemeteryData = CemeterySavedData.get(level.getServer());
        BlockPos existingOrigin = cemeteryData.getCemeteryOrigin();
        if (existingOrigin != null) {
            DebugLog.log("[CobblemonNML] Reusing existing cemetery at " + existingOrigin + " for town recruitment.");
            return new CemeteryGenerationResult(true, existingOrigin, null);
        }

        OverworldPortalSavedData portalData = OverworldPortalSavedData.get(level);
        BlockPos portalPos = portalData.getPortalPos();
        if (!portalData.isGenerated() || portalPos == null) {
            DebugLog.log("[CobblemonNML] Town recruitment cannot place the cemetery because the Overworld dungeon portal does not exist yet.");
            return CemeteryGenerationResult.failure();
        }

        return generateCemeteryForRecruitmentAtPortal(level, portalPos);
    }

    private static CemeteryGenerationResult generateCemeteryForRecruitmentAtPortal(
            ServerLevel level,
            BlockPos portalPos
    ) {
        CemeterySavedData cemeteryData = CemeterySavedData.get(level.getServer());
        BlockPos existingOrigin = cemeteryData.getCemeteryOrigin();
        if (existingOrigin != null) {
            return new CemeteryGenerationResult(true, existingOrigin, null);
        }

        Optional<StructureTemplate> cemeteryTemplateOptional =
                level.getStructureManager().get(CEMETERY_STRUCTURE);
        if (cemeteryTemplateOptional.isEmpty()) {
            DebugLog.log("[CobblemonNML] ERROR: Could not find cemetery structure: " + CEMETERY_STRUCTURE);
            return CemeteryGenerationResult.failure();
        }

        Optional<StructureTemplate> portalTemplateOptional =
                level.getStructureManager().get(PORTAL_STRUCTURE);
        if (portalTemplateOptional.isEmpty()) {
            DebugLog.log("[CobblemonNML] ERROR: Could not find Overworld portal structure while generating cemetery.");
            return CemeteryGenerationResult.failure();
        }

        StructureTemplate cemeteryTemplate = cemeteryTemplateOptional.get();
        StructureTemplate portalTemplate = portalTemplateOptional.get();
        BoundingBox portalBounds = createStructureBounds(portalPos, portalTemplate);
        int portalChunkX = portalPos.getX() >> 4;
        int portalChunkZ = portalPos.getZ() >> 4;
        RandomSource random = RandomSource.create(
                level.getSeed() ^ portalPos.asLong() ^ 0x43454D4554455259L
        );

        DebugLog.log("[CobblemonNML] Searching for cemetery location near Overworld portal at " + portalPos);

        CemeteryPlacement bestPlacement = findBestCemeteryPlacement(
                level,
                cemeteryTemplate,
                portalPos,
                portalBounds,
                portalChunkX,
                portalChunkZ,
                false
        );

        if (bestPlacement == null) {
            DebugLog.log(
                    "[CobblemonNML] No dry cemetery location found inside the 3x3 chunk neighborhood. "
                            + "Trying water-surface fallback."
            );
            bestPlacement = findBestCemeteryPlacement(
                    level,
                    cemeteryTemplate,
                    portalPos,
                    portalBounds,
                    portalChunkX,
                    portalChunkZ,
                    true
            );
        }

        if (bestPlacement == null) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Could not find a suitable dry-land or water-surface "
                            + "cemetery location with a 20-30 block edge gap inside the 3x3 chunk neighborhood around the Overworld portal."
            );
            return CemeteryGenerationResult.failure();
        }

        boolean placed = placeCemetery(level, cemeteryTemplate, bestPlacement, random);
        if (!placed) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Failed to place cemetery at selected candidate "
                            + bestPlacement.origin()
            );
            return CemeteryGenerationResult.failure();
        }

        int gravePlotCount = CemeteryManager.processGeneratedCemetery(
                level,
                bestPlacement.origin(),
                bestPlacement.bounds()
        );
        if (gravePlotCount <= 0) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Cemetery structure was placed but no grave markers were processed at "
                            + bestPlacement.origin()
            );
            return CemeteryGenerationResult.failure();
        }

        VillageNetworkManager.registerPlacedStructure(
                level,
                CEMETERY_STRUCTURE,
                bestPlacement.origin(),
                bestPlacement.bounds()
        );

        DebugLog.log(
                "[CobblemonNML] Generated cemetery at "
                        + bestPlacement.origin()
                        + " facing "
                        + bestPlacement.entranceFacing().getName()
                        + " toward portal, rotation="
                        + bestPlacement.rotation()
                        + ", edgeGap="
                        + String.format("%.1f", Math.sqrt(bestPlacement.edgeGapSquared()))
                        + ", entranceDistanceSq="
                        + bestPlacement.entranceDistanceSquared()
                        + ", gravePlots="
                        + gravePlotCount
                        + "."
        );

        return new CemeteryGenerationResult(
                true,
                bestPlacement.origin(),
                bestPlacement.bounds()
        );
    }
    // FIND BEST CEMETERY PLACEMENT
    private static CemeteryPlacement findBestCemeteryPlacement(
            ServerLevel level,
            StructureTemplate template,
            BlockPos portalPos,
            BoundingBox portalBounds,
            int portalChunkX,
            int portalChunkZ,
            boolean waterOnly
    ) {
        CemeteryPlacement bestPlacement = null;

        /*
         * Probe every two blocks inside each chunk. This keeps the
         * search bounded to the fixed 3x3 area while allowing the
         * cemetery to sit substantially closer than chunk-center-only
         * placement.
         */
        int[] localOffsets = {
                0, 2, 4, 6, 8, 10, 12, 14
        };

        for (int offsetX = -CEMETERY_SEARCH_RADIUS_CHUNKS;
             offsetX <= CEMETERY_SEARCH_RADIUS_CHUNKS;
             offsetX++) {
            for (int offsetZ = -CEMETERY_SEARCH_RADIUS_CHUNKS;
                 offsetZ <= CEMETERY_SEARCH_RADIUS_CHUNKS;
                 offsetZ++) {

                int chunkX =
                        portalChunkX + offsetX;

                int chunkZ =
                        portalChunkZ + offsetZ;

                level.getChunk(
                        chunkX,
                        chunkZ
                );

                for (int localX : localOffsets) {
                    for (int localZ : localOffsets) {
                        int x =
                                (chunkX << 4) + localX;

                        int z =
                                (chunkZ << 4) + localZ;

                        int surfaceY =
                                waterOnly
                                        ? findWaterSurfaceY(
                                        level,
                                        x,
                                        z
                                )
                                        : findGroundY(
                                        level,
                                        x,
                                        z
                                );

                        if (surfaceY == Integer.MIN_VALUE) {
                            continue;
                        }

                        BlockPos origin =
                                new BlockPos(
                                        x,
                                        surfaceY + 1,
                                        z
                                );

                        for (Rotation rotation : Rotation.values()) {
                            StructurePlaceSettings settings =
                                    createCemeterySettings(
                                            rotation
                                    );

                            BoundingBox bounds =
                                    template.getBoundingBox(
                                            settings,
                                            origin
                                    );

                            if (bounds.intersects(portalBounds)) {
                                continue;
                            }

                            long edgeGapSquared =
                                    horizontalBoundsGapSquared(
                                            portalBounds,
                                            bounds
                                    );

                            long minEdgeGapSquared =
                                    (long) CEMETERY_MIN_EDGE_GAP
                                            * CEMETERY_MIN_EDGE_GAP;

                            long maxEdgeGapSquared =
                                    (long) CEMETERY_MAX_EDGE_GAP
                                            * CEMETERY_MAX_EDGE_GAP;

                            /*
                             * Keep enough open space for the road to
                             * matter visually. Distance is measured
                             * between the OUTER EDGES of the two
                             * structure bounding boxes, not between
                             * their origins.
                             */
                            if (edgeGapSquared < minEdgeGapSquared
                                    || edgeGapSquared > maxEdgeGapSquared) {
                                continue;
                            }

                            boolean suitable =
                                    waterOnly
                                            ? isCemeteryWaterAreaSuitable(
                                            level,
                                            bounds,
                                            surfaceY
                                    )
                                            : isCemeteryAreaSuitable(
                                            level,
                                            bounds,
                                            surfaceY
                                    );

                            if (!suitable) {
                                continue;
                            }

                            CemeteryEntranceInfo entrance =
                                    getCemeteryEntranceInfo(
                                            template,
                                            origin,
                                            settings
                                    );

                            if (entrance == null) {
                                continue;
                            }

                            long entranceDistanceSquared =
                                    horizontalDistanceSquared(
                                            portalPos,
                                            entrance.pos()
                                    );

                            boolean entranceFacesPortal =
                                    entranceFacesBounds(
                                            entrance,
                                            portalBounds
                                    );

                            CemeteryPlacement candidate =
                                    new CemeteryPlacement(
                                            origin,
                                            rotation,
                                            bounds,
                                            entrance.pos(),
                                            entrance.facing(),
                                            edgeGapSquared,
                                            entranceDistanceSquared,
                                            entranceFacesPortal
                                    );

                            if (isBetterCemeteryPlacement(
                                    candidate,
                                    bestPlacement
                            )) {
                                bestPlacement =
                                        candidate;
                            }
                        }
                    }
                }
            }
        }

        return bestPlacement;
    }
    // CEMETERY PLACEMENT SCORING
    private static boolean isBetterCemeteryPlacement(
            CemeteryPlacement candidate,
            CemeteryPlacement currentBest
    ) {
        if (currentBest == null) {
            return true;
        }

        /*
         * First preference: the structure entrance should point
         * toward the portal. This keeps the cemetery visually
         * oriented toward the road network.
         */
        if (candidate.entranceFacesPortal()
                != currentBest.entranceFacesPortal()) {
            return candidate.entranceFacesPortal();
        }

        /*
         * Once both candidates satisfy the 20-30 block edge-gap
         * requirement, prefer the shorter entrance-to-portal
         * connection so the road does not meander unnecessarily.
         */
        if (candidate.entranceDistanceSquared()
                != currentBest.entranceDistanceSquared()) {
            return candidate.entranceDistanceSquared()
                    < currentBest.entranceDistanceSquared();
        }

        /*
         * Stable tie-breaker: prefer the smaller actual edge gap.
         */
        return candidate.edgeGapSquared()
                < currentBest.edgeGapSquared();
    }
    // ENTRANCE FACES PORTAL
    private static boolean entranceFacesBounds(
            CemeteryEntranceInfo entrance,
            BoundingBox targetBounds
    ) {
        int targetX =
                clamp(
                        entrance.pos().getX(),
                        targetBounds.minX(),
                        targetBounds.maxX()
                );

        int targetZ =
                clamp(
                        entrance.pos().getZ(),
                        targetBounds.minZ(),
                        targetBounds.maxZ()
                );

        int deltaX =
                targetX - entrance.pos().getX();

        int deltaZ =
                targetZ - entrance.pos().getZ();

        int facingX =
                entrance.facing().getStepX();

        int facingZ =
                entrance.facing().getStepZ();

        return facingX * deltaX
                + facingZ * deltaZ
                > 0;
    }
    // HORIZONTAL STRUCTURE EDGE GAP
    private static long horizontalBoundsGapSquared(
            BoundingBox first,
            BoundingBox second
    ) {
        long gapX =
                axisGap(
                        first.minX(),
                        first.maxX(),
                        second.minX(),
                        second.maxX()
                );

        long gapZ =
                axisGap(
                        first.minZ(),
                        first.maxZ(),
                        second.minZ(),
                        second.maxZ()
                );

        return gapX * gapX
                + gapZ * gapZ;
    }

    private static long axisGap(
            int firstMin,
            int firstMax,
            int secondMin,
            int secondMax
    ) {
        if (firstMax < secondMin) {
            return Math.max(
                    0L,
                    (long) secondMin - firstMax - 1L
            );
        }

        if (secondMax < firstMin) {
            return Math.max(
                    0L,
                    (long) firstMin - secondMax - 1L
            );
        }

        return 0L;
    }

    private static int clamp(
            int value,
            int min,
            int max
    ) {
        return Math.max(
                min,
                Math.min(
                        max,
                        value
                )
        );
    }
    // CEMETERY ENTRANCE INFO
    private static CemeteryEntranceInfo getCemeteryEntranceInfo(
            StructureTemplate template,
            BlockPos origin,
            StructurePlaceSettings settings
    ) {
        var markerBlocks =
                template.filterBlocks(
                        origin,
                        settings,
                        ModBlocks.VILLAGE_ENTRANCE_MARKER.get(),
                        true
                );

        if (markerBlocks.isEmpty()) {
            return null;
        }

        StructureTemplate.StructureBlockInfo marker =
                markerBlocks.getFirst();

        BlockState markerState =
                marker.state();

        if (!markerState.hasProperty(
                net.epiac9.cobblemonnml.block.VillageEntranceMarkerBlock.FACING
        )) {
            return null;
        }

        return new CemeteryEntranceInfo(
                marker.pos().immutable(),
                markerState.getValue(
                        net.epiac9.cobblemonnml.block.VillageEntranceMarkerBlock.FACING
                )
        );
    }
    // FIND CANDIDATE
    private static BlockPos findCandidateInChunk(
            ServerLevel level,
            StructureTemplate template,
            int chunkX,
            int chunkZ
    ) {
        // GENERATE TARGET CHUNK
        level.getChunk( chunkX, chunkZ );
        /*
         * Use the center of the chunk rather than an arbitrary block position. This gives the structure more room
         * before crossing into neighboring chunks.
         */
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int groundY = findPortalGroundY( level, x, z );
        if (groundY == Integer.MIN_VALUE) {
            return null;
        }
        // PLACE DIRECTLY ABOVE GROUND
        int structureY = groundY + 1;
        if (!isAreaSuitable( level, template, x, structureY, z )) {
            return null;
        }
        return new BlockPos( x, structureY, z );
    }
    // FIND CEMETERY CANDIDATE
    // FIND CEMETERY WATER CANDIDATE
    // PLACE STRUCTURE
    private static boolean placePortal(
            ServerLevel level,
            StructureTemplate template,
            BlockPos position,
            RandomSource random
    ) {
        StructurePlaceSettings settings =
                new StructurePlaceSettings().setMirror( Mirror.NONE )
                        .setRotation( Rotation.NONE )
                        .setIgnoreEntities( false );
        clearPortalArea( level, template, position );
        return template.placeInWorld( level, position, position, settings, random, 2 );
    }
    // PLACE CEMETERY
    private static boolean placeCemetery(
            ServerLevel level,
            StructureTemplate template,
            CemeteryPlacement placement,
            RandomSource random
    ) {
        StructurePlaceSettings settings =
                createCemeterySettings(
                        placement.rotation()
                );

        clearCemeteryArea(
                level,
                placement.bounds()
        );

        return template.placeInWorld(
                level,
                placement.origin(),
                placement.origin(),
                settings,
                random,
                2
        );
    }
    // CEMETERY SETTINGS
    private static StructurePlaceSettings createCemeterySettings(
            Rotation rotation
    ) {
        return new StructurePlaceSettings()
                .setMirror( Mirror.NONE )
                .setRotation( rotation )
                .setIgnoreEntities( false );
    }
    // CHECK STRUCTURE AREA
    private static boolean isAreaSuitable(
            ServerLevel level,
            StructureTemplate template,
            int originX,
            int originY,
            int originZ
    ) {
        Vec3i size = template.getSize();

        /*
         * We only care that the structure has actual terrain underneath its footprint.
         * Trees, leaves, vines, grass and other foliage are allowed because clearPortalArea(...) removes them before placement.
         */
        for (int x = 0; x < size.getX(); x++) {
            for (int z = 0; z < size.getZ(); z++) {
                BlockPos groundPos = new BlockPos( originX + x, originY - 1, originZ + z );
                BlockState groundState = level.getBlockState( groundPos );
                if (groundState.isAir()) {
                    return false;
                }
            }
        }
        return true;
    }
    // CHECK CEMETERY AREA
    private static boolean isCemeteryAreaSuitable(
            ServerLevel level,
            BoundingBox bounds,
            int centerGroundY
    ) {
        for (int worldX = bounds.minX();
             worldX <= bounds.maxX();
             worldX++) {
            for (int worldZ = bounds.minZ();
                 worldZ <= bounds.maxZ();
                 worldZ++) {

                int groundY =
                        findGroundY(
                                level,
                                worldX,
                                worldZ
                        );

                if (groundY == Integer.MIN_VALUE) {
                    return false;
                }

                if (Math.abs(
                        groundY - centerGroundY
                ) > CEMETERY_MAX_HEIGHT_DIFFERENCE) {
                    return false;
                }

                BlockPos groundPos =
                        new BlockPos(
                                worldX,
                                groundY,
                                worldZ
                        );

                BlockState groundState =
                        level.getBlockState(
                                groundPos
                        );

                if (groundState.isAir()) {
                    return false;
                }

                if (!level
                        .getFluidState(groundPos)
                        .isEmpty()) {
                    return false;
                }
            }
        }

        return true;
    }
    // CHECK CEMETERY WATER AREA
    private static boolean isCemeteryWaterAreaSuitable(
            ServerLevel level,
            BoundingBox bounds,
            int centerWaterY
    ) {
        for (int worldX = bounds.minX();
             worldX <= bounds.maxX();
             worldX++) {
            for (int worldZ = bounds.minZ();
                 worldZ <= bounds.maxZ();
                 worldZ++) {

                int waterSurfaceY =
                        findWaterSurfaceY(
                                level,
                                worldX,
                                worldZ
                        );

                if (waterSurfaceY == Integer.MIN_VALUE) {
                    return false;
                }

                if (Math.abs(
                        waterSurfaceY - centerWaterY
                ) > CEMETERY_MAX_HEIGHT_DIFFERENCE) {
                    return false;
                }
            }
        }

        return true;
    }
    // FIND PORTAL GROUND
    private static int findPortalGroundY( ServerLevel level, int x, int z ) {
        int startY = level.getMaxBuildHeight() - 1;

        for (int y = startY; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos( x, y, z );
            BlockState state = level.getBlockState( pos );

            if (state.isAir()) {
                continue;
            }
            if (state.is( BlockTags.LEAVES )) {
                continue;
            }
            if (state.is( BlockTags.LOGS )) {
                continue;
            }

            /*
             * Water counts as valid portal ground.
             * Because we scan downward from the top of the world,
             * the first water block found is the top water surface.
             * The structure is then placed one block above it.
             */
            if (!level.getFluidState( pos ).isEmpty()) {
                return y;
            }

            if (state.canBeReplaced()) {
                continue;
            }

            return y;
        }

        return Integer.MIN_VALUE;
    }
    // FIND WATER SURFACE
    private static int findWaterSurfaceY(
            ServerLevel level,
            int x,
            int z
    ) {
        int startY =
                level.getMaxBuildHeight() - 1;

        for (int y = startY;
             y >= level.getMinBuildHeight();
             y--) {
            BlockPos pos =
                    new BlockPos(
                            x,
                            y,
                            z
                    );

            BlockState state =
                    level.getBlockState(pos);

            if (state.isAir()) {
                continue;
            }
            if (state.is(BlockTags.LEAVES)) {
                continue;
            }
            if (state.is(BlockTags.LOGS)) {
                continue;
            }

            if (!level.getFluidState(pos).isEmpty()) {
                return y;
            }

            /*
             * The first non-foliage solid surface is land,
             * so this X/Z is not a water fallback candidate.
             */
            if (!state.canBeReplaced()) {
                return Integer.MIN_VALUE;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static int findGroundY( ServerLevel level, int x, int z ) {
        int startY = level.getMaxBuildHeight() - 1;

        for (int y = startY; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos( x, y, z );
            BlockState state = level.getBlockState( pos );

            if (state.isAir()) {
                continue;
            }
            if (state.is( BlockTags.LEAVES )) {
                continue;
            }
            if (state.is( BlockTags.LOGS )) {
                continue;
            }

            if (!level.getFluidState( pos ).isEmpty()) {
                return Integer.MIN_VALUE;
            }

            if (state.canBeReplaced()) {
                continue;
            }

            return y;
        }

        return Integer.MIN_VALUE;
    }
    // HORIZONTAL DISTANCE
    private static long horizontalDistanceSquared(
            BlockPos first,
            BlockPos second
    ) {
        long deltaX = (long) first.getX() - second.getX();
        long deltaZ = (long) first.getZ() - second.getZ();

        return deltaX * deltaX
                + deltaZ * deltaZ;
    }
    // CREATE STRUCTURE BOUNDS
    private static BoundingBox createStructureBounds( BlockPos origin, StructureTemplate template ) {
        Vec3i size = template.getSize();

        return new BoundingBox(
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                origin.getX() + size.getX() - 1,
                origin.getY() + size.getY() - 1,
                origin.getZ() + size.getZ() - 1
        );
    }

    private static void clearPortalArea( ServerLevel level, StructureTemplate template, BlockPos origin ) {
        Vec3i size = template.getSize();
        for (int offsetX = 0; offsetX < size.getX(); offsetX++) {
            for (int offsetY = 0; offsetY < size.getY(); offsetY++) {
                for (int offsetZ = 0; offsetZ < size.getZ(); offsetZ++) {
                    BlockPos pos = origin.offset( offsetX, offsetY, offsetZ );
                    BlockState state = level.getBlockState( pos );
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.canBeReplaced()) {
                        level.removeBlock( pos, false );
                    }
                }
            }
        }
    }

    private static void clearCemeteryArea(
            ServerLevel level,
            BoundingBox bounds
    ) {
        for (int x = bounds.minX();
             x <= bounds.maxX();
             x++) {
            for (int y = bounds.minY();
                 y <= bounds.maxY();
                 y++) {
                for (int z = bounds.minZ();
                     z <= bounds.maxZ();
                     z++) {

                    BlockPos pos =
                            new BlockPos(
                                    x,
                                    y,
                                    z
                            );

                    BlockState state =
                            level.getBlockState(pos);

                    if (state.is(BlockTags.LEAVES)
                            || state.is(BlockTags.LOGS)
                            || state.canBeReplaced()) {
                        level.removeBlock(
                                pos,
                                false
                        );
                    }
                }
            }
        }
    }
    public record CemeteryGenerationResult(
            boolean success,
            BlockPos origin,
            BoundingBox bounds
    ) {
        public static CemeteryGenerationResult failure() {
            return new CemeteryGenerationResult(false, null, null);
        }
    }
    // CEMETERY PLACEMENT
    private record CemeteryPlacement(
            BlockPos origin,
            Rotation rotation,
            BoundingBox bounds,
            BlockPos entrancePos,
            net.minecraft.core.Direction entranceFacing,
            long edgeGapSquared,
            long entranceDistanceSquared,
            boolean entranceFacesPortal
    ) {
    }
    // CEMETERY ENTRANCE INFO
    private record CemeteryEntranceInfo(
            BlockPos pos,
            net.minecraft.core.Direction facing
    ) {
    }

}
