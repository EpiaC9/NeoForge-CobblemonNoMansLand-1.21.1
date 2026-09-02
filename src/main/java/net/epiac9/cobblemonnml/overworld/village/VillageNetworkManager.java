package net.epiac9.cobblemonnml.overworld.village;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.data.VillageDataManager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class VillageNetworkManager {
    private VillageNetworkManager() {
    }

    public static VillageStructureInstance registerPlacedStructure(
            ServerLevel level,
            ResourceLocation structureId,
            BlockPos origin,
            BoundingBox bounds
    ) {
        if (level == null
                || structureId == null
                || origin == null
                || bounds == null) {
            return null;
        }

        if (VillageDataManager.getStructure(structureId).isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Village network ignored unregistered structure: "
                            + structureId
            );
            return null;
        }

        List<VillageEntrance> entrances =
                VillageEntranceScanner.captureAndRemove(
                        level,
                        bounds
                );

        if (entrances.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Village structure "
                            + structureId
                            + " at "
                            + origin
                            + " has no village entrance marker."
            );
            return null;
        }

        UUID instanceId =
                createStableInstanceId(
                        level,
                        structureId,
                        origin
                );

        VillageStructureInstance instance =
                new VillageStructureInstance(
                        instanceId,
                        structureId,
                        bounds,
                        entrances
                );

        VillageNetworkSavedData data =
                VillageNetworkSavedData.get(level);

        boolean added = data.registerStructure(instance);

        if (!added) {
            VillageStructureInstance existing = data.getStructure(instanceId);
            return existing;
        }

        DebugLog.log(
                "[CobblemonNML] Registered village structure "
                        + structureId
                        + " at "
                        + origin
                        + " with "
                        + entrances.size()
                        + " entrance(s)."
        );

        VillageGenerationQueue.enqueueNeighborScan(
                level.getServer(),
                instance.id()
        );

        return instance;
    }

    private static UUID createStableInstanceId(
            ServerLevel level,
            ResourceLocation structureId,
            BlockPos origin
    ) {
        String key =
                level.dimension().location()
                        + "|"
                        + structureId
                        + "|"
                        + origin.getX()
                        + ","
                        + origin.getY()
                        + ","
                        + origin.getZ();

        return UUID.nameUUIDFromBytes(
                key.getBytes(StandardCharsets.UTF_8)
        );
    }
}
