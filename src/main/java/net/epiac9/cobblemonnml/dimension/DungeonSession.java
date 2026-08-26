package net.epiac9.cobblemonnml.dimension;

import net.epiac9.cobblemonnml.dimension.gameplay.DungeonBlockProtection;
import net.epiac9.cobblemonnml.dimension.gameplay.DungeonLootTracker;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.UUID;

public final class DungeonSession {
    // ACTIVE THEME
    private static DungeonTheme activeTheme = null;
    // ACTIVE TIER
    private static DungeonTier activeTier = null;
    // OWNER
    private static UUID dungeonOwnerUUID = null;
    // SESSION IDENTITY
    private static UUID sessionId = null;
    // INVALIDATED
    private static boolean invalidated = false;

    private DungeonSession() {
    }
    // START
    public static void start(DungeonTheme theme, DungeonTier tier, UUID ownerUUID) {
        if (theme == null || tier == null) {
            DebugLog.log("[CobblemonNML] Cannot start dungeon session without both a resolved theme and tier.");
            return;
        }

        DungeonBlockProtection.clearPlacedBlocks();
        activeTheme = theme;
        activeTier = tier;
        dungeonOwnerUUID = ownerUUID;
        sessionId = UUID.randomUUID();
        invalidated = false;

        DebugLog.log("Dungeon session started.");
        DebugLog.log("Theme: " + theme.getDisplayName());
        DebugLog.log("Tier: " + tier.getDisplayName());
        DebugLog.log("Owner: " + (ownerUUID != null ? ownerUUID : "NONE"));
        DebugLog.log("Session: " + sessionId);
    }
    // END
    public static void end() {
        if (activeTheme != null || activeTier != null) {
            DebugLog.log("Dungeon session ended.");
        }

        DungeonBlockProtection.clearPlacedBlocks();
        DungeonLootTracker.clearAll();
        activeTheme = null;
        activeTier = null;
        dungeonOwnerUUID = null;
        sessionId = null;
        invalidated = true;
    }

    public static DungeonTheme getTheme() {
        return activeTheme;
    }

    public static DungeonTier getTier() {
        return activeTier;
    }

    public static UUID getOwnerUUID() {
        return dungeonOwnerUUID;
    }

    public static UUID getSessionId() {
        return sessionId;
    }

    public static boolean isActive() {
        return activeTheme != null && activeTier != null && sessionId != null;
    }

    public static boolean isInvalidated() {
        return invalidated;
    }
}
