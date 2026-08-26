package net.epiac9.cobblemonnml.overworld.village.routing;

import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageEntrance;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.VillageStructureInstance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

public final class VillageEntrancePairSelector {
    private final VillagePathfinder pathfinder;

    public VillageEntrancePairSelector() {
        this(
                new VillagePathfinder()
        );
    }

    public VillageEntrancePairSelector(
            VillagePathfinder pathfinder
    ) {
        if (pathfinder == null) {
            throw new IllegalArgumentException(
                    "pathfinder cannot be null."
            );
        }

        this.pathfinder =
                pathfinder;
    }

    public Optional<VillageRoute> findBestRoute(
            ServerLevel level,
            VillageStructureInstance source,
            VillageStructureInstance destination,
            ChunkPos sourceChunk,
            VillageNetworkSavedData networkData
    ) {
        if (level == null
                || source == null
                || destination == null
                || sourceChunk == null
                || networkData == null) {
            return Optional.empty();
        }

        VillageRoute best =
                null;

        for (VillageEntrance sourceEntrance : source.entrances()) {
            if (!facesOutside(
                    source.bounds(),
                    sourceEntrance
            )) {
                continue;
            }

            for (VillageEntrance destinationEntrance : destination.entrances()) {
                if (!facesOutside(
                        destination.bounds(),
                        destinationEntrance
                )) {
                    continue;
                }

                Optional<VillagePathfinder.PathResult> result =
                        pathfinder.findRoute(
                                level,
                                sourceEntrance,
                                destinationEntrance,
                                sourceChunk,
                                networkData
                        );

                if (result.isEmpty()
                        || !result.get().isUsable()) {
                    continue;
                }

                VillagePathfinder.PathResult path =
                        result.get();

                VillageRoute candidate =
                        new VillageRoute(
                                VillageConnectionKey.of(
                                        source.id(),
                                        destination.id()
                                ),
                                source.id(),
                                destination.id(),
                                sourceEntrance,
                                destinationEntrance,
                                path.nodes(),
                                path.totalCost(),
                                path.expandedNodes()
                        );

                if (best == null
                        || candidate.totalCost()
                        < best.totalCost()) {
                    best =
                            candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static boolean facesOutside(
            BoundingBox bounds,
            VillageEntrance entrance
    ) {
        BlockPos outside =
                entrance
                        .pos()
                        .relative(
                                entrance.facing()
                        );

        return !contains(
                bounds,
                outside
        );
    }

    private static boolean contains(
            BoundingBox bounds,
            BlockPos pos
    ) {
        return pos.getX() >= bounds.minX()
                && pos.getX() <= bounds.maxX()
                && pos.getY() >= bounds.minY()
                && pos.getY() <= bounds.maxY()
                && pos.getZ() >= bounds.minZ()
                && pos.getZ() <= bounds.maxZ();
    }
}
