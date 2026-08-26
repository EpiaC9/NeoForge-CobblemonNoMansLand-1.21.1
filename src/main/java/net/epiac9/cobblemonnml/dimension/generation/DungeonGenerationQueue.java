package net.epiac9.cobblemonnml.dimension.generation;

import com.lemenok.cobblemontrialsedition.threads.ActiveStructureTracker;

import net.epiac9.cobblemonnml.Config;
import net.epiac9.cobblemonnml.block.DungeonMarkerBlock;
import net.epiac9.cobblemonnml.block.SpecialRoomMarkerBlock;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonDimensionEvents;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.DungeonSlotManager;
import net.epiac9.cobblemonnml.dimension.encounter.DungeonEncounterContext;
import net.epiac9.cobblemonnml.dimension.encounter.DungeonEncounterManager;
import net.epiac9.cobblemonnml.dimension.encounter.DungeonMarkerCapture;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.portal.DungeonPortalManager;
import net.epiac9.cobblemonnml.portal.DungeonPortalVisualState;
import net.epiac9.cobblemonnml.events.quest.item.DungeonQuestItemMarkerManager;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import net.minecraft.world.level.block.JigsawBlock;

import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public final class DungeonGenerationQueue {
    // ENCOUNTER FEATURES
    /*
     * Alpha Pokémon encounters are disabled until the appropriate Cobblemon Alpha implementation is available.
     */
    private static final boolean ALPHA_ENCOUNTERS_ENABLED = false;
    // STRUCTURE PIECES
    private static final Queue<PoolElementStructurePiece> PIECES = new ArrayDeque<>();

    /*
     * Completed logical rooms waiting for encounter setup.
     * Room activation is deliberately spread across server ticks so EasyNPC / TBCS / Trials / Raid setup for several rooms
     * cannot all land on one large server-thread spike.
     */
    private static final Queue<DungeonRoom> ROOMS_TO_RESOLVE = new ArrayDeque<>();
    private static final int ROOMS_PER_TICK = 1;
    // PROCESSED MARKERS
    private static final Set<BlockPos> PROCESSED_MARKERS = new HashSet<>();
    // SLOT BOUNDING BOXES
    private static final List<BoundingBox> SLOT_A_BOUNDS = new ArrayList<>();
    private static final List<BoundingBox> SLOT_B_BOUNDS = new ArrayList<>();
    private static final List<BoundingBox> SLOT_C_BOUNDS = new ArrayList<>();
    private static final List<BoundingBox> SLOT_D_BOUNDS = new ArrayList<>();

    /*
     * Bounds belonging to the accepted special-room branch for the active generation.
     * Encounter markers inside these bounds carry special-room provenance into
     * DungeonEncounterManager so trainer markers can route to Quest NPCs.
     */
    private static final List<BoundingBox> SPECIAL_ROOM_BOUNDS = new ArrayList<>();
    // GENERATION STATE
    private static ServerLevel level;
    private static BlockPos startPos;
    private static DungeonSlotManager.Slot generatingSlot;
    private static DungeonTier generatingTier;

    /*
     * Store the active theme at generation start.
     *
     * This gives Trials Edition a stable structure identity for every jigsaw piece during the entire generation process.
     */
    private static DungeonTheme generatingTheme;
    // GENERATION FAIL-SAFE
    private static final long GENERATION_TIMEOUT_TICKS = 20L * 120L;
    private static long generationStartedGameTime = -1L;
    // TICKET-DRIVEN CHUNK PREPARATION
    /*
     * Pass 8 removes the direct FULL-chunk future request from the server thread. A lightweight region ticket asks vanilla's chunk
     * system to make the destination chunk available, while this queue only polls getChunkNow() on later ticks.
     *
     * The custom ticket has no timeout. Every ticket is explicitly removed when dungeon generation completes or is cancelled.
     */
    private static final TicketType<ChunkPos> DUNGEON_GENERATION_TICKET =
            TicketType.create("cobblemonnml_dungeon_generation", Comparator.comparingLong(ChunkPos::toLong));

    /*
     * Radius 0 asks for the target chunk itself at FULL accessibility. Don't need forced ticking; the chunk only needs to be present
     * so the structure template can safely write into it.
     */
    private static final int DUNGEON_CHUNK_TICKET_RADIUS = 0;
    private static PoolElementStructurePiece pieceBeingPrepared;
    private static final Queue<ChunkPos> CHUNKS_TO_PREPARE = new ArrayDeque<>();

    /*
     * Pieces commonly overlap destination chunks. Once a chunk has become available, keep its ticket alive for the remainder of this
     * generation and do not prepare it again.
     */
    private static final Set<Long> PREPARED_CHUNKS = new HashSet<>();
    private static final Set<ChunkPos> ACTIVE_CHUNK_TICKETS = new HashSet<>();
    private static ChunkPos chunkBeingPrepared;
    private static long chunkTicketRequestedNanos = 0L;
    private static int chunkTicketWaitTicks = 0;
    private static int currentPieceNumber = 0;
    private static int currentPieceTotalChunkCount = 0;
    private static int currentPiecePreparedChunkCount = 0;
    private static double currentPieceSlowestChunkReadyMillis = 0.0D;
    private static long currentPieceChunkPreparationStartedNanos = 0L;
    // SLOT ENTITY SAFETY
    /*
     * The normal block reset intentionally uses exact jigsaw piece bounds because filling an entire dungeon slot would
     * be unnecessarily expensive.
     * Entity cleanup can be broader because we only iterate entities that are already loaded.
     * The safety radius is calculated from the largest dungeon tier generation distance plus this extra margin.
     */
    private static final int SLOT_ENTITY_SAFETY_MARGIN = 128;

    /*
     * The second full-world verification scan was useful while the stale-entity issue was being diagnosed, but it duplicates the
     * cost of every normal slot sweep. Leave it disabled for normal gameplay and temporarily enable it only when troubleshooting.
     */
    private static final boolean VERIFY_ENTITY_SWEEPS = false;

    /*
     * Detailed marker-by-marker logging is useful while debugging room assignment, but synchronous console output can dwarf the actual
     * marker-processing cost. Keep normal gameplay on summary logging.
     */
    private static final boolean VERBOSE_MARKER_LOGGING = false;
    // OVERWORLD PORTAL
    private static ServerLevel overworld;
    private static BlockPos overworldPortalCenter;
    private static final double GENERATION_BAR_RANGE = 64.0D;
    // GENERATION PROGRESS
    private static int totalPieces = 0;
    private static int placedPieces = 0;
    private static int totalRoomsToResolve = 0;
    private static int resolvedRooms = 0;
    private static boolean encounterSetupPrepared = false;

    /*
     * Main dungeon structure placement completes before the special branch is calculated.
     * This prevents resolveGeneratedRooms() from consuming/removing normal encounter markers before a possible special corridor and
     * special room have also been placed.
     */
    private static boolean specialRoomPhaseComplete = false;
    // FORCED SPECIAL ROOM
    /*
     * Portal selection can request that the next dungeon generation
     * force a special-room branch instead of using the normal configured
     * chance roll. The request is consumed once when generation starts.
     */
    private static boolean forceSpecialRoomForNextGeneration = false;
    private static boolean forceSpecialRoomThisGeneration = false;

    /*
     * Start corridor = depth 0.
     * Attached special room = depth 1.
     * The room itself terminates, so no deeper branch is required.
     */
    private static final int SPECIAL_ROOM_MAX_DEPTH = 1;
    private static final int SPECIAL_ROOM_MAX_DISTANCE = 128;

    /*
     * JigsawPlacement chooses a starting rotation itself.
     * We calculate several tiny candidate layouts using different seeds until the corridor entrance faces the direction required by the
     * SpecialRoomMarkerBlock.
     * These calls only calculate pieces. Nothing is physically placed until the matching pieces enter the normal PIECES queue.
     */
    private static final int SPECIAL_ROOM_ROTATION_ATTEMPTS = 32;
    private static int generatedMessageTicks = 0;
    // GENERATION BOSS BAR
    private static final ServerBossEvent GENERATION_BAR =
            new ServerBossEvent(
                    Component.literal( "Dungeon Generating" ),
                    BossEvent.BossBarColor.YELLOW,
                    BossEvent.BossBarOverlay.PROGRESS
            );
    // ROOM DATA
    private record RoomMarker( BlockPos pos, String marker, boolean specialRoom ) {
    }
    private record DungeonRoom( BlockPos anchor, List<RoomMarker> markers ) {
    }
    private record SpecialRoomMarker( BlockPos pos, Direction facing ) {
    }
    private enum RoomCategory {
        VAULT_SPAWNER,
        TRAINER,
        ALPHA,
        RAID
    }
    // FORCE SPECIAL ROOM FOR NEXT GENERATION
    public static void setForceSpecialRoomForNextGeneration(boolean force) {
        forceSpecialRoomForNextGeneration = force;
        DebugLog.log(
                "[CobblemonNML] Force special room for next generation: "
                        + force
        );
    }
    // START GENERATION
    public static void start(
            ServerLevel dungeonLevel,
            ServerLevel overworldLevel,
            BlockPos portalCenter,
            DungeonSlotManager.Slot slot,
            DungeonTier tier,
            BlockPos dungeonStart,
            Iterable<PoolElementStructurePiece> pieces
    ) {
        PIECES.clear();
        ROOMS_TO_RESOLVE.clear();
        PROCESSED_MARKERS.clear();
        SPECIAL_ROOM_BOUNDS.clear();
        DungeonMarkerCapture.reset();
        pieceBeingPrepared = null;
        CHUNKS_TO_PREPARE.clear();
        PREPARED_CHUNKS.clear();
        clearChunkTicketPreparationState();
        currentPieceNumber = 0;
        totalRoomsToResolve = 0;
        resolvedRooms = 0;
        encounterSetupPrepared = false;
        specialRoomPhaseComplete = false;
        forceSpecialRoomThisGeneration = forceSpecialRoomForNextGeneration;
        forceSpecialRoomForNextGeneration = false;
        level = dungeonLevel;
        generationStartedGameTime = dungeonLevel.getGameTime();
        overworld = overworldLevel;
        overworldPortalCenter = portalCenter.immutable();
        generatingSlot = slot;
        generatingTier = tier;

        /*
         * DungeonSession has already been started before generation begins, so capture its resolved theme here.
         */
        generatingTheme = DungeonSession.getTheme();
        startPos = dungeonStart.immutable();
        // CLEAR OLD BOUNDS FOR REUSED SLOT
        getMutableBounds(slot).clear();
        // QUEUE STRUCTURE PIECES
        for (PoolElementStructurePiece piece : pieces) {
            PIECES.add(piece);
        }
        totalPieces = PIECES.size();
        placedPieces = 0;
        generatedMessageTicks = 0;
        // DEBUG
        DebugLog.log( "Starting dungeon generation." );
        DebugLog.log( "Slot: " + slot );
        DebugLog.log( "Theme: " + (generatingTheme != null ? generatingTheme.getDisplayName() : "UNKNOWN"));
        DebugLog.log( "Tier: " + (tier != null ? tier.getDisplayName() : "UNKNOWN"));
        DebugLog.log( "Trials Edition structure ID: " + getTrialsStructureId());
        printCurrentTierWeights();
        // RESET BOSS BAR
        GENERATION_BAR.removeAllPlayers();
        GENERATION_BAR.setColor( BossEvent.BossBarColor.YELLOW );
        GENERATION_BAR.setName( Component.literal( "Dungeon Generating 0/" + totalPieces ) );
        GENERATION_BAR.setProgress( 0.0F );
        addVisiblePlayers();
        // DEFER ALL STRUCTURE PLACEMENT TO SERVER TICKS
        /*
         * Do not place the first jigsaw piece synchronously from portal activation. This lets the arming/activation call
         * return immediately and keeps every structure piece under the same one-piece-per-tick budget below.
         * The overworld portal intentionally remains locked while this queue is active and is only switched to the active
         * portal block after completeGeneration().
         */
        if (PIECES.isEmpty()) {
            finishGeneration();
        }
    }
    // SERVER TICK
    public static void tick() {
        // GENERATION TIMEOUT FAIL-SAFE
        if (level != null && generationStartedGameTime >= 0L) {
            long elapsedTicks =
                    level.getGameTime()
                            - generationStartedGameTime;
            if (elapsedTicks >= GENERATION_TIMEOUT_TICKS) {
                DebugLog.log("[CobblemonNML] FAIL-SAFE: " + "Dungeon generation exceeded timeout.");
                DebugLog.log("[CobblemonNML] Elapsed ticks: " + elapsedTicks);
                cancelGeneration();
                DungeonDimensionEvents.requestReset();
                return;
            }
        }
        // COMPLETED MESSAGE TIMER
        if (generatedMessageTicks > 0) {
            generatedMessageTicks--;
            if (generatedMessageTicks == 0) {
                GENERATION_BAR.removeAllPlayers();
                overworld = null;
                overworldPortalCenter = null;
            }
        }
        // NOTHING GENERATING
        if (level == null) {
            return;
        }
        addVisiblePlayers();
        removePlayersWhoShouldNotSeeBar();
        // PREPARE / PLACE STRUCTURE PIECES
        if (pieceBeingPrepared != null || !PIECES.isEmpty()) {
            tickStructurePiecePreparation();
            return;
        }
        // PROGRESSIVE ENCOUNTER SETUP
        if (encounterSetupPrepared) {
            tickEncounterSetup();
        }
    }
    // PREPARE NEXT STRUCTURE PIECE
    private static void tickStructurePiecePreparation() {
        if (level == null) {
            return;
        }
        // ACQUIRE NEXT PIECE
        if (pieceBeingPrepared == null) {
            pieceBeingPrepared = PIECES.poll();
            CHUNKS_TO_PREPARE.clear();
            if (pieceBeingPrepared == null) {
                finishGeneration();
                return;
            }
            currentPieceNumber = placedPieces + 1;
            queueChunksForPiece( pieceBeingPrepared );
            DebugLog.log(
                    "[CobblemonNML] Preparing "
                            + CHUNKS_TO_PREPARE.size()
                            + " destination chunk(s) for structure piece "
                            + currentPieceNumber
                            + "/"
                            + totalPieces
                            + " "
                            + pieceBeingPrepared.getBoundingBox()
            );
        }
        // TICKET-DRIVEN DESTINATION-CHUNK PREPARATION
        if (tickTicketChunkPreparation()) {

            /*
             * A ticket was added, the current chunk is still loading, or one chunk became ready on this tick. Never stack the
             * next chunk request or structure placement onto that tick.
             */
            return;
        }
        if (currentPieceTotalChunkCount > 0 && currentPieceChunkPreparationStartedNanos != 0L) {
            double elapsedMillis =
                    (System.nanoTime() - currentPieceChunkPreparationStartedNanos)
                            / 1_000_000.0D;
            DebugLog.logf(
                    "[CobblemonNML] PERF: Destination chunks ready for structure piece %d/%d: %d/%d chunk(s), slowest %.2f ms, elapsed %.2f ms.%n",
                    currentPieceNumber,
                    totalPieces,
                    currentPiecePreparedChunkCount,
                    currentPieceTotalChunkCount,
                    currentPieceSlowestChunkReadyMillis,
                    elapsedMillis
            );
        }
        // ALL DESTINATION CHUNKS ARE WARM - PLACE THE PIECE
        PoolElementStructurePiece piece = pieceBeingPrepared;
        pieceBeingPrepared = null;
        long pieceStartedNanos = System.nanoTime();
        placePiece(piece);
        logTiming(
                "Structure piece "
                        + currentPieceNumber
                        + "/"
                        + totalPieces
                        + " "
                        + piece.getBoundingBox(),
                pieceStartedNanos
        );
        placedPieces++;
        updateGenerationBar();
        currentPieceNumber = 0;
        currentPieceTotalChunkCount = 0;
        currentPiecePreparedChunkCount = 0;
        currentPieceSlowestChunkReadyMillis = 0.0D;
        currentPieceChunkPreparationStartedNanos = 0L;
        if (PIECES.isEmpty()) {
            finishGeneration();
        }
    }
    // TICKET-DRIVEN DESTINATION CHUNK PREPARATION
    /**
     * @return true when this tick was consumed by chunk preparation;
     *         false only when there are no chunks left to prepare.
     */
    private static boolean tickTicketChunkPreparation() {
        if (level == null) {
            clearChunkTicketPreparationState();
            return false;
        }
        // POLL THE CURRENT TICKETED CHUNK WITHOUT BLOCKING
        if (chunkBeingPrepared != null) {
            if (level .getChunkSource() .getChunkNow( chunkBeingPrepared.x, chunkBeingPrepared.z ) == null) {
                chunkTicketWaitTicks++;

                /*
                 * A very slow chunk is no longer a server-thread freeze.
                 * Report prolonged waits occasionally while continuing to let the vanilla chunk system work in the background.
                 */
                if (chunkTicketWaitTicks > 0 && chunkTicketWaitTicks % 200 == 0) {
                    DebugLog.log(
                            "[CobblemonNML] WARNING: Still waiting for ticketed structure chunk ["
                                    + chunkBeingPrepared.x
                                    + ", "
                                    + chunkBeingPrepared.z
                                    + "] for piece "
                                    + currentPieceNumber
                                    + "/"
                                    + totalPieces
                                    + " after "
                                    + chunkTicketWaitTicks
                                    + " server ticks."
                    );
                }
                return true;
            }
            double readyMillis =
                    chunkTicketRequestedNanos == 0L
                            ? 0.0D
                            : (System.nanoTime() - chunkTicketRequestedNanos)
                            / 1_000_000.0D;
            currentPiecePreparedChunkCount++;
            currentPieceSlowestChunkReadyMillis = Math.max( currentPieceSlowestChunkReadyMillis, readyMillis );
            PREPARED_CHUNKS.add( ChunkPos.asLong( chunkBeingPrepared.x, chunkBeingPrepared.z ) );
            if (readyMillis >= 1000.0D) {
                DebugLog.logf(
                        "[CobblemonNML] PERF WARNING: Ticketed structure chunk [%d, %d] for piece %d/%d became ready after %.2f ms across %d server tick(s).%n",
                        chunkBeingPrepared.x,
                        chunkBeingPrepared.z,
                        currentPieceNumber,
                        totalPieces,
                        readyMillis,
                        chunkTicketWaitTicks
                );
            }
            chunkBeingPrepared = null;
            chunkTicketRequestedNanos = 0L;
            chunkTicketWaitTicks = 0;

            /*
             * Deliberately stop here. The next ticket request, or the structure placement when this was the final chunk, happens
             * on a later server tick.
             */
            return true;
        }
        // ACQUIRE THE NEXT DESTINATION CHUNK
        chunkBeingPrepared = CHUNKS_TO_PREPARE.poll();
        if (chunkBeingPrepared == null) {
            return false;
        }
        // ADD ONE LIGHTWEIGHT REGION TICKET
        long ticketStartedNanos = System.nanoTime();
        try {
            level.getChunkSource()
                    .addRegionTicket(
                            DUNGEON_GENERATION_TICKET,
                            chunkBeingPrepared,
                            DUNGEON_CHUNK_TICKET_RADIUS,
                            chunkBeingPrepared
                    );
            ACTIVE_CHUNK_TICKETS.add( chunkBeingPrepared );
        } catch (RuntimeException exception) {
            DebugLog.log(
                    "[CobblemonNML] ERROR: Could not add dungeon-generation chunk ticket for ["
                            + chunkBeingPrepared.x
                            + ", "
                            + chunkBeingPrepared.z
                            + "]: "
                            + exception.getMessage()
            );

            /*
             * Do not fall back to a synchronous FULL-chunk request. That was the source of the multi-second server freezes we are
             * explicitly avoiding. Leave this chunk queued for a retry on a later tick instead.
             */
            CHUNKS_TO_PREPARE.add( chunkBeingPrepared );
            chunkBeingPrepared = null;
            return true;
        }
        logSlowTiming(
                "Chunk ticket submission ["
                        + chunkBeingPrepared.x
                        + ", "
                        + chunkBeingPrepared.z
                        + "] for piece "
                        + currentPieceNumber
                        + "/"
                        + totalPieces,
                ticketStartedNanos,
                10.0D
        );
        chunkTicketRequestedNanos = System.nanoTime();
        chunkTicketWaitTicks = 0;
        return true;
    }
    // CLEAR TICKET-DRIVEN CHUNK STATE
    private static void clearChunkTicketPreparationState() {
        if (level != null && !ACTIVE_CHUNK_TICKETS.isEmpty()) {
            for (ChunkPos ticketPos : new ArrayList<>(ACTIVE_CHUNK_TICKETS)) {
                try {
                    level.getChunkSource()
                            .removeRegionTicket(
                                    DUNGEON_GENERATION_TICKET,
                                    ticketPos,
                                    DUNGEON_CHUNK_TICKET_RADIUS,
                                    ticketPos
                            );
                } catch (RuntimeException exception) {
                    DebugLog.log(
                            "[CobblemonNML] WARNING: Could not remove dungeon-generation chunk ticket for ["
                                    + ticketPos.x
                                    + ", "
                                    + ticketPos.z
                                    + "]: "
                                    + exception.getMessage()
                    );
                }
            }
        }
        ACTIVE_CHUNK_TICKETS.clear();
        chunkBeingPrepared = null;
        chunkTicketRequestedNanos = 0L;
        chunkTicketWaitTicks = 0;
        currentPieceTotalChunkCount = 0;
        currentPiecePreparedChunkCount = 0;
        currentPieceSlowestChunkReadyMillis = 0.0D;
        currentPieceChunkPreparationStartedNanos = 0L;
    }
    // QUEUE DESTINATION CHUNKS FOR ONE PIECE
    private static void queueChunksForPiece(PoolElementStructurePiece piece) {
        CHUNKS_TO_PREPARE.clear();
        if (piece == null) {
            return;
        }
        BoundingBox box = piece.getBoundingBox();
        int minChunkX = Math.floorDiv( box.minX(), 16 );
        int maxChunkX = Math.floorDiv( box.maxX(), 16 );
        int minChunkZ = Math.floorDiv( box.minZ(), 16 );
        int maxChunkZ = Math.floorDiv( box.maxZ(), 16 );
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long chunkKey = ChunkPos.asLong( chunkX, chunkZ );
                if (PREPARED_CHUNKS.contains( chunkKey )) {
                    continue;
                }
                CHUNKS_TO_PREPARE.add( new ChunkPos( chunkX, chunkZ ) );
            }
        }
        currentPieceTotalChunkCount = CHUNKS_TO_PREPARE.size();
        currentPiecePreparedChunkCount = 0;
        currentPieceSlowestChunkReadyMillis = 0.0D;
        currentPieceChunkPreparationStartedNanos =
                currentPieceTotalChunkCount > 0
                        ? System.nanoTime()
                        : 0L;

    }
    // TRIALS EDITION STRUCTURE ID
    private static ResourceLocation getTrialsStructureId() {
        // FULL THEME + TIER ID
        if (generatingTheme != null && generatingTier != null) {
            String tierPath =
                    switch (generatingTier) {
                        case TIER_1 -> "tier1";
                        case TIER_2 -> "tier2";
                        case TIER_3 -> "tier3";
                        case TIER_4 -> "tier4";
                    };

            /*
             * Examples: cobblemonnml:dungeon/ghost/tier1
             */
            return ResourceLocation.fromNamespaceAndPath(
                            "cobblemonnml",
                            "dungeon/"
                                    + generatingTheme.getId()
                                    + "/"
                                    + tierPath
            );
        }
        // SAFE FALLBACK
        /*
         * This should not normally be used because a dungeon session should always have both Theme and Tier before
         * generation starts.
         */
        if (generatingTier != null) {
            return switch (generatingTier) {
                case TIER_1 -> ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon/tier1" );
                case TIER_2 -> ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon/tier2" );
                case TIER_3 -> ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon/tier3" );
                case TIER_4 -> ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon/tier4" );
            };
        }
        return ResourceLocation
                .fromNamespaceAndPath( "cobblemonnml", "dungeon" );
    }
    // PLACE ONE STRUCTURE PIECE
    private static void placePiece( PoolElementStructurePiece piece ) {
        if (level == null || startPos == null) {
            return;
        }
        StructureManager structureManager = level.structureManager();
        ChunkGenerator chunkGenerator = level.getChunkSource().getGenerator();
        RandomSource random = level.getRandom();
        ResourceLocation trialsStructureId = getTrialsStructureId();
        // TELL TRIALS EDITION WHICH THEMED CONFIG IS ACTIVE
        /*
         * Enable the marker capture window only while this structure piece is physically being placed.
         * DungeonMarkerBlock.onPlace() records the exact world positions of marker blocks during this window.
         */
        DungeonMarkerCapture.beginPiece( level );
        ActiveStructureTracker.set( trialsStructureId );
        try {
            piece.place( level, structureManager, chunkGenerator, random, BoundingBox.infinite(), startPos, false );
        } finally {
            ActiveStructureTracker.clear();
            DungeonMarkerCapture.endPiece();
        }

        /*
         * Encounter markers are still resolved only after every jigsaw piece has finished.
         * The difference is that we now remember their exact positions while placement happens instead of rescanning the full
         * volume of every completed structure piece afterward.
         */
        // STORE PIECE BOUNDS
        if (generatingSlot != null) {
            getMutableBounds(generatingSlot).add( piece.getBoundingBox() );
        }
    }
    // RESOLVE COMPLETED DUNGEON ROOMS
    private static void resolveGeneratedRooms() {
        if (level == null || generatingSlot == null) {
            return;
        }
        List<BoundingBox> bounds = getMutableBounds(generatingSlot);
        if (bounds.isEmpty()) {
            DebugLog.log( "No dungeon bounds available for room scan." );
            return;
        }
        long preparationStartedNanos = System.nanoTime();
        PROCESSED_MARKERS.clear();
        List<BlockPos> roomAnchors = new ArrayList<>();
        List<RoomMarker> encounterMarkers = new ArrayList<>();

        /*
         * Special-room markers are structural generation candidates.
         * They must never be assigned to a normal encounter room, because the normal room resolver discards every non-selected encounter
         * marker after choosing a category.
         */
        List<SpecialRoomMarker> specialRoomMarkers = new ArrayList<>();

        /*
         * Portal markers are structural/global markers, not encounter-room markers.
         */
        List<BlockPos> portalMarkers = new ArrayList<>();
        // PROCESS CAPTURED MARKER POSITIONS
        long markerScanStartedNanos = System.nanoTime();
        List<BlockPos> capturedMarkerPositions = DungeonMarkerCapture.snapshot();
        if (!capturedMarkerPositions.isEmpty()) {

            /*
             * Fast path: inspect only the marker positions that announced themselves while the jigsaw pieces were being placed. This makes
             * marker preparation scale with marker count instead of dungeon volume.
             */
            for (BlockPos markerPos : capturedMarkerPositions) {
                collectCompletedMarker( markerPos, roomAnchors, encounterMarkers, specialRoomMarkers, portalMarkers );
            }
        } else {

            /*
             * Safety fallback. This should not normally run. If another mod or a future Minecraft change bypasses Block.onPlace() for structure
             * marker placement, keep the old bounding-box scan available so a dungeon still functions instead of silently losing encounters.
             */
            DebugLog.log(
                    "WARNING: No dungeon marker positions were captured during structure placement. "
                            + "Falling back to the legacy structure-volume marker scan."
            );
            BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
            for (BoundingBox box : bounds) {
                if (box == null) {
                    continue;
                }
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        for (int z = box.minZ(); z <= box.maxZ(); z++) {
                            scanPos.set( x, y, z );
                            BlockState state = level.getBlockState( scanPos );
                            if (!(state.getBlock() instanceof DungeonMarkerBlock)) {
                                continue;
                            }
                            collectCompletedMarker(
                                    scanPos,
                                    roomAnchors,
                                    encounterMarkers,
                                    specialRoomMarkers,
                                    portalMarkers
                            );
                        }
                    }
                }
            }
        }
        logTiming( "Completed dungeon marker processing", markerScanStartedNanos );
        DebugLog.log(
                "[CobblemonNML] Dungeon marker summary: captured="
                        + capturedMarkerPositions.size()
                        + ", rooms="
                        + roomAnchors.size()
                        + ", encounters="
                        + encounterMarkers.size()
                        + ", specialRooms="
                        + specialRoomMarkers.size()
                        + ", portals="
                        + portalMarkers.size()
                        + "."
        );
        // RETURN PORTALS
        int placedReturnPortals = 0;
        for (BlockPos portalMarker : portalMarkers) {
            if (placeDungeonReturnPortal( portalMarker )) {
                placedReturnPortals++;
            }
        }
        if (!portalMarkers.isEmpty()) {
            DebugLog.log( "Placed " + placedReturnPortals + "/" + portalMarkers.size() + " dungeon return portal(s)." );
        }
        // NO ROOM ANCHORS
        if (roomAnchors.isEmpty()) {
            DebugLog.log( "WARNING: No room marker blocks were found. " + "Encounter markers will not activate." );
            return;
        }
        // CREATE LOGICAL ROOMS
        List<DungeonRoom> rooms = new ArrayList<>();
        for (BlockPos roomAnchor : roomAnchors) {
            rooms.add( new DungeonRoom( roomAnchor, new ArrayList<>() ) );
        }
        // ASSIGN MARKERS TO NEAREST ROOM
        for (RoomMarker encounterMarker : encounterMarkers) {
            DungeonRoom nearestRoom = findNearestRoom( encounterMarker.pos(), rooms );
            if (nearestRoom == null) {
                DebugLog.log(
                        "Could not assign marker '"
                                + encounterMarker.marker()
                                + "' at "
                                + encounterMarker.pos()
                                + " to a room."
                );
                continue;
            }
            nearestRoom.markers().add(encounterMarker);
            if (VERBOSE_MARKER_LOGGING) {
                DebugLog.log(
                        "Assigned marker '"
                                + encounterMarker.marker()
                                + "' at "
                                + encounterMarker.pos()
                                + " to room "
                                + nearestRoom.anchor()
                );
            }
        }
        // QUEUE ROOMS FOR PROGRESSIVE RESOLUTION
        ROOMS_TO_RESOLVE.clear();
        resolvedRooms = 0;
        for (DungeonRoom room : rooms) {
            if (room .markers() .isEmpty()) {
                DebugLog.log( "Room anchor " + room.anchor() + " has no encounter markers." );
                continue;
            }
            ROOMS_TO_RESOLVE.add( room );
        }
        totalRoomsToResolve = ROOMS_TO_RESOLVE.size();
        DebugLog.log(
                "Dungeon room preparation complete. "
                        + totalRoomsToResolve
                        + " room(s) queued for progressive resolution."
        );
        logTiming( "Completed encounter preparation", preparationStartedNanos );
    }
    // COLLECT ONE COMPLETED DUNGEON MARKER
    private static void collectCompletedMarker(
            BlockPos markerPos,
            List<BlockPos> roomAnchors,
            List<RoomMarker> encounterMarkers,
            List<SpecialRoomMarker> specialRoomMarkers,
            List<BlockPos> portalMarkers
    ) {
        if (level == null || markerPos == null) {
            return;
        }
        BlockPos markerKey = markerPos.immutable();
        if (!PROCESSED_MARKERS.add( markerKey )) {
            return;
        }

        /*
         * Validate the final world state. A marker may have been captured while an earlier jigsaw piece was placed and then overwritten by a later
         * overlapping piece. Only markers that still exist in the completed dungeon are processed.
         */
        BlockState state = level.getBlockState( markerKey );
        if (!(state.getBlock() instanceof DungeonMarkerBlock markerBlock)) {
            PROCESSED_MARKERS.remove( markerKey );
            return;
        }
        String marker = markerBlock.getMarkerId();
        if (marker == null || marker.isBlank()) {
            PROCESSED_MARKERS.remove( markerKey );
            return;
        }
        marker =
                marker
                        .trim()
                        .toLowerCase();
        if (VERBOSE_MARKER_LOGGING) {
            DebugLog.log( "Found completed dungeon marker '" + marker + "' at " + markerKey );
        }
        switch (marker) {
            case "room" -> roomAnchors.add( markerKey );
            case "portal" -> portalMarkers.add( markerKey );
            case "quest_item" -> {
                DungeonQuestItemMarkerManager.recordMarker( level, markerKey );
                level.setBlock( markerKey, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL );
                return;
            }
            case "special_room" -> {

                /*
                 * SpecialRoomMarkerBlock extends DungeonMarkerBlock, so without this explicit branch it falls through into encounterMarkers.
                 * Capture FACING before any marker cleanup occurs.
                 */
                if (state.getBlock() instanceof SpecialRoomMarkerBlock
                        && state.hasProperty(
                        SpecialRoomMarkerBlock.FACING
                        )) {
                    Direction facing = state.getValue( SpecialRoomMarkerBlock.FACING );
                    specialRoomMarkers.add( new SpecialRoomMarker( markerKey, facing ) );
                    DebugLog.log( "[CobblemonNML] Special room marker detected at " + markerKey + " facing " + facing );
                } else {
                    DebugLog.log(
                            "[CobblemonNML] WARNING: Marker ID 'special_room' at "
                                    + markerKey
                                    + " is not a valid SpecialRoomMarkerBlock with FACING."
                    );
                }
                return;

                /*
                 * Do not remove this marker here. It belongs to the special-room
                 * generation phase and is cleaned up separately after its facing
                 * has been preserved.
                 */
            }
            default -> {
                boolean specialRoom = isInsideSpecialRoomBounds( markerKey );
                encounterMarkers.add( new RoomMarker( markerKey, marker, specialRoom ) );
                if (specialRoom && VERBOSE_MARKER_LOGGING) {
                    DebugLog.log(
                            "[CobblemonNML] Encounter marker '"
                                    + marker
                                    + "' at "
                                    + markerKey
                                    + " classified as special-room content."
                    );
                }
            }
        }

        /*
         * Normal marker blocks are editor/generation metadata and should not
         * remain visible or interactable in the finished dungeon.
         */
        level.setBlock( markerKey, Blocks.AIR.defaultBlockState(), 3 );
    }
    // SPECIAL ROOM PROVENANCE
    private static boolean isInsideSpecialRoomBounds( BlockPos pos ) {
        if (pos == null || SPECIAL_ROOM_BOUNDS.isEmpty()) {
            return false;
        }
        for (BoundingBox box : SPECIAL_ROOM_BOUNDS) {
            if (box != null && box.isInside( pos )) {
                return true;
            }
        }
        return false;
    }
    // PREPARE SPECIAL ROOM BRANCH
    private static boolean prepareSpecialRoomBranch() {
        if (level == null) {
            return false;
        }
        List<SpecialRoomMarker> specialRoomMarkers = new ArrayList<>();

        /*
         * IMPORTANT:
         * Only inspect special_room markers here.
         * Do NOT call collectCompletedMarker(), because that method also consumes/removes normal room and encounter markers.
         * Those must remain untouched until every special structure piece has finished placement.
         */
        for (BlockPos capturedPos : DungeonMarkerCapture.snapshot()) {
            if (capturedPos == null) {
                continue;
            }
            BlockState state = level.getBlockState( capturedPos );
            if (!(state.getBlock() instanceof SpecialRoomMarkerBlock)) {
                continue;
            }
            if (!state.hasProperty( SpecialRoomMarkerBlock.FACING )) {
                continue;
            }
            Direction facing = state.getValue( SpecialRoomMarkerBlock.FACING );
            specialRoomMarkers.add( new SpecialRoomMarker( capturedPos.immutable(), facing ) );
            DebugLog.log(
                    "[CobblemonNML] Special room structural candidate detected at "
                            + capturedPos
                            + " facing "
                            + facing
            );
        }
        if (specialRoomMarkers.isEmpty()) {
            DebugLog.log( "[CobblemonNML] No special-room structural candidates exist." );
            return false;
        }
        Config.TierConfig tierConfig = getCurrentTierConfig();
        int chance =
                tierConfig
                        .specialRoomChance()
                        .get();
        int configuredAttempts =
                tierConfig
                        .specialRoomAttempts()
                        .get();
        chance = Math.clamp( chance , 0, 100);
        configuredAttempts = Math.max( 0, configuredAttempts );
        int actualAttempts = Math.min( configuredAttempts, specialRoomMarkers.size() );
        DebugLog.log(
                "[CobblemonNML] Special room rules: tier="
                        + ( generatingTier != null ? generatingTier.getDisplayName() : "UNKNOWN" )
                        + ", chance="
                        + chance
                        + "% per attempt, configuredAttempts="
                        + configuredAttempts
                        + ", availableMarkers="
                        + specialRoomMarkers.size()
                        + ", actualAttempts="
                        + actualAttempts
                        + ", maximumSuccesses=1."
        );
        RandomSource random = level.getRandom();
        List<SpecialRoomMarker> shuffledMarkers = new ArrayList<>( specialRoomMarkers );
        // FISHER-YATES SHUFFLE
        for (int i = shuffledMarkers.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt( i + 1 );
            SpecialRoomMarker temporary = shuffledMarkers.get( i );
            shuffledMarkers.set( i, shuffledMarkers.get( swapIndex ) );
            shuffledMarkers.set( swapIndex, temporary );
        }
        SpecialRoomMarker selectedMarker = null;
        // FORCED SPECIAL ROOM
        if (forceSpecialRoomThisGeneration) {
            DebugLog.log(
                    "[CobblemonNML] Special room is forced for this generation. "
                            + "Skipping configured chance rolls."
            );

            /*
             * Try the shuffled structural candidates until one can actually
             * calculate and queue a valid special corridor/room branch.
             * This preserves the normal maximum of one special-room success.
             */
            for (int attempt = 0; attempt < shuffledMarkers.size(); attempt++) {
                SpecialRoomMarker candidate = shuffledMarkers.get( attempt );

                DebugLog.log(
                        "[CobblemonNML] Forced special room candidate "
                                + (attempt + 1)
                                + "/"
                                + shuffledMarkers.size()
                                + " at "
                                + candidate.pos()
                                + " facing "
                                + candidate.facing()
                );

                if (queueSpecialRoomBranch( candidate )) {
                    DebugLog.log(
                            "[CobblemonNML] Forced special room successfully queued at "
                                    + candidate.pos()
                    );
                    cleanupSpecialRoomMarkers( specialRoomMarkers );
                    return true;
                }

                DebugLog.log(
                        "[CobblemonNML] Forced special room candidate could not generate at "
                                + candidate.pos()
                                + ". Trying another candidate."
                );
            }

            DebugLog.log(
                    "[CobblemonNML] WARNING: Special room was forced, but no available "
                            + "special-room marker could generate a valid branch."
            );
            cleanupSpecialRoomMarkers( specialRoomMarkers );
            return false;
        }
        // CHANCE ATTEMPTS
        for (int attempt = 0; attempt < actualAttempts; attempt++) {
            SpecialRoomMarker candidate = shuffledMarkers.get( attempt );
            int roll = random.nextInt( 100 );
            boolean success = roll < chance;
            DebugLog.log(
                    "[CobblemonNML] Special room attempt "
                            + (attempt + 1)
                            + "/"
                            + actualAttempts
                            + " at "
                            + candidate.pos()
                            + " facing "
                            + candidate.facing()
                            + " | roll="
                            + roll
                            + " | required<"
                            + chance
                            + " -> "
                            + ( success ? "SUCCESS" : "FAIL" )
            );
            if (success) {
                selectedMarker = candidate;
                break;
            }
        }
        // NO WINNER
        if (selectedMarker == null) {
            DebugLog.log( "[CobblemonNML] No special room selected for this dungeon." );
            cleanupSpecialRoomMarkers( specialRoomMarkers );
            return false;
        }
        DebugLog.log(
                "[CobblemonNML] Special room candidate selected at "
                        + selectedMarker.pos()
                        + " facing "
                        + selectedMarker.facing()
        );
        // CALCULATE / QUEUE BRANCH
        boolean queued = queueSpecialRoomBranch( selectedMarker );

        /*
         * Whether branch calculation succeeds or not, none of the editor marker blocks should remain in the final dungeon.
         */
        cleanupSpecialRoomMarkers( specialRoomMarkers );
        return queued;
    }
    // CLEAN UP SPECIAL ROOM MARKERS
    private static void cleanupSpecialRoomMarkers( List<SpecialRoomMarker> specialRoomMarkers ) {
        if (level == null || specialRoomMarkers == null || specialRoomMarkers.isEmpty()) {
            return;
        }
        for (SpecialRoomMarker specialRoomMarker : specialRoomMarkers) {
            if (specialRoomMarker == null || specialRoomMarker.pos() == null) {
                continue;
            }
            BlockPos markerPos = specialRoomMarker.pos();
            BlockState state = level.getBlockState( markerPos );
            if (!(state.getBlock() instanceof SpecialRoomMarkerBlock)) {
                continue;
            }
            level.setBlock( markerPos, Blocks.AIR.defaultBlockState(), 3 );
            DebugLog.log(
                    "[CobblemonNML] Special room marker preserved for special-room processing: "
                            + markerPos
                            + " facing "
                            + specialRoomMarker.facing()
            );
        }
    }
    // QUEUE SPECIAL ROOM JIGSAW BRANCH
    private static boolean queueSpecialRoomBranch( SpecialRoomMarker selectedMarker ) {
        if (level == null || selectedMarker == null || generatingTheme == null) {
            return false;
        }
        // THEME-SPECIFIC CORRIDOR POOL
        /*
         * Example: cobblemonnml:dungeon/bug/special/corridor
         */
        ResourceLocation poolId =
                ResourceLocation.fromNamespaceAndPath( "cobblemonnml", generatingTheme.getSpecialCorridorPool() );
        ResourceKey<StructureTemplatePool> poolKey = ResourceKey.create( Registries.TEMPLATE_POOL, poolId );
        Holder<StructureTemplatePool> corridorPool;
        try {
            corridorPool =
                    level
                            .registryAccess()
                            .registryOrThrow( Registries.TEMPLATE_POOL )
                            .getHolderOrThrow( poolKey );
        } catch (IllegalStateException exception) {
            DebugLog.log( "[CobblemonNML] Missing special corridor template pool: " + poolId );
            return false;
        }
        ResourceLocation startJigsawName =
                ResourceLocation.fromNamespaceAndPath( "cobblemonnml", "dungeon/special/corridor" );
        StructureTemplateManager structureTemplateManager = level.getStructureManager();
        ChunkGenerator chunkGenerator =
                level
                        .getChunkSource()
                        .getGenerator();

        /*
         * SpecialRoomMarkerBlock FACING means: "the branch extends FROM this marker in this direction."
         * The corridor entrance jigsaw itself faces back toward the main dungeon so its puzzle face must point opposite that
         * extension direction.
         */
        Direction requiredEntranceFacing =
                selectedMarker
                        .facing()
                        .getOpposite();
        // FIND A START ROTATION THAT MATCHES MARKER FACING
        for (int rotationAttempt = 0; rotationAttempt < SPECIAL_ROOM_ROTATION_ATTEMPTS; rotationAttempt++) {

            /*
             * JigsawPlacement's public structure-start method chooses a rotation internally rather than accepting one directly.
             * Use a different deterministic branch seed for each layout calculation, then retain only a layout whose corridor
             * entrance actually faces the required direction.
             */
            long branchSeed =
                    level.getSeed()
                            ^ selectedMarker.pos().asLong()
                            ^ ( 0x9E3779B97F4A7C15L * (rotationAttempt + 1L) );
            Structure.GenerationContext generationContext =
                    new Structure.GenerationContext(
                            level.registryAccess(),
                            chunkGenerator,
                            chunkGenerator.getBiomeSource(),
                            level
                                    .getChunkSource()
                                    .randomState(),
                            structureTemplateManager,
                            branchSeed,
                            new ChunkPos( selectedMarker.pos() ),
                            level,
                            biome -> true
                    );
            Optional<Structure.GenerationStub> result;
            try {
                result =
                        JigsawPlacement.addPieces(
                                generationContext,
                                corridorPool,
                                Optional.of( startJigsawName ),
                                SPECIAL_ROOM_MAX_DEPTH,
                                selectedMarker.pos().above(),
                                false,
                                Optional.empty(),
                                SPECIAL_ROOM_MAX_DISTANCE,
                                PoolAliasLookup.EMPTY,
                                JigsawStructure
                                        .DEFAULT_DIMENSION_PADDING,
                                JigsawStructure
                                        .DEFAULT_LIQUID_SETTINGS
                        );
            } catch (RuntimeException exception) {
                DebugLog.log(
                        "[CobblemonNML] Special-room jigsaw layout attempt failed: "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage()
                );
                continue;
            }
            if (result.isEmpty()) {
                continue;
            }
            StructurePiecesBuilder piecesBuilder =
                    result
                            .get()
                            .getPiecesBuilder();
            List<PoolElementStructurePiece> branchPieces = new ArrayList<>();
            for (StructurePiece piece : piecesBuilder .build() .pieces()) {
                if (piece instanceof PoolElementStructurePiece poolPiece) {

                    branchPieces.add( poolPiece );
                }
            }
            if (branchPieces.isEmpty()) {
                continue;
            }
            PoolElementStructurePiece corridorPiece = branchPieces.getFirst( );
            Direction generatedEntranceFacing =
                    findSpecialCorridorEntranceFacing( corridorPiece, structureTemplateManager, startJigsawName );
            DebugLog.log(
                    "[CobblemonNML] Special corridor rotation candidate "
                            + (rotationAttempt + 1)
                            + "/"
                            + SPECIAL_ROOM_ROTATION_ATTEMPTS
                            + ": generatedEntranceFacing="
                            + generatedEntranceFacing
                            + ", requiredEntranceFacing="
                            + requiredEntranceFacing
            );
            if (generatedEntranceFacing != requiredEntranceFacing) {
                continue;
            }
            // MATCH FOUND - APPEND TO EXISTING PROGRESSIVE QUEUE
            SPECIAL_ROOM_BOUNDS.clear();
            for (PoolElementStructurePiece branchPiece : branchPieces) {
                if (branchPiece != null && branchPiece.getBoundingBox() != null) {
                    SPECIAL_ROOM_BOUNDS.add( branchPiece.getBoundingBox() );
                }
            }
            DebugLog.log(
                    "[CobblemonNML] Recorded "
                            + SPECIAL_ROOM_BOUNDS.size()
                            + " special-room branch bound(s) for encounter routing."
            );
            PIECES.addAll(branchPieces);
            totalPieces += branchPieces.size();
            updateGenerationBar();
            DebugLog.log( "[CobblemonNML] Queued " + branchPieces.size() + " special-room structure piece(s)." );
            DebugLog.log(
                    "[CobblemonNML] Special branch origin="
                            + selectedMarker.pos()
                            + ", extensionFacing="
                            + selectedMarker.facing()
                            + ", entranceFacing="
                            + generatedEntranceFacing
            );
            return true;
        }
        DebugLog.log(
                "[CobblemonNML] WARNING: Could not calculate a special-room "
                        + "corridor with the required facing "
                        + requiredEntranceFacing
                        + " after "
                        + SPECIAL_ROOM_ROTATION_ATTEMPTS
                        + " layout attempt(s)."
        );
        return false;
    }
    // FIND SPECIAL CORRIDOR ENTRANCE FACING
    private static Direction findSpecialCorridorEntranceFacing(
            PoolElementStructurePiece corridorPiece,
            StructureTemplateManager structureTemplateManager,
            ResourceLocation startJigsawName
    ) {
        if (level == null || corridorPiece == null || structureTemplateManager == null || startJigsawName == null) {
            return null;
        }
        List<StructureTemplate.StructureBlockInfo> jigsawBlocks =
                corridorPiece
                        .getElement()
                        .getShuffledJigsawBlocks(
                                structureTemplateManager,
                                corridorPiece.getPosition(),
                                corridorPiece.getRotation(),
                                level.getRandom()
                        );
        for (StructureTemplate.StructureBlockInfo jigsawInfo : jigsawBlocks) {
            if (jigsawInfo == null || jigsawInfo.nbt() == null) {
                continue;
            }
            String name =
                    jigsawInfo
                            .nbt()
                            .getString( "name" );
            if (!startJigsawName .toString() .equals( name )) {
                continue;
            }
            if (!(jigsawInfo .state() .getBlock() instanceof JigsawBlock)) {
                continue;
            }
            return JigsawBlock.getFrontFacing( jigsawInfo.state() );
        }
        return null;
    }
    // PLACE DUNGEON RETURN PORTAL
    private static boolean placeDungeonReturnPortal( BlockPos center ) {
        if (level == null || center == null) {
            return false;
        }
        DungeonTier tier = DungeonSession.getTier();
        DungeonTheme theme = DungeonSession.getTheme();
        if (tier == null) {
            tier = generatingTier;
        }
        if (theme == null) {
            theme = generatingTheme;
        }
        if (tier == null || theme == null) {
            DebugLog.log( "Cannot place dungeon return portal at " + center + ": active Theme/Tier is unavailable." );
            return false;
        }
        Block portalBlock =
                ModBlocks
                        .DUNGEON_PORTAL
                        .get();
        int tierIndex =
                DungeonPortalVisualState
                        .tierIndex( tier );
        int themeIndex =
                DungeonPortalVisualState
                        .themeIndex( theme );
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos portalPos = center.offset( x, 0, z );
                int cellIndex =
                        DungeonPortalVisualState
                                .cellIndex( x, z );
                BlockState portalState =
                        portalBlock
                                .defaultBlockState()
                                .setValue( DungeonPortalVisualState.ACTIVATED, true )
                                .setValue( DungeonPortalVisualState.TIER, tierIndex )
                                .setValue( DungeonPortalVisualState.THEME, themeIndex )
                                .setValue( DungeonPortalVisualState.CELL, cellIndex );
                level.setBlock( portalPos, portalState, 3 );
            }
        }
        DebugLog.log(
                "Placed dungeon return portal at "
                        + center
                        + " | Theme: "
                        + theme.getDisplayName()
                        + " | Tier: "
                        + tier.getDisplayName()
        );
        return true;
    }
    // FIND NEAREST ROOM
    private static DungeonRoom findNearestRoom( BlockPos markerPos, List<DungeonRoom> rooms ) {
        if (markerPos == null || rooms == null || rooms.isEmpty()) {
            return null;
        }
        DungeonRoom nearestRoom = null;
        long nearestDistance = Long.MAX_VALUE;
        for (DungeonRoom room : rooms) {
            if (room == null || room.anchor() == null) {
                continue;
            }
            long dx =
                    (long) markerPos.getX()
                            - room.anchor().getX();
            long dy =
                    (long) markerPos.getY()
                            - room.anchor().getY();
            long dz =
                    (long) markerPos.getZ()
                            - room.anchor().getZ();
            long distanceSquared =
                    dx * dx
                            + dy * dy
                            + dz * dz;
            if (distanceSquared < nearestDistance) {
                nearestDistance = distanceSquared;
                nearestRoom = room;
            }
        }
        return nearestRoom;
    }
    // RESOLVE ONE ROOM
    private static void resolveRoomMarkers( List<RoomMarker> markers ) {
        if (level == null || markers == null || markers.isEmpty()) {
            return;
        }

        RandomSource random = level.getRandom();
        Config.TierConfig tierConfig = getCurrentTierConfig();

        List<RoomMarker> raids = getMarkersByType( markers, "raid" );
        List<RoomMarker> alphas = getMarkersByType( markers, "alpha" );
        List<RoomMarker> trainers = getMarkersByType( markers, "trainer" );
        List<RoomMarker> trialSpawners = getMarkersByType( markers, "trial_spawner" );
        List<RoomMarker> eliteSpawners = getMarkersByType( markers, "elite_spawner" );
        List<RoomMarker> bossSpawners = getMarkersByType( markers, "boss_spawner" );
        List<RoomMarker> normalVaults = getMarkersByType( markers, "vault" );
        List<RoomMarker> ominousVaults = getMarkersByType( markers, "ominous_vault" );

        List<RoomCategory> availableCategories = new ArrayList<>();
        if (!raids.isEmpty()) {
            availableCategories.add( RoomCategory.RAID );
        }
        if (ALPHA_ENCOUNTERS_ENABLED && !alphas.isEmpty()) {
            availableCategories.add( RoomCategory.ALPHA );
        }
        if (!trainers.isEmpty()) {
            availableCategories.add( RoomCategory.TRAINER );
        }

        boolean hasSelectableSpawner =
                hasSelectableSpawner( trialSpawners, eliteSpawners, bossSpawners, tierConfig );
        boolean hasSelectableVault =
                hasSelectableVault( normalVaults, ominousVaults, tierConfig );

        if (hasSelectableSpawner && hasSelectableVault) {
            availableCategories.add( RoomCategory.VAULT_SPAWNER );
        }

        if (availableCategories.isEmpty()) {
            DebugLog.log( "Room contained markers but no currently enabled encounter category." );
            return;
        }

        List<RoomCategory> remainingCategories = new ArrayList<>();
        for (RoomCategory category : availableCategories) {
            if (getRoomCategoryWeight( category ) > 0) {
                remainingCategories.add( category );
            }
        }

        if (remainingCategories.isEmpty()) {
            DebugLog.log( "All available normal room categories have weight 0." );
            return;
        }

        boolean firstAttempt = true;

        while (!remainingCategories.isEmpty()) {
            RoomCategory selectedCategory =
                    firstAttempt
                            ? chooseWeightedRoomCategory( remainingCategories, random )
                            : chooseRandomRoomCategory( remainingCategories, random );

            if (selectedCategory == null) {
                DebugLog.log( "Dungeon room category roll failed." );
                return;
            }

            if (firstAttempt) {
                DebugLog.log( "Selected dungeon room category: " + selectedCategory );
            } else {
                DebugLog.log(
                        "[CobblemonNML] ROOM FALLBACK: Trying "
                                + selectedCategory
                                + " from remaining normal room categories "
                                + remainingCategories
                );
            }

            boolean success = tryActivateRoomCategory(
                    selectedCategory,
                    raids,
                    alphas,
                    trainers,
                    trialSpawners,
                    eliteSpawners,
                    bossSpawners,
                    normalVaults,
                    ominousVaults,
                    tierConfig,
                    random
            );

            if (success) {
                if (!firstAttempt) {
                    DebugLog.log(
                            "[CobblemonNML] ROOM FALLBACK: "
                                    + selectedCategory
                                    + " succeeded."
                    );
                }
                return;
            }

            remainingCategories.remove( selectedCategory );

            DebugLog.log(
                    "[CobblemonNML] ROOM FALLBACK: "
                            + selectedCategory
                            + " failed. Removing it from this room's candidates. Remaining: "
                            + remainingCategories
            );

            firstAttempt = false;
        }

        DebugLog.log(
                "[CobblemonNML] ROOM FALLBACK: Every available normal room category failed. "
                        + "Leaving this room without an encounter."
        );
    }
    // TRY ONE ROOM CATEGORY
    private static boolean tryActivateRoomCategory(
            RoomCategory category,
            List<RoomMarker> raids,
            List<RoomMarker> alphas,
            List<RoomMarker> trainers,
            List<RoomMarker> trialSpawners,
            List<RoomMarker> eliteSpawners,
            List<RoomMarker> bossSpawners,
            List<RoomMarker> normalVaults,
            List<RoomMarker> ominousVaults,
            Config.TierConfig tierConfig,
            RandomSource random
    ) {
        if (category == null) {
            return false;
        }

        return switch (category) {
            case RAID -> processMarker( chooseRandom( raids, random ) );
            case ALPHA -> processMarker( chooseRandom( alphas, random ) );
            case TRAINER -> processMarker( chooseRandom( trainers, random ) );
            case VAULT_SPAWNER -> {
                RoomMarker selectedSpawner =
                        chooseWeightedSpawner(
                                trialSpawners,
                                eliteSpawners,
                                bossSpawners,
                                tierConfig,
                                random
                        );

                if (selectedSpawner == null) {
                    DebugLog.log(
                            "Vault + Spawner room was selected, but no weighted spawner could be chosen."
                    );
                    yield false;
                }

                RoomMarker selectedVault =
                        chooseWeightedVault(
                                normalVaults,
                                ominousVaults,
                                tierConfig,
                                random
                        );

                if (selectedVault == null) {
                    DebugLog.log(
                            "Vault + Spawner room was selected, but no weighted vault could be chosen."
                    );
                    yield false;
                }

                DebugLog.log( "Selected dungeon spawner: " + selectedSpawner.marker() );
                boolean spawnerSuccess = processMarker( selectedSpawner );

                if (!spawnerSuccess) {
                    yield false;
                }

                /*
                 * The spawner is the encounter-defining part of this normal room.
                 * Once it exists, do not fall back to another category and create
                 * a second encounter just because the companion vault failed.
                 */
                DebugLog.log( "Selected dungeon vault: " + selectedVault.marker() );
                if (!processMarker( selectedVault )) {
                    DebugLog.log(
                            "[CobblemonNML] ROOM FALLBACK: Spawner succeeded but companion vault failed; "
                                    + "keeping the successful spawner encounter."
                    );
                }

                yield true;
            }
        };
    }
    // RANDOM FALLBACK ROOM CATEGORY
    private static RoomCategory chooseRandomRoomCategory(
            List<RoomCategory> availableCategories,
            RandomSource random
    ) {
        if (availableCategories == null || availableCategories.isEmpty() || random == null) {
            return null;
        }
        return availableCategories.get( random.nextInt( availableCategories.size() ) );
    }
    // HAS SELECTABLE SPAWNER
    private static boolean hasSelectableSpawner(
            List<RoomMarker> trialSpawners,
            List<RoomMarker> eliteSpawners,
            List<RoomMarker> bossSpawners,
            Config.TierConfig tierConfig
    ) {
        if (tierConfig == null) {
            return false;
        }
        if (!trialSpawners.isEmpty() && tierConfig .normalSpawnerChance() .get() > 0) {
            return true;
        }
        if (!eliteSpawners.isEmpty() && tierConfig .eliteSpawnerChance() .get() > 0) {
            return true;
        }
        return !bossSpawners.isEmpty()
                && tierConfig
                .bossSpawnerChance()
                .get() > 0;
    }
    // HAS SELECTABLE VAULT
    private static boolean hasSelectableVault(
            List<RoomMarker> normalVaults,
            List<RoomMarker> ominousVaults,
            Config.TierConfig tierConfig
    ) {
        if (tierConfig == null) {
            return false;
        }
        if (!normalVaults.isEmpty() && tierConfig .normalVaultChance() .get() > 0) {
            return true;
        }
        return !ominousVaults.isEmpty()
                && tierConfig
                .ominousVaultChance()
                .get() > 0;
    }
    // WEIGHTED SPAWNER
    private static RoomMarker chooseWeightedSpawner(
            List<RoomMarker> trialSpawners,
            List<RoomMarker> eliteSpawners,
            List<RoomMarker> bossSpawners,
            Config.TierConfig tierConfig,
            RandomSource random
    ) {
        if (tierConfig == null || random == null) {
            return null;
        }
        int normalWeight =
                trialSpawners.isEmpty()
                        ? 0
                        : tierConfig
                        .normalSpawnerChance()
                        .get();
        int eliteWeight =
                eliteSpawners.isEmpty()
                        ? 0
                        : tierConfig
                        .eliteSpawnerChance()
                        .get();
        int bossWeight =
                bossSpawners.isEmpty()
                        ? 0
                        : tierConfig
                        .bossSpawnerChance()
                        .get();
        int totalWeight =
                normalWeight
                        + eliteWeight
                        + bossWeight;
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt( totalWeight );
        DebugLog.log(
                "Spawner weighted roll: "
                        + roll
                        + "/"
                        + totalWeight
                        + " [normal="
                        + normalWeight
                        + ", elite="
                        + eliteWeight
                        + ", boss="
                        + bossWeight
                        + "]"
        );
        int runningWeight = normalWeight;
        if (normalWeight > 0 && roll < runningWeight) {
            return chooseRandom( trialSpawners, random );
        }
        runningWeight += eliteWeight;
        if (eliteWeight > 0 && roll < runningWeight) {
            return chooseRandom( eliteSpawners, random );
        }
        if (bossWeight > 0) {
            return chooseRandom( bossSpawners, random );
        }
        return null;
    }
    // WEIGHTED VAULT
    private static RoomMarker chooseWeightedVault(
            List<RoomMarker> normalVaults,
            List<RoomMarker> ominousVaults,
            Config.TierConfig tierConfig,
            RandomSource random
    ) {
        if (tierConfig == null || random == null) {
            return null;
        }
        int normalWeight =
                normalVaults.isEmpty()
                        ? 0
                        : tierConfig
                        .normalVaultChance()
                        .get();
        int ominousWeight =
                ominousVaults.isEmpty()
                        ? 0
                        : tierConfig
                        .ominousVaultChance()
                        .get();
        int totalWeight =
                normalWeight
                        + ominousWeight;
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt( totalWeight );
        DebugLog.log(
                "Vault weighted roll: "
                        + roll
                        + "/"
                        + totalWeight
                        + " [normal="
                        + normalWeight
                        + ", ominous="
                        + ominousWeight
                        + "]"
        );
        if (normalWeight > 0 && roll < normalWeight) {
            return chooseRandom( normalVaults, random );
        }
        if (ominousWeight > 0) {
            return chooseRandom( ominousVaults, random );
        }
        return null;
    }
    // WEIGHTED ROOM CATEGORY
    private static RoomCategory chooseWeightedRoomCategory(
            List<RoomCategory> availableCategories,
            RandomSource random
    ) {
        if (availableCategories == null || availableCategories.isEmpty() || random == null) {
            return null;
        }
        int totalWeight = 0;
        for (RoomCategory category : availableCategories) {
            int weight = getRoomCategoryWeight( category );
            if (weight > 0) {
                totalWeight += weight;
            }
        }
        if (totalWeight <= 0) {
            DebugLog.log( "All available room categories have weight 0." );
            return null;
        }
        int roll = random.nextInt( totalWeight );
        int runningWeight = 0;
        for (RoomCategory category : availableCategories) {
            int weight = getRoomCategoryWeight( category );
            if (weight <= 0) {
                continue;
            }
            runningWeight += weight;
            if (roll < runningWeight) {
                DebugLog.log(
                        "Dungeon room weighted roll: "
                                + roll
                                + "/"
                                + totalWeight
                                + " -> "
                                + category
                                + " (weight "
                                + weight
                                + ")"
                );
                return category;
            }
        }
        return null;
    }
    // CURRENT TIER CONFIG
    private static Config.TierConfig getCurrentTierConfig() {
        if (generatingTier == null) {
            return Config.TIER_1;
        }
        return switch (generatingTier) {
            case TIER_1 -> Config.TIER_1;
            case TIER_2 -> Config.TIER_2;
            case TIER_3 -> Config.TIER_3;
            case TIER_4 -> Config.TIER_4;
        };
    }
    // ROOM CATEGORY WEIGHT
    private static int getRoomCategoryWeight( RoomCategory category ) {
        if (category == null) {
            return 0;
        }
        Config.TierConfig tierConfig = getCurrentTierConfig();
        return switch (category) {
            case VAULT_SPAWNER ->
                    tierConfig
                            .vaultSpawnerChance()
                            .get();
            case TRAINER ->
                    tierConfig
                            .trainerChance()
                            .get();
            case ALPHA -> {
                if (!ALPHA_ENCOUNTERS_ENABLED) {
                    yield 0;
                }
                yield tierConfig
                        .alphaChance()
                        .get();
            }
            case RAID ->
                    tierConfig
                            .raidChance()
                            .get();
        };
    }
    // PRINT TIER WEIGHTS
    private static void printCurrentTierWeights() {
        Config.TierConfig tierConfig = getCurrentTierConfig();
        DebugLog.log(
                "Dungeon room generation weights for "
                        + ( generatingTier != null ? generatingTier.getDisplayName() : "fallback Tier 1" )
        );
        DebugLog.log( "Vault + Spawner: " + tierConfig .vaultSpawnerChance() .get() );
        DebugLog.log( "Trainer: " + tierConfig .trainerChance() .get() );
        if (ALPHA_ENCOUNTERS_ENABLED) {
            DebugLog.log( "Alpha: " + tierConfig .alphaChance() .get() );
        } else {
            DebugLog.log( "Alpha: DISABLED" );
        }
        DebugLog.log( "Raid: " + tierConfig .raidChance() .get() );
        DebugLog.log( "Spawner type weights:" );
        DebugLog.log( "  Normal: " + tierConfig .normalSpawnerChance() .get() );
        DebugLog.log( "  Elite: " + tierConfig .eliteSpawnerChance() .get() );
        DebugLog.log( "  Boss: " + tierConfig .bossSpawnerChance() .get() );
        DebugLog.log( "Vault type weights:" );
        DebugLog.log( "  Normal: " + tierConfig .normalVaultChance() .get() );
        DebugLog.log( "  Ominous: " + tierConfig .ominousVaultChance() .get() );
    }
    // GET MARKERS BY TYPE
    private static List<RoomMarker> getMarkersByType( List<RoomMarker> markers, String markerType ) {
        List<RoomMarker> result = new ArrayList<>();
        if (markers == null || markerType == null) {
            return result;
        }
        for (RoomMarker roomMarker : markers) {
            if (roomMarker == null) {
                continue;
            }
            if (roomMarker .marker() .equals( markerType )) {
                result.add( roomMarker );
            }
        }
        return result;
    }
    // RANDOM MARKER
    private static RoomMarker chooseRandom( List<RoomMarker> markers, RandomSource random ) {
        if (markers == null || markers.isEmpty() || random == null) {
            return null;
        }
        return markers.get( random.nextInt( markers.size() ) );
    }
