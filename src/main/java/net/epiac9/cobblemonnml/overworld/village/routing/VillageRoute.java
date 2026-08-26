package net.epiac9.cobblemonnml.overworld.village.routing;

import net.epiac9.cobblemonnml.overworld.village.VillageConnectionKey;
import net.epiac9.cobblemonnml.overworld.village.VillageEntrance;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VillageRoute(
        VillageConnectionKey connectionKey,
        UUID sourceStructureId,
        UUID destinationStructureId,
        VillageEntrance sourceEntrance,
        VillageEntrance destinationEntrance,
        List<VillageRouteNode> nodes,
        int totalCost,
        int expandedNodes
) {
    public VillageRoute {
        Objects.requireNonNull( connectionKey, "connectionKey" );
        Objects.requireNonNull( sourceStructureId, "sourceStructureId" );
        Objects.requireNonNull( destinationStructureId, "destinationStructureId" );
        Objects.requireNonNull( sourceEntrance, "sourceEntrance" );
        Objects.requireNonNull( destinationEntrance, "destinationEntrance" );
        nodes = List.copyOf( Objects.requireNonNull(nodes, "nodes") );

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Village route must contain at least one node."
            );
        }

        if (totalCost < 0 || expandedNodes < 0) {
            throw new IllegalArgumentException(
                    "Village route cost and expanded-node count cannot be negative."
            );
        }
    }
}
