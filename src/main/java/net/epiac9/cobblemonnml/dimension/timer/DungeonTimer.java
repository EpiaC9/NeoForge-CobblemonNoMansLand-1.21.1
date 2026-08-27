package net.epiac9.cobblemonnml.dimension.timer;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonDimensionEvents;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.dimension.network.DungeonTimerPayload;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class DungeonTimer {
    // TIMER STATE
    private static boolean active = false;

    /*
     * Remains true after the timer reaches zero.
     * This is intentionally NOT cleared by stop().
     * If a player disconnected while inside the dungeon and reconnects after the timer expired, the dimension
     * event handler can detect this and kill them.
     * It is cleared only when a brand-new dungeon timer starts.
     */
    private static boolean expired = false;
    private static long endTimeMillis = 0L;
    private static int durationSeconds = 0;
    private static int lastDisplayedSecond = -1;
    // START TIMER
    public static void start(MinecraftServer server) {
        // ALREADY RUNNING
        /*
         * Another player entering the same active dungeon must not restart the timer.
         * Send them the current state immediately instead.
         */
        if (active) {
            sendTimerState( server, getSecondsRemaining(), true );
            return;
        }
        // CURRENT TIER
        DungeonTier tier =
                DungeonDimension
                        .getCurrentTier();
        if (tier == null) {
            DebugLog.log( "Cannot start dungeon timer: " + "current tier is null." );
            return;
        }
        // NEW SESSION
        expired = false;
        // USE TIER TIMER
        durationSeconds = tier.getTimerSeconds();
        active = true;
        endTimeMillis =
                System.currentTimeMillis()
                        + durationSeconds
                        * 1000L;
        lastDisplayedSecond = durationSeconds;
        // INITIAL CLIENT SYNC
        sendTimerState( server, durationSeconds, true );
        DebugLog.log( "Dungeon timer started for " + tier.getDisplayName() + ": " + durationSeconds + " seconds." );
    }
    // SERVER TICK
    public static void tick(MinecraftServer server) {
        if (!active) {
            return;
        }
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension .DUNGEON_DIMENSION );
        if (dungeonLevel == null) {
            return;
        }
        // REMAINING TIME
        long millisRemaining = Math.max( 0L, endTimeMillis - System.currentTimeMillis() );
        int secondsRemaining = (int) Math.ceil( millisRemaining / 1000.0D );
        // SYNC ONCE PER DISPLAYED SECOND
        if (secondsRemaining != lastDisplayedSecond) {
            lastDisplayedSecond = secondsRemaining;
            sendTimerState( server, secondsRemaining, true );
        }
        if (millisRemaining > 0L) {
            return;
        }
        // TIMER EXPIRED
        active = false;
        expired = true;
        endTimeMillis = 0L;
        lastDisplayedSecond = 0;
        // HIDE / FINAL SYNC
        sendTimerState( server, 0, false );
        DebugLog.log( "Dungeon timer expired." );
        // KILL ONLINE DUNGEON PLAYERS
        List<ServerPlayer> players = new ArrayList<>( dungeonLevel.players() );

        /*
         * Anybody currently online inside the dungeon dies immediately.
         * Offline players are handled when they reconnect because expired remains true.
         */
        for (ServerPlayer player : players) {
            player.kill();
        }
        // REQUEST RESET
        DungeonDimensionEvents
                .requestReset();
    }
    // SEND TIMER STATE
    private static void sendTimerState( MinecraftServer server, int secondsRemaining, boolean visible ) {
        if (server == null) {
            return;
        }
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension .DUNGEON_DIMENSION );
        if (dungeonLevel == null) {
            return;
        }
        // THEME
        DungeonTimerPayload payload = getPayload(secondsRemaining, visible);
        // SEND TO EVERY PLAYER IN DUNGEON
        for (ServerPlayer player : dungeonLevel.players()) {
            PacketDistributor.sendToPlayer( player, payload );
        }
    }
    private static @NotNull DungeonTimerPayload getPayload(int secondsRemaining, boolean visible) {
        DungeonTheme theme =
                DungeonSession
                        .getTheme();
        int themeIndex =
                theme != null
                        ? theme.getVisualIndex()
                        : 0;
        // TIER
        DungeonTier tier = DungeonSession.getTier();
        int tierIndex = tier != null ? tier.ordinal() + 1 : 0;
        // PAYLOAD
        return new DungeonTimerPayload(
                visible,
                Math.max( 0, secondsRemaining ),
                Math.max( 0, durationSeconds ),
                themeIndex,
                tierIndex
        );
    }
    // STOP TIMER
    public static void stop() {
        active = false;
        endTimeMillis = 0L;
        durationSeconds = 0;
        lastDisplayedSecond = -1;

        /*
         * IMPORTANT:
         * Do NOT set expired = false here.
         * Dungeon cleanup can call stop() after timeout.
         * We still need to remember that the dungeon expired so an offline player reconnecting inside it dies.
         * The HUD also checks that the local player is physically inside the dungeon dimension, so it disappears as soon
         * as a player leaves even if no final payload can be sent to them here.
         */
        DebugLog.log( "Dungeon timer stopped." );
    }
    // ACTIVE?
    public static boolean isActive() {
        return active;
    }
    // EXPIRED?
    public static boolean isExpired() {
        return expired;
    }
    // REMAINING TIME
    public static int getSecondsRemaining() {
        if (!active) {
            return 0;
        }
        long millisRemaining = Math.max( 0L, endTimeMillis - System.currentTimeMillis() );
        return (int) Math.ceil( millisRemaining / 1000.0D );
    }
}