// PROCESS SELECTED MARKER
    private static boolean processMarker(
            RoomMarker marker
    ) {

        if (level == null
                || marker == null) {

            return false;
        }

        DebugLog.log(
                "Activating selected room marker '"
                        + marker.marker()
                        + "' at "
                        + marker.pos()
        );

        long markerStartedNanos =
                System.nanoTime();

        DungeonEncounterContext context =
                marker.specialRoom()
                        ? DungeonEncounterContext.specialRoom()
                        : DungeonEncounterContext.normalRoom();

        boolean success =
                DungeonEncounterManager.tryHandleMarker(
                        level,
                        marker.pos(),
                        marker.marker(),
                        context
                );

        logSlowTiming(
                "Encounter marker '"
                        + marker.marker()
                        + "' at "
                        + marker.pos(),
                markerStartedNanos,
                25.0D
        );

        return success;
    }
    // UPDATE GENERATION BAR
    private static void updateGenerationBar() {
        if (totalPieces <= 0) {
            return;
        }
        float progress =
                (float) placedPieces
                        / (float) totalPieces;
        progress = Math.clamp( progress , 0.0F, 1.0F);
        GENERATION_BAR.setProgress( progress );
        GENERATION_BAR.setName( Component.literal( "Dungeon Generating " + placedPieces + "/" + totalPieces ) );
    }
    // FINISH GENERATION
    private static void finishGeneration() {
        DebugLog.log( "Dungeon structure placement finished for slot " + generatingSlot );
        if (generatingTheme != null) {
            DebugLog.log( "Dungeon theme: " + generatingTheme.getDisplayName() );
        }
        if (generatingTier != null) {
            DebugLog.log( "Dungeon tier: " + generatingTier.getDisplayName() );
        }
        // SPECIAL ROOM STRUCTURAL PHASE
        if (!specialRoomPhaseComplete) {

            /*
             * Mark the phase complete before returning so when the newly queued corridor/room pieces finish, finishGeneration() will
             * continue into normal marker resolution instead of rolling a second special room.
             */
            specialRoomPhaseComplete = true;
            boolean specialPiecesQueued = prepareSpecialRoomBranch();
            if (specialPiecesQueued) {
                DebugLog.log(
                        "[CobblemonNML] Special-room structure pieces queued. "
                                + "Normal encounter resolution will resume after they finish placement."
                );
                return;
            }
        }
        // REMOVE LOADED STALE ENTITIES BEFORE ENCOUNTERS
        long staleSweepStartedNanos = System.nanoTime();
        sweepLoadedNonPlayerEntitiesInSlot( level, startPos, "pre-encounter stale-slot sweep" );
        logSlowTiming( "Pre-encounter stale-slot sweep", staleSweepStartedNanos, 25.0D );
        // PREPARE ROOM MARKERS
        resolveGeneratedRooms();
        DebugLog.log( "Unique dungeon marker positions processed: " + PROCESSED_MARKERS.size() );
        encounterSetupPrepared = true;
        // NO ENCOUNTER ROOMS
        if (ROOMS_TO_RESOLVE.isEmpty()) {
            completeGeneration();
            return;
        }
        GENERATION_BAR.setColor( BossEvent.BossBarColor.YELLOW );
        GENERATION_BAR.setProgress( 0.0F );
        GENERATION_BAR.setName( Component.literal( "Preparing Encounters 0/" + totalRoomsToResolve ) );
    }
    // PROGRESSIVE ENCOUNTER SETUP
    private static void tickEncounterSetup() {
        if (level == null || !encounterSetupPrepared) {
            return;
        }
        int resolvedThisTick = 0;
        while (!ROOMS_TO_RESOLVE.isEmpty() && resolvedThisTick < ROOMS_PER_TICK) {
            DungeonRoom room = ROOMS_TO_RESOLVE.poll();
            if (room == null) {
                continue;
            }
            DebugLog.log(
                    "Resolving dungeon room at "
                            + room.anchor()
                            + " with "
                            + room.markers().size()
                            + " marker(s)."
            );
            long roomStartedNanos = System.nanoTime();
            resolveRoomMarkers( room.markers() );
            resolvedRooms++;
            resolvedThisTick++;
            logTiming(
                    "Dungeon room "
                            + resolvedRooms
                            + "/"
                            + totalRoomsToResolve
                            + " at "
                            + room.anchor(),
                    roomStartedNanos
            );
            updateEncounterSetupBar();
        }
        if (ROOMS_TO_RESOLVE.isEmpty()) {
            DebugLog.log(
                    "Dungeon room resolution complete. "
                            + resolvedRooms
                            + " room(s) resolved across server ticks."
            );
            completeGeneration();
        }
    }
    // ENCOUNTER SETUP BOSS BAR
    private static void updateEncounterSetupBar() {
        if (totalRoomsToResolve <= 0) {
            return;
        }
        float progress =
                (float) resolvedRooms
                        / (float) totalRoomsToResolve;
        progress = Math.clamp( progress , 0.0F, 1.0F);
        GENERATION_BAR.setProgress( progress );
        GENERATION_BAR.setName(
                Component.literal( "Preparing Encounters " + resolvedRooms + "/" + totalRoomsToResolve )
        );
    }
    // COMPLETE GENERATION
    private static void completeGeneration() {
        // UNLOCK OVERWORLD PORTAL ONLY AFTER EVERYTHING IS READY
        /*
         * Structure placement, stale-entity cleanup, marker scan, return-portal placement and every logical encounter room
         * have all completed before this method is reached.
         * Until this exact point the overworld portal remains the non-teleporting core block with ACTIVATED=false.
         */
        if (overworld != null && overworldPortalCenter != null && generatingTier != null && generatingTheme != null) {
            DungeonPortalManager.activatePortal( overworld, overworldPortalCenter, generatingTier, generatingTheme );
            DebugLog.log(
                    "Dungeon portal READY: "
                            + generatingTheme.getDisplayName()
                            + " / "
                            + generatingTier.getDisplayName()
            );
        }
        GENERATION_BAR.setProgress( 1.0F );
        GENERATION_BAR.setColor( BossEvent.BossBarColor.GREEN );
        GENERATION_BAR.setName( Component.literal( "Dungeon Generated" ) );
        generatedMessageTicks = 60;
        PIECES.clear();
        ROOMS_TO_RESOLVE.clear();
        PROCESSED_MARKERS.clear();
        SPECIAL_ROOM_BOUNDS.clear();
        DungeonMarkerCapture.reset();
        pieceBeingPrepared = null;
        CHUNKS_TO_PREPARE.clear();
        PREPARED_CHUNKS.clear();
        clearChunkTicketPreparationState();
        currentPieceNumber = 0;
        encounterSetupPrepared = false;
        specialRoomPhaseComplete = false;
        forceSpecialRoomThisGeneration = false;
        level = null;
        startPos = null;
        generatingSlot = null;
        generatingTier = null;
        generatingTheme = null;
        totalPieces = 0;
        placedPieces = 0;
        totalRoomsToResolve = 0;
        resolvedRooms = 0;
        generationStartedGameTime = -1L;
    }
    // PERFORMANCE TIMING
    private static void logTiming( String label, long startedNanos ) {
        if (label == null) {
            return;
        }
        double elapsedMillis =
                (System.nanoTime() - startedNanos)
                        / 1_000_000.0D;
        DebugLog.logf( "[CobblemonNML] PERF: %s took %.2f ms.%n", label, elapsedMillis );
    }
    private static void logSlowTiming( String label, long startedNanos, double thresholdMillis ) {
        if (label == null) {
            return;
        }
        double elapsedMillis =
                (System.nanoTime() - startedNanos)
                        / 1_000_000.0D;
        if (elapsedMillis < thresholdMillis) {
            return;
        }
        DebugLog.logf( "[CobblemonNML] PERF WARNING: %s took %.2f ms.%n", label, elapsedMillis );
    }
    // ADD VISIBLE PLAYERS
    private static void addVisiblePlayers() {
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                addBossBarPlayer( player );
            }
        }
        if (overworld == null || overworldPortalCenter == null) {
            return;
        }
        double maxDistanceSquared =
                GENERATION_BAR_RANGE
                        * GENERATION_BAR_RANGE;
        for (ServerPlayer player : overworld.players()) {
            double distanceSquared =
                    player.distanceToSqr(
                            overworldPortalCenter.getX()
                                    + 0.5D,

                            overworldPortalCenter.getY()
                                    + 0.5D,

                            overworldPortalCenter.getZ()
                                    + 0.5D
                    );
            if (distanceSquared <= maxDistanceSquared) {

                addBossBarPlayer( player );
            }
        }
    }
    // ADD BOSS BAR PLAYER
    private static void addBossBarPlayer( ServerPlayer player ) {
        if (!GENERATION_BAR .getPlayers() .contains( player )) {
            GENERATION_BAR.addPlayer( player );
        }
    }
    // REMOVE INVALID BOSS BAR PLAYERS
    private static void removePlayersWhoShouldNotSeeBar() {
        List<ServerPlayer> shownPlayers = new ArrayList<>( GENERATION_BAR .getPlayers() );
        double maxDistanceSquared =
                GENERATION_BAR_RANGE
                        * GENERATION_BAR_RANGE;
        for (ServerPlayer player : shownPlayers) {
            boolean shouldSee = false;
            // INSIDE DUNGEON
            if (player .level() .dimension() .equals( DungeonDimension .DUNGEON_DIMENSION )) {
                shouldSee = true;
            }
            // NEAR OVERWORLD PORTAL
            else if (overworld != null
                    && overworldPortalCenter != null
                    && player
                    .level()
                    .dimension()
                    .equals(
                            Level.OVERWORLD
                    )) {
                double distanceSquared =
                        player.distanceToSqr(
                                overworldPortalCenter.getX()
                                        + 0.5D,

                                overworldPortalCenter.getY()
                                        + 0.5D,

                                overworldPortalCenter.getZ()
                                        + 0.5D
                        );
                shouldSee =
                        distanceSquared
                                <= maxDistanceSquared;
            }
            if (!shouldSee) {
                GENERATION_BAR.removePlayer( player );
            }
        }
    }
    // CANCEL GENERATION
    public static void cancelGeneration() {
        ActiveStructureTracker.clear();
        PIECES.clear();
        ROOMS_TO_RESOLVE.clear();
        PROCESSED_MARKERS.clear();
        SPECIAL_ROOM_BOUNDS.clear();
        DungeonMarkerCapture.reset();
        pieceBeingPrepared = null;
        CHUNKS_TO_PREPARE.clear();
        PREPARED_CHUNKS.clear();
        clearChunkTicketPreparationState();
        currentPieceNumber = 0;
        encounterSetupPrepared = false;
        forceSpecialRoomThisGeneration = false;
        totalRoomsToResolve = 0;
        resolvedRooms = 0;
        level = null;
        startPos = null;
        generatingSlot = null;
        generatingTier = null;
        generatingTheme = null;
        overworld = null;
        overworldPortalCenter = null;
        totalPieces = 0;
        placedPieces = 0;
        generatedMessageTicks = 0;
        GENERATION_BAR.removeAllPlayers();
        generationStartedGameTime = -1L;
        DebugLog.log( "Dungeon generation queue cancelled." );
    }
    // SLOT-WIDE LOADED ENTITY SAFETY SWEEP
    /*
     * Removes every loaded non-player entity inside the safety region around one dungeon slot.
     * This is intentionally broader than the exact generated piece bounds. It protects against stale entities belonging
     * to forgotten rooms/corridors from older generation code.
     * Only already-loaded entities are inspected. This does not force-load thousands of chunks.
     */
    public static void sweepLoadedNonPlayerEntitiesInSlot(
            ServerLevel dungeonLevel,
            BlockPos slotOrigin,
            String reason
    ) {
        if (dungeonLevel == null || slotOrigin == null) {
            return;
        }
        int safetyRadius = getSlotEntitySafetyRadius();
        List<Entity> entitiesToRemove = new ArrayList<>();
        for (Entity entity : dungeonLevel.getAllEntities()) {
            if (entity == null || entity.isRemoved() || entity instanceof Player) {
                continue;
            }
            if (isInsideSlotSafetyRegion( entity.blockPosition(), slotOrigin, safetyRadius )) {
                continue;
            }
            entitiesToRemove.add( entity );
        }
        String sweepReason =
                reason == null
                        || reason.isBlank()
                        ? "unspecified"
                        : reason;
        if (!entitiesToRemove.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Slot safety sweep '"
                            + sweepReason
                            + "' found "
                            + entitiesToRemove.size()
                            + " loaded stale/non-player entity/entities."
            );
            DebugLog.log( "[CobblemonNML] Slot origin: " + slotOrigin + " | safety radius: " + safetyRadius );
        }
        for (Entity entity : entitiesToRemove) {
            DebugLog.log(
                    "[CobblemonNML] Slot safety sweep removing "
                            + entity.getType()
                            + " UUID="
                            + entity.getUUID()
                            + " position="
                            + entity.blockPosition()
            );
            entity.discard();
        }
        int survivors = 0;
        if (VERIFY_ENTITY_SWEEPS) {
            for (Entity entity : dungeonLevel.getAllEntities()) {
                if (entity == null || entity.isRemoved() || entity instanceof Player) {
                    continue;
                }
                if (isInsideSlotSafetyRegion( entity.blockPosition(), slotOrigin, safetyRadius )) {
                    continue;
                }
                survivors++;
                DebugLog.log(
                        "[CobblemonNML] WARNING: Entity survived slot safety sweep: "
                                + entity.getType()
                                + " UUID="
                                + entity.getUUID()
                                + " position="
                                + entity.blockPosition()
                );
            }
        }
        DebugLog.log(
                "[CobblemonNML] Slot safety sweep complete. "
                        + "Reason="
                        + sweepReason
                        + ", removed="
                        + entitiesToRemove.size()
                        + ( VERIFY_ENTITY_SWEEPS ? ", survivors=" + survivors : "" )
        );
    }
    // SLOT ENTITY SAFETY RADIUS
    private static int getSlotEntitySafetyRadius() {
        int largestGenerationDistance = 0;
        for (DungeonTier tier : DungeonTier.values()) {
            if (tier == null) {
                continue;
            }
            largestGenerationDistance = Math.max( largestGenerationDistance, tier.getMaxDistance() );
        }
        return largestGenerationDistance
                + SLOT_ENTITY_SAFETY_MARGIN;
    }
    // INSIDE SLOT SAFETY REGION
    private static boolean isInsideSlotSafetyRegion( BlockPos position, BlockPos slotOrigin, int safetyRadius ) {
        if (position == null || slotOrigin == null || safetyRadius < 0) {
            return true;
        }
        long dx =
                (long) position.getX()
                        - slotOrigin.getX();
        long dz =
                (long) position.getZ()
                        - slotOrigin.getZ();
        return Math.abs( dx ) > safetyRadius
                || Math.abs( dz ) > safetyRadius;
    }
    // GENERATION STATE
    public static boolean isGenerating() {

        /*
         * Generation is still active while completed rooms are being initialized across ticks. Keeping this true also
         * prevents reset work or another generation from competing with encounter setup.
         */
        return level != null;
    }
    // GET SLOT BOUNDS
    public static List<BoundingBox> getBoundsForSlot(DungeonSlotManager.Slot slot) {
        return new ArrayList<>(getMutableBounds(slot));
    }
    // CLEAR SLOT BOUNDS
    public static void clearBoundsForSlot(DungeonSlotManager.Slot slot) {
        getMutableBounds(slot).clear();
    }
    // INTERNAL SLOT BOUNDS
    private static List<BoundingBox> getMutableBounds( DungeonSlotManager.Slot slot ) {
        return switch (slot) {
            case A -> SLOT_A_BOUNDS;
            case B -> SLOT_B_BOUNDS;
            case C -> SLOT_C_BOUNDS;
            case D -> SLOT_D_BOUNDS;
        };
    }
}
