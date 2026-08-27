package net.epiac9.cobblemonnml.dimension.timer;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonDimensionEvents;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.network.DungeonTimerPayload;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class DungeonTimer {
    private static boolean active = false;
    private static boolean paused = false;
    private static boolean expired = false;
    private static long endTimeMillis = 0L;
    private static long pausedRemainingMillis = 0L;
    private static int durationSeconds = 0;
    private static int lastDisplayedSecond = -1;

    private DungeonTimer() {}

    public static void start(MinecraftServer server) {
        if (active) {
            sendTimerState(server, getSecondsRemaining(), true);
            return;
        }

        DungeonTier tier = DungeonDimension.getCurrentTier();
        if (tier == null) {
            DebugLog.log("Cannot start dungeon timer: current tier is null.");
            return;
        }

        expired = false;
        paused = false;
        pausedRemainingMillis = 0L;
        durationSeconds = tier.getTimerSeconds();
        active = true;
        endTimeMillis = System.currentTimeMillis() + durationSeconds * 1000L;
        lastDisplayedSecond = durationSeconds;

        sendTimerState(server, durationSeconds, true);
        DebugLog.log("Dungeon timer started for " + tier.getDisplayName() + ": " + durationSeconds + " seconds.");
    }

    public static void tick(MinecraftServer server) {
        if (!active) return;

        ServerLevel dungeonLevel = server.getLevel(DungeonDimension.DUNGEON_DIMENSION);
        if (dungeonLevel == null) return;

        if (paused) {
            int secondsRemaining = getSecondsRemaining();
            if (secondsRemaining != lastDisplayedSecond) {
                lastDisplayedSecond = secondsRemaining;
                sendTimerState(server, secondsRemaining, true);
            }
            return;
        }

        long millisRemaining = Math.max(0L, endTimeMillis - System.currentTimeMillis());
        int secondsRemaining = (int) Math.ceil(millisRemaining / 1000.0D);

        if (secondsRemaining != lastDisplayedSecond) {
            lastDisplayedSecond = secondsRemaining;
            sendTimerState(server, secondsRemaining, true);
        }

        if (millisRemaining <= 0L) expire(server);
    }

    public static boolean pause(MinecraftServer server) {
        if (!active || paused) return false;

        pausedRemainingMillis = Math.max(0L, endTimeMillis - System.currentTimeMillis());
        endTimeMillis = 0L;
        paused = true;
        lastDisplayedSecond = getSecondsRemaining();
        sendTimerState(server, lastDisplayedSecond, true);
        DebugLog.log("Dungeon timer paused at " + lastDisplayedSecond + " seconds.");
        return true;
    }

    public static boolean unpause(MinecraftServer server) {
        if (!active || !paused) return false;

        endTimeMillis = System.currentTimeMillis() + pausedRemainingMillis;
        pausedRemainingMillis = 0L;
        paused = false;
        lastDisplayedSecond = getSecondsRemaining();
        sendTimerState(server, lastDisplayedSecond, true);
        DebugLog.log("Dungeon timer unpaused at " + lastDisplayedSecond + " seconds.");
        return true;
    }

    public static boolean advance(MinecraftServer server, int seconds) {
        if (!active || seconds <= 0) return false;

        long amountMillis = seconds * 1000L;
        if (paused) pausedRemainingMillis = Math.max(0L, pausedRemainingMillis - amountMillis);
        else endTimeMillis -= amountMillis;

        int remaining = getSecondsRemaining();
        if (remaining <= 0) {
            expire(server);
            return true;
        }

        lastDisplayedSecond = remaining;
        sendTimerState(server, remaining, true);
        DebugLog.log("Dungeon timer advanced by " + seconds + " seconds. Remaining: " + remaining + ".");
        return true;
    }

    public static boolean rewind(MinecraftServer server, int seconds) {
        if (!active || seconds <= 0) return false;

        long amountMillis = seconds * 1000L;
        if (paused) pausedRemainingMillis += amountMillis;
        else endTimeMillis += amountMillis;

        durationSeconds += seconds;
        int remaining = getSecondsRemaining();
        lastDisplayedSecond = remaining;
        sendTimerState(server, remaining, true);
        DebugLog.log("Dungeon timer rewound by " + seconds + " seconds. Remaining: " + remaining + ".");
        return true;
    }

    public static boolean end(MinecraftServer server) {
        if (!active) return false;
        expire(server);
        return true;
    }

    private static void expire(MinecraftServer server) {
        if (!active) return;

        active = false;
        paused = false;
        expired = true;
        endTimeMillis = 0L;
        pausedRemainingMillis = 0L;
        lastDisplayedSecond = 0;

        sendTimerState(server, 0, false);
        DebugLog.log("Dungeon timer expired.");

        ServerLevel dungeonLevel = server.getLevel(DungeonDimension.DUNGEON_DIMENSION);
        if (dungeonLevel != null) {
            List<ServerPlayer> players = new ArrayList<>(dungeonLevel.players());
            for (ServerPlayer player : players) player.kill();
        }

        DungeonDimensionEvents.requestReset();
    }

    private static void sendTimerState(MinecraftServer server, int secondsRemaining, boolean visible) {
        if (server == null) return;

        ServerLevel dungeonLevel = server.getLevel(DungeonDimension.DUNGEON_DIMENSION);
        if (dungeonLevel == null) return;

        DungeonTimerPayload payload = getPayload(secondsRemaining, visible);
        for (ServerPlayer player : dungeonLevel.players()) PacketDistributor.sendToPlayer(player, payload);
    }

    private static @NotNull DungeonTimerPayload getPayload(int secondsRemaining, boolean visible) {
        DungeonTheme theme = DungeonSession.getTheme();
        int themeIndex = theme != null ? theme.getVisualIndex() : 0;

        DungeonTier tier = DungeonSession.getTier();
        int tierIndex = tier != null ? tier.ordinal() + 1 : 0;

        return new DungeonTimerPayload(
                visible,
                paused,
                Math.max(0, secondsRemaining),
                Math.max(0, durationSeconds),
                themeIndex,
                tierIndex
        );
    }

    public static void stop() {
        active = false;
        paused = false;
        endTimeMillis = 0L;
        pausedRemainingMillis = 0L;
        durationSeconds = 0;
        lastDisplayedSecond = -1;
        DebugLog.log("Dungeon timer stopped.");
    }

    public static boolean isActive() { return active; }
    public static boolean isPaused() { return paused; }
    public static boolean isExpired() { return expired; }

    public static int getSecondsRemaining() {
        if (!active) return 0;

        long millisRemaining = paused
                ? Math.max(0L, pausedRemainingMillis)
                : Math.max(0L, endTimeMillis - System.currentTimeMillis());

        return (int) Math.ceil(millisRemaining / 1000.0D);
    }
}
