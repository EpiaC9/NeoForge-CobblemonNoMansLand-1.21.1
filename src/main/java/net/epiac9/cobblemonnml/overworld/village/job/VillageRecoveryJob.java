package net.epiac9.cobblemonnml.overworld.village.job;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageGenerationQueue;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageStructureInstance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.UUID;

public final class VillageRecoveryJob implements VillageJob {
    /*
     * Fixed 3x3 routing neighborhood, centered on the source
     * structure's representative chunk.
     */
    private static final int[] OFFSETS = {
            -1, 0, 1
    };

    private final VillageConnectionKey connectionKey;
    private final UUID sourceStructureId;
    private final UUID destinationStructureId;
    private final ChunkPos sourceChunk;

    private int nextChunkIndex;
    private boolean finished;

    public VillageRecoveryJob(
            VillageConnectionKey connectionKey,
            UUID sourceStructureId,
            UUID destinationStructureId,
            ChunkPos sourceChunk
    ) {
        this.connectionKey =
                Objects.requireNonNull(
                        connectionKey,
                        "connectionKey"
                );

        this.sourceStructureId =
                Objects.requireNonNull(
                        sourceStructureId,
                        "sourceStructureId"
                );

        this.destinationStructureId =
                Objects.requireNonNull(
                        destinationStructureId,
                        "destinationStructureId"
                );

        this.sourceChunk =
                Objects.requireNonNull(
                        sourceChunk,
                        "sourceChunk"
                );
    }

    public VillageConnectionKey connectionKey() {
        return connectionKey;
    }

    public UUID sourceStructureId() {
        return sourceStructureId;
    }

    public UUID destinationStructureId() {
        return destinationStructureId;
    }

    public ChunkPos sourceChunk() {
        return sourceChunk;
    }

    @Override
    public boolean processOneUnit(
            ServerLevel level
    ) {
        if (finished) {
            return true;
        }

        VillageNetworkSavedData data =
                VillageNetworkSavedData.get(
                        level
                );

        if (data.hasConnection(
                sourceStructureId,
                destinationStructureId
        )) {
            finished = true;
            return true;
        }

        VillageStructureInstance source =
                data.getStructure(
                        sourceStructureId
                );

        VillageStructureInstance destination =
                data.getStructure(
                        destinationStructureId
                );

        if (source == null
                || destination == null) {
            data.removeConnectionState(
                    connectionKey
            );

            finished = true;

            DebugLog.log(
                    "[CobblemonNML] Cancelled village recovery because one structure no longer exists: "
                            + sourceStructureId
                            + " <-> "
                            + destinationStructureId
                            + "."
            );

            return true;
        }

        if (nextChunkIndex < 9) {
            int offsetX =
                    OFFSETS[
                            nextChunkIndex % 3
                    ];

            int offsetZ =
                    OFFSETS[
                            nextChunkIndex / 3
                    ];

            int chunkX =
                    sourceChunk.x
                            + offsetX;

            int chunkZ =
                    sourceChunk.z
                            + offsetZ;

            /*
             * getChunk() intentionally performs the actual load here.
             * Only one chunk is requested per work unit, keeping the
             * recovery load progressive instead of forcing all nine
             * chunks in a single server tick.
             */
            level.getChunk(
                    chunkX,
                    chunkZ
            );

            nextChunkIndex++;

            return false;
        }

        /*
         * All nine chunks are now available. Re-plan the high-level
         * connection from fresh terrain data instead of restoring any
         * stale transient A* state.
         */
        VillageGenerationQueue.enqueueRoute(
                level.getServer(),
                new VillageRouteJob(
                        connectionKey,
                        sourceStructureId,
                        destinationStructureId,
                        sourceChunk
                )
        );

        finished = true;

        DebugLog.log(
                "[CobblemonNML] Village recovery neighborhood ready for "
                        + sourceStructureId
                        + " <-> "
                        + destinationStructureId
                        + "; requeued route after loading 3x3 chunks."
        );

        return true;
    }
}
