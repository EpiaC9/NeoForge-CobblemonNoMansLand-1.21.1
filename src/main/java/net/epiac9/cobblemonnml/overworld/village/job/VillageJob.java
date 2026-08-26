package net.epiac9.cobblemonnml.overworld.village.job;

import net.minecraft.server.level.ServerLevel;

public interface VillageJob {
    /**
     * Processes one bounded work unit.
     *
     * @return true when this job is complete and can be removed.
     */
    boolean processOneUnit(ServerLevel level);
}
