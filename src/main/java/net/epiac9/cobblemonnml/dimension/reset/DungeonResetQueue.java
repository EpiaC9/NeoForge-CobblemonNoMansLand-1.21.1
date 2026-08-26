package net.epiac9.cobblemonnml.dimension.reset;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.generation.DungeonGenerationQueue;
import net.epiac9.cobblemonnml.dimension.DungeonSlotManager;
import net.epiac9.cobblemonnml.dimension.network.DungeonCleanupToastPayload;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class DungeonResetQueue {
    // RESET BLOCK
    private static final BlockState BEDROCK_STATE = Blocks.BEDROCK.defaultBlockState();
    // POST-RESET ENTITY SAFETY
    /*
     * Normal reset jobs remember the exact slot origin that was active when cleanup started. Once block restoration has
     * touched all old dungeon chunks, we run the broad slot-wide loaded-entity sweep one more time.
     * Pass 9 persists the slot origin independently for every pending reset. The expanded reset-box sweep remains only as
     * a defensive fallback for malformed/very old saved state.
     */
    private static final int RESUMED_ENTITY_SWEEP_MARGIN = 64;
    // RESET START DELAY
    /*
     * When a dungeon closes, all gameplay cleanup happens immediately:
     * - trainers
     * - raids
     * - portal
     * - temporary entities
     * Physical block restoration waits for 5 seconds.
     * This is especially useful for singleplayer:
     * if the player exits the dungeon and then immediately leaves the world, we avoid dirtying hundreds of dungeon
     * chunks right before Minecraft performs its final save.
     */
    private static final int NEW_RESET_GRACE_TICKS = 100;

    /*
     * Resumed jobs only need a short delay after the world loads so the server can finish its initial startup work.
     */
    private static final int RESUMED_RESET_GRACE_TICKS = 20;
    // RESET FAIL-SAFE
    /*
     * If restoration of one X slice repeatedly throws an exception,
     * do not allow that broken slice to permanently lock the entire dungeon cleanup system.
     */
    private static final int MAX_SLICE_FAILURES = 5;
    // RESET JOBS
    private static final Map<DungeonSlotManager.Slot, ResetJob> JOBS = new EnumMap<>( DungeonSlotManager.Slot.class );
    // SAVED RESET RESUME CHECK
    private static MinecraftServer resumeCheckedServer = null;
    private static boolean savedResetResumeChecked = false;
    // START RESET
    public static void start( ServerLevel level, DungeonSlotManager.Slot slot, Iterable<BoundingBox> bounds ) {
        if (level == null || slot == null || bounds == null) {
            return;
        }
        // ALREADY RESETTING
        if (JOBS.containsKey(slot)) {
            return;
        }
        // NORMALIZE BOUNDS
        /*
         * Jigsaw pieces can overlap.
         * Remove:
         * - exact duplicate boxes
         * - boxes completely contained inside another box
         * Partial overlaps are still allowed. Those are handled cheaply later because fillSlice() skips blocks that
         * have already been restored to bedrock.
         */
        List<BoundingBox> boxes = normalizeBounds( bounds );
        // NO BOUNDS
        if (boxes.isEmpty()) {
            DungeonGenerationQueue
                    .clearBoundsForSlot( slot );
            DebugLog.log( "No dungeon bounds to reset for slot " + slot );
            return;
        }
        DebugLog.log( "Prepared " + boxes.size() + " normalized dungeon reset bound(s) for slot " + slot );
        // CAPTURE SLOT ORIGIN
        /*
         * Slot origins are deterministic and belong to the slot, not to whichever slot happens to be selected later.
         * Persist the exact origin with this reset so a restarted server can perform the same broad post-reset entity
         * sweep as the original in-memory job.
         */
        BlockPos slotOrigin = DungeonSlotManager.getOrigin( slot );
        if (slotOrigin != null) {
            slotOrigin = slotOrigin.immutable();
        }
        // SAVE RESET STATE
        MinecraftServer server = level.getServer();
        DungeonResetSavedData savedData;
        savedData =
                DungeonResetSavedData
                        .get( server );
        savedData.beginReset( slot, boxes, slotOrigin );

        /*
         * This reset already exists in memory.
         * Do not perform another SavedData resume lookup during this server session.
         */
        resumeCheckedServer = server;
        savedResetResumeChecked = true;
        // CREATE RESET JOB
        ResetJob job =
                new ResetJob(
                        level,
                        slot,
                        boxes,
                        0,
                        boxes
                                .getFirst()
                                .minX(),
                        savedData,
                        NEW_RESET_GRACE_TICKS,
                        slotOrigin
                );
        JOBS.put( slot, job );
        DebugLog.log(
                "Background reset queued for slot "
                        + slot
                        + ". Physical reset begins in "
                        + ( NEW_RESET_GRACE_TICKS / 20 )
                        + " seconds."
        );
        sendCleanupToast( level, slot, "STARTED" );
    }
    // NORMALIZE RESET BOUNDS
    private static List<BoundingBox> normalizeBounds( Iterable<BoundingBox> bounds ) {
        List<BoundingBox> normalized = new ArrayList<>();
        for (BoundingBox incoming : bounds) {
            if (incoming == null) {
                continue;
            }
            // ALREADY COVERED?
            boolean covered = false;
            for (BoundingBox existing : normalized) {
                if (contains( existing, incoming )) {
                    covered = true;
                    break;
                }
            }
            if (covered) {
                continue;
            }
            // REMOVE BOXES COVERED BY THIS NEW BOX
            normalized.removeIf( existing -> contains( incoming, existing ) );
            normalized.add( incoming );
        }
        return normalized;
    }
    // BOUNDING BOX CONTAINS
    private static boolean contains( BoundingBox outer, BoundingBox inner ) {

        return outer.minX() <= inner.minX()
                && outer.maxX() >= inner.maxX()
                && outer.minY() <= inner.minY()
                && outer.maxY() >= inner.maxY()
                && outer.minZ() <= inner.minZ()
                && outer.maxZ() >= inner.maxZ();
    }
    // RESUME SAVED RESETS
    public static void resumeSavedReset( MinecraftServer server ) {
        if (server == null) {
            return;
        }
        // NEW SERVER INSTANCE
        if (resumeCheckedServer != server) {
            resumeCheckedServer = server;
            savedResetResumeChecked = false;
        }
        // ALREADY CHECKED
        if (savedResetResumeChecked) {
            return;
        }
        DungeonResetSavedData savedData =
                DungeonResetSavedData
                        .get( server );
        // NOTHING PENDING
        if (!savedData.hasPendingResets()) {
            savedResetResumeChecked = true;
            return;
        }
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension .DUNGEON_DIMENSION );
        if (dungeonLevel == null) {

            /*
             * The dimension has not loaded yet.
             * Leave savedResetResumeChecked false so another server tick can try again.
             */
            return;
        }
        // RESTORE EVERY PENDING SLOT
        int resumedCount = 0;
        for (DungeonSlotManager.Slot slot : savedData.getPendingSlots()) {
            if (slot == null) {
                continue;
            }
            // ALREADY RESTORED INTO MEMORY
            if (JOBS.containsKey(slot)) {
                continue;
            }
            // RESTORE SAVED BOXES
            List<BoundingBox> boxes = savedData.getBoxes( slot );
            if (boxes.isEmpty()) {
                savedData.finishReset( slot );
                continue;
            }
            int boxIndex = savedData.getCurrentBoxIndex( slot );
            // INVALID SAVED INDEX
            /*
             * currentX may legitimately be maxX + 1 if the server stopped after the last X slice of this box.
             * tickJob() will advance to the next box normally.
             */
            if (boxIndex < 0 || boxIndex >= boxes.size()) {
                savedData.finishReset( slot );
                continue;
            }
            int currentX = savedData.getCurrentX( slot );
            BoundingBox currentBox = boxes.get( boxIndex );
            if (currentX < currentBox.minX()) {
                currentX = currentBox.minX();
            }
            // RESTORE SLOT ORIGIN
            BlockPos slotOrigin = savedData.getSlotOrigin( slot );
            if (slotOrigin == null) {
                BlockPos fallbackOrigin = DungeonSlotManager.getOrigin( slot );
                if (fallbackOrigin != null) {
                    slotOrigin = fallbackOrigin.immutable();
                }
            }
            // RESTORE JOB
            ResetJob job =
                    new ResetJob(
                            dungeonLevel,
                            slot,
                            boxes,
                            boxIndex,
                            currentX,
                            savedData,
                            RESUMED_RESET_GRACE_TICKS,
                            slotOrigin
                    );
            JOBS.put( slot, job );
            resumedCount++;
            DebugLog.log(
                    "Resumed dungeon reset for slot "
                            + slot
                            + " at box "
                            + boxIndex
                            + "/"
                            + boxes.size()
                            + ", x="
                            + currentX
            );
            sendCleanupToast( dungeonLevel, slot, "STARTED" );
        }
        savedResetResumeChecked = true;
        if (resumedCount > 1) {
            DebugLog.log( "Resumed " + resumedCount + " persisted dungeon reset jobs." );
        }
    }
    // RESET TICK
    public static void tick(int slicesPerJob) {
        if (slicesPerJob <= 0) {
            return;
        }
        for (DungeonSlotManager.Slot slot : DungeonSlotManager.Slot.values()) {
            ResetJob job = JOBS.get( slot );
            if (job == null) {
                continue;
            }
            tickJob( slot, job, slicesPerJob );
        }
    }
    // TICK ONE RESET JOB
    private static void tickJob( DungeonSlotManager.Slot slot, ResetJob job, int slicesPerJob ) {
        // STARTUP / LOGOUT GRACE PERIOD
        if (job.delayTicks > 0) {
            job.delayTicks--;
            return;
        }
        int slicesDone = 0;
        while (slicesDone < slicesPerJob) {
            // ALL BOXES COMPLETE
            if (job.currentBoxIndex >= job.boxes.size()) {
                finish( slot );
                return;
            }
            BoundingBox box = job.boxes.get( job.currentBoxIndex );
            // FIX CURRENT X
            if (job.currentX < box.minX()) {
                job.currentX = box.minX();
            }
            // FINISHED CURRENT BOX
            if (job.currentX > box.maxX()) {
                job.currentBoxIndex++;
                if (job.currentBoxIndex >= job.boxes.size()) {
                    saveProgress( job );
                    finish( slot );
                    return;
                }
                BoundingBox nextBox = job.boxes.get( job.currentBoxIndex );
                job.currentX = nextBox.minX();
                saveProgress( job );
                continue;
            }
            // RESTORE CURRENT X SLICE
            try {
                fillSlice( job.level, box, job.currentX );

                /*
                 * Successful progress clears the failure count.
                 */
                job.consecutiveSliceFailures = 0;
            } catch (RuntimeException exception) {
                job.consecutiveSliceFailures++;
                DebugLog.log( "[CobblemonNML] RESET FAIL-SAFE: " + "Could not restore reset slice." );
                DebugLog.log(
                        "[CobblemonNML] Slot="
                                + slot
                                + ", box="
                                + job.currentBoxIndex
                                + "/"
                                + job.boxes.size()
                                + ", x="
                                + job.currentX
                                + ", attempt="
                                + job.consecutiveSliceFailures
                                + "/"
                                + MAX_SLICE_FAILURES
                );
                DebugLog.log(
                        "[CobblemonNML] Cause: "
                                + exception.getClass().getSimpleName()
                                + ": "
                                + exception.getMessage()
                );
                // RETRY THIS SAME SLICE ON A LATER TICK
                if (job.consecutiveSliceFailures < MAX_SLICE_FAILURES) {
                    return;
                }
                // REPEATED FAILURE
                /*
                 * Do not throw away the entire reset job.
                 * Leave the saved cursor pointing at this slice and pause the in-memory job. The reset remains persisted and can be
                 * resumed safely on the next server/world startup.
                 */
                DebugLog.log(
                        "[CobblemonNML] RESET FAIL-SAFE: "
                                + "Reset slice repeatedly failed. "
                                + "Pausing slot "
                                + slot
                                + " at x="
                                + job.currentX
                                + ". Saved reset state has been preserved."
                );
                saveProgress( job );
                JOBS.remove( slot );
                return;
            }
            job.currentX++;
            slicesDone++;
            // SAVE RESUME POSITION
            saveProgress( job );
        }
    }
    // SAVE RESET PROGRESS
    private static void saveProgress(ResetJob job) {
        if (job.savedData == null) {
            return;
        }
        job.savedData.updateProgress( job.slot, job.currentBoxIndex, job.currentX );
    }
    // FILL ONE X SLICE
    private static void fillSlice( ServerLevel level, BoundingBox box, int x ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                pos.set( x, y, z );
                // ALREADY CLEAN
                /*
                 * Overlapping structure pieces can cause the same position to occur in several bounding boxes.
                 * Never call setBlock again if the first pass has already restored this block to bedrock.
                 * This avoids:
                 * - unnecessary block changes
                 * - unnecessary chunk dirtying
                 * - unnecessary client updates
                 * - unnecessary final-save work
                 */
                if (level.getBlockState(pos).is(Blocks.BEDROCK)) {
                    continue;
                }
                level.setBlock( pos, BEDROCK_STATE, 2 );
            }
        }
    }
    // FINISH RESET
    private static void finish(DungeonSlotManager.Slot slot) {
        ResetJob job = JOBS.remove( slot );
        if (job == null) {
            return;
        }
        // POST-RESET ENTITY SAFETY SWEEP
        /*
         * This is intentionally AFTER all block slices finished.
         * The block reset may load a chunk containing a Pokemon, NPC, dropped item, projectile, or other entity that was
         * not loaded during the initial dungeon cleanup. That is exactly how a stale raid Pokemon can survive the first
         * sweep and reappear later.
         * Normal and resumed jobs both use the same whole-slot loaded-entity sweep. Pass 9 persists the slot origin per
         * reset job, so a server restart no longer weakens this cleanup step. The expanded reset-box sweep below remains
         * only as a defensive fallback.
         */
        if (job.slotOrigin != null) {
            DungeonGenerationQueue
                    .sweepLoadedNonPlayerEntitiesInSlot( job.level, job.slotOrigin, "post-reset slot cleanup" );
        } else {
            sweepLoadedNonPlayerEntitiesInResetBounds( job.level, job.boxes );
        }
        // CLEAR GENERATION BOUNDS
        DungeonGenerationQueue
                .clearBoundsForSlot( slot );
        // CLEAR SAVED RESET STATE
        if (job.savedData != null) {
            job.savedData.finishReset( slot );
        }
        DebugLog.log( "Background reset finished for slot " + slot );
        sendCleanupToast( job.level, slot, "FINISHED" );
    }
    // POST-RESET FALLBACK ENTITY SWEEP
    /*
     * Defensive fallback used only if a reset job has no usable slot origin. Every reset box has just been physically
     * touched, so this pass removes loaded non-player entities inside or near those boxes without force-loading additional
     * chunks.
     */
    private static void sweepLoadedNonPlayerEntitiesInResetBounds( ServerLevel level, List<BoundingBox> boxes ) {
        if (level == null || boxes == null || boxes.isEmpty()) {
            return;
        }
        int safeMargin = Math.max( 0, DungeonResetQueue.RESUMED_ENTITY_SWEEP_MARGIN );
        List<Entity> entitiesToRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity == null || entity.isRemoved() || entity instanceof Player) {
                continue;
            }
            if (isInsideAnyExpandedResetBound( entity.blockPosition(), boxes, safeMargin )) {
                continue;
            }
            entitiesToRemove.add( entity );
        }
        String sweepReason = "post-reset resumed fallback";
        if (!entitiesToRemove.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Reset-bound entity sweep '"
                            + sweepReason
                            + "' found "
                            + entitiesToRemove.size()
                            + " loaded stale/non-player entity/entities."
            );
        }
        for (Entity entity : entitiesToRemove) {
            DebugLog.log(
                    "[CobblemonNML] Reset-bound entity sweep removing "
                            + entity.getType()
                            + " UUID="
                            + entity.getUUID()
                            + " position="
                            + entity.blockPosition()
            );
            entity.discard();
        }
        int survivors = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity == null || entity.isRemoved() || entity instanceof Player) {
                continue;
            }
            if (isInsideAnyExpandedResetBound( entity.blockPosition(), boxes, safeMargin )) {
                continue;
            }
            survivors++;
            DebugLog.log(
                    "[CobblemonNML] WARNING: Entity survived reset-bound sweep: "
                            + entity.getType()
                            + " UUID="
                            + entity.getUUID()
                            + " position="
                            + entity.blockPosition()
            );
        }
        DebugLog.log(
                "[CobblemonNML] Reset-bound entity sweep complete. "
                        + "Reason="
                        + sweepReason
                        + ", margin="
                        + safeMargin
                        + ", removed="
                        + entitiesToRemove.size()
                        + ", survivors="
                        + survivors
        );
    }
    // POSITION INSIDE ANY EXPANDED RESET BOUND
    private static boolean isInsideAnyExpandedResetBound( BlockPos position, List<BoundingBox> boxes, int margin ) {
        if (position == null || boxes == null || boxes.isEmpty()) {
            return true;
        }
        long x = position.getX();
        long y = position.getY();
        long z = position.getZ();
        long safeMargin = Math.max( 0, margin );
        for (BoundingBox box : boxes) {
            if (box == null) {
                continue;
            }
            if (x < (long) box.minX() - safeMargin
                    || x > (long) box.maxX() + safeMargin
                    || y < (long) box.minY() - safeMargin
                    || y > (long) box.maxY() + safeMargin
                    || z < (long) box.minZ() - safeMargin
                    || z > (long) box.maxZ() + safeMargin) {
                continue;
            }
            return false;
        }
        return true;
    }
    // PAUSE ALL RESET JOBS
    public static void pauseAll() {
        if (!JOBS.isEmpty()) {

            /*
             * Progress is normally saved after every completed X slice, but write the current cursor once more
             * before dropping the in-memory jobs.
             */
            for (ResetJob job : JOBS.values()) {
                saveProgress( job );
            }
            DebugLog.log( "Pausing " + JOBS.size() + " dungeon reset job(s)." );
            JOBS.clear();
        }

        /*
         * Integrated servers may stop and restart inside the same Minecraft client JVM.
         */
        resumeCheckedServer = null;
        savedResetResumeChecked = false;
    }
    // RESETTING?
    public static boolean isResetting(DungeonSlotManager.Slot slot) {
        return JOBS.containsKey( slot );
    }
    // RESET JOB
    private static final class ResetJob {
        private final ServerLevel level;
        private final DungeonSlotManager.Slot slot;
        private final List<BoundingBox> boxes;
        private int currentBoxIndex;
        private int currentX;
        private final DungeonResetSavedData savedData;
        private int delayTicks;
        private int consecutiveSliceFailures = 0;

        /*
         * Exact logical slot origin for the broad post-reset loaded-entity sweep. Pass 9 persists this per slot, so
         * resumed jobs retain the same cleanup coverage.
         */
        private final BlockPos slotOrigin;
        private ResetJob(
                ServerLevel level,
                DungeonSlotManager.Slot slot,
                List<BoundingBox> boxes,
                int currentBoxIndex,
                int currentX,
                DungeonResetSavedData savedData,
                int delayTicks,
                BlockPos slotOrigin
        ) {
            this.level = level;
            this.slot = slot;
            this.boxes = new ArrayList<>( boxes );
            this.currentBoxIndex = currentBoxIndex;
            this.currentX = currentX;
            this.savedData = savedData;
            this.delayTicks = Math.max( 0, delayTicks );
            this.slotOrigin =
                    slotOrigin == null
                            ? null
                            : slotOrigin.immutable();
        }
    }
    // SEND CLEANUP TOAST
    private static void sendCleanupToast( ServerLevel level, DungeonSlotManager.Slot slot, String status ) {
        MinecraftServer server = level.getServer();
        DungeonCleanupToastPayload payload = new DungeonCleanupToastPayload( slot.name(), status );
        for (ServerPlayer player : server .getPlayerList() .getPlayers()) {
            PacketDistributor.sendToPlayer( player, payload );
        }
    }
}
