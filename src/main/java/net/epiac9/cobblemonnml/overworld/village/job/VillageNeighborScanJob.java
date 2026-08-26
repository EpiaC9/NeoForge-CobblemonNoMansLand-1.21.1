package net.epiac9.cobblemonnml.overworld.village.job;

import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageGenerationQueue;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageStructureInstance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.UUID;

public final class VillageNeighborScanJob implements VillageJob {
    private final UUID sourceStructureId;

    private List<VillageStructureInstance> candidates;
    private VillageStructureInstance source;
    private ChunkPos sourceChunk;
    private int nextCandidateIndex;

    public VillageNeighborScanJob(UUID sourceStructureId) {
        if (sourceStructureId == null) {
            throw new IllegalArgumentException(
                    "sourceStructureId cannot be null."
            );
        }

        this.sourceStructureId = sourceStructureId;
    }

    public UUID sourceStructureId() {
        return sourceStructureId;
    }

    @Override
    public boolean processOneUnit(ServerLevel level) {
        VillageNetworkSavedData data =
                VillageNetworkSavedData.get( level );

        if (!initialize(data)) {
            return true;
        }

        if (nextCandidateIndex >= candidates.size()) {
            return true;
        }

        VillageStructureInstance candidate =
                candidates.get(nextCandidateIndex++);

        inspectCandidate(
                level,
                data,
                candidate
        );

        return nextCandidateIndex >= candidates.size();
    }

    private boolean initialize(VillageNetworkSavedData data) {
        if (source != null) {
            return true;
        }

        source = data.getStructure( sourceStructureId );

        if (source == null) {
            return false;
        }

        sourceChunk =
                getRepresentativeChunk(source);

        candidates =
                data.getStructures();

        nextCandidateIndex = 0;

        return true;
    }

    private void inspectCandidate(
            ServerLevel level,
            VillageNetworkSavedData data,
            VillageStructureInstance candidate
    ) {
        if (candidate == null) {
            return;
        }

        if (candidate.id().equals(sourceStructureId)) {
            return;
        }

        if (!isInsideFixedNeighborhood(
                sourceChunk,
                getRepresentativeChunk(candidate)
        )) {
            return;
        }

        if (data.hasConnection(
                sourceStructureId,
                candidate.id()
        )) {
            return;
        }

        VillageConnectionKey connectionKey =
                VillageConnectionKey.of(
                        sourceStructureId,
                        candidate.id()
                );

        VillageGenerationQueue.enqueueRoute(
                level.getServer(),
                new VillageRouteJob(
                        connectionKey,
                        sourceStructureId,
                        candidate.id(),
                        sourceChunk
                )
        );
    }

    static boolean isInsideFixedNeighborhood(
            ChunkPos sourceChunk,
            ChunkPos candidateChunk
    ) {
        if (sourceChunk == null || candidateChunk == null) {
            return false;
        }

        return Math.abs(
                candidateChunk.x - sourceChunk.x
        ) <= 1
                && Math.abs(
                candidateChunk.z - sourceChunk.z
        ) <= 1;
    }

    static ChunkPos getRepresentativeChunk(
            VillageStructureInstance structure
    ) {
        BoundingBox bounds = structure.bounds();

        int centerX =
                bounds.minX()
                        + (bounds.maxX() - bounds.minX()) / 2;

        int centerZ =
                bounds.minZ()
                        + (bounds.maxZ() - bounds.minZ()) / 2;

        return new ChunkPos(
                centerX >> 4,
                centerZ >> 4
        );
    }
}
