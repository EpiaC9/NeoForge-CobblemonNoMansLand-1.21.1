package net.epiac9.cobblemonnml.dimension.tier;

import net.epiac9.cobblemonnml.Config;

public enum DungeonTier {
    TIER_1( "dungeon/tier1/start", 4, 192, Config.TIER_1 ),
    TIER_2( "dungeon/tier2/start", 6, 320, Config.TIER_2 ),
    TIER_3( "dungeon/tier3/start", 8, 448, Config.TIER_3 ),
    TIER_4( "dungeon/tier4/start", 10, 640, Config.TIER_4 );
    private final String startPool;
    private final int maxDepth;
    private final int maxDistance;
    private final Config.TierConfig config;
    DungeonTier( String startPool, int maxDepth, int maxDistance, Config.TierConfig config ) {
        this.startPool = startPool;
        this.maxDepth = maxDepth;
        this.maxDistance = maxDistance;
        this.config = config;
    }
    public String getStartPool() {
        return startPool;
    }
    public int getMaxDepth() {
        return maxDepth;
    }
    public int getMaxDistance() {
        return maxDistance;
    }
    public int getTimerSeconds() {
        return config
                .timerSeconds()
                .get();
    }
    public String getDisplayName() {
        return switch (this) {
            case TIER_1 -> "Tier 1";
            case TIER_2 -> "Tier 2";
            case TIER_3 -> "Tier 3";
            case TIER_4 -> "Tier 4";
        };
    }
}
