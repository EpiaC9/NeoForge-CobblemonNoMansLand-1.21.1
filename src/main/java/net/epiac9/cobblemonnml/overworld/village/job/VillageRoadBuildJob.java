package net.epiac9.cobblemonnml.overworld.village.job;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageNetworkSavedData;
import net.epiac9.cobblemonnml.overworld.village.road.VillageRoadBuilder;
import net.epiac9.cobblemonnml.overworld.village.routing.VillageRoute;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

public final class VillageRoadBuildJob implements VillageJob {
    private final VillageRoute route;

    private int nextNodeIndex;
    private int changedBlocks;
    private int unchangedBlocks;
    private boolean hasUnresolvedWaterSegments;
    private boolean hasHeightGaps;
    private boolean finished;

    public VillageRoadBuildJob(
            VillageRoute route
    ) {
        this.route =
                Objects.requireNonNull(
                        route,
                        "route"
                );
    }

    public VillageConnectionKey connectionKey() {
        return route.connectionKey();
    }

    public VillageRoute route() {
        return route;
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

        data.markConnectionBuilding(
                route.connectionKey()
        );

        if (data.hasConnection(
                route.sourceStructureId(),
                route.destinationStructureId()
        )) {
            finished = true;
            return true;
        }

        if (nextNodeIndex
                >= route.nodes().size()) {
            finish(
                    level,
                    data
            );
            return true;
        }

        VillageRoadBuilder.NodeBuildResult result =
                VillageRoadBuilder.buildGroundNode(
                        level,
                        route,
                        nextNodeIndex,
                        data
                );

        changedBlocks +=
                result.changedBlocks();

        unchangedBlocks +=
                result.unchangedBlocks();

        if (result.waterPending()) {
            hasUnresolvedWaterSegments = true;
        }

        if (result.heightGapPending()) {
            hasHeightGaps = true;
        }

        nextNodeIndex++;

        if (nextNodeIndex
                >= route.nodes().size()) {
            finish(
                    level,
                    data
            );
            return true;
        }

        return false;
    }

    private void finish(
            ServerLevel level,
            VillageNetworkSavedData data
    ) {
        if (finished) {
            return;
        }

        finished = true;

        if (hasHeightGaps) {
            data.markConnectionDeferred(
                    route.connectionKey()
            );

            DebugLog.log(
                    "[CobblemonNML] Partial village road build finished for "
                            + route.sourceStructureId()
                            + " <-> "
                            + route.destinationStructureId()
                            + ": changed="
                            + changedBlocks
                            + ", unchanged="
                            + unchangedBlocks
                            + ", height gap(s) remain disconnected"
                            + (hasUnresolvedWaterSegments
                                    ? " and one or more bridge cells could not be placed."
                                    : ".")
                            + " Connection intentionally left incomplete."
            );
            return;
        }

        if (hasUnresolvedWaterSegments) {
            data.markConnectionDeferred(
                    route.connectionKey()
            );

            DebugLog.log(
                    "[CobblemonNML] Village road/bridge build finished for "
                            + route.sourceStructureId()
                            + " <-> "
                            + route.destinationStructureId()
                            + ": changed="
                            + changedBlocks
                            + ", unchanged="
                            + unchangedBlocks
                            + ", one or more bridge cells could not be placed. "
                            + "Connection intentionally left incomplete."
            );
            return;
        }

        boolean completed =
                data.completeConnection(
                        route.sourceStructureId(),
                        route.destinationStructureId()
                );

        DebugLog.log(
                "[CobblemonNML] Village road/bridge build finished for "
                        + route.sourceStructureId()
                        + " <-> "
                        + route.destinationStructureId()
                        + ": changed="
                        + changedBlocks
                        + ", unchanged="
                        + unchangedBlocks
                        + ", connectionCompleted="
                        + completed
                        + "."
        );
    }
}
