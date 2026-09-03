package net.epiac9.cobblemonnml.dimension;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectRuntime;
import net.epiac9.cobblemonnml.dimension.timer.DungeonTimer;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonDimensionEvents {
    // RESET STATE
    private static boolean resetPending = false;
    private static final long RESET_TIMEOUT_TICKS = 20L * 120L;
    private static long resetRequestedGameTime = -1L;
    // PLAYER CHANGED DIMENSION
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ENTERED DUNGEON
        if (event.getTo().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            handleDungeonEntry(player);
            return;
        }
        // LEFT DUNGEON
        if (event.getFrom().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            ActionBattleTypeEffectRuntime.clearPlayer(player);
            QuestRuntimeManager.failAllDungeonQuests(player);
            requestResetIfDungeonEmpty(player.getServer(), player.getUUID() );
        }
    }
    // PLAYER LOGGED OUT
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ONLY CARE ABOUT DUNGEON LOGOUTS
        if (!player.level() .dimension() .equals( DungeonDimension .DUNGEON_DIMENSION )) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DebugLog.log( "Player " + player .getGameProfile() .getName() + " logged out inside dungeon." );
        ActionBattleTypeEffectRuntime.clearPlayer(player);
        QuestRuntimeManager.failAllDungeonQuests(player);
        // SINGLE-PLAYER OWNER IS QUITTING WORLD
        /*
         * When the owner of an integrated single-player server logs out, Minecraft is normally shutting the entire server down.
         * Do NOT begin dungeon block cleanup here.
         * Starting DungeonResetQueue while Minecraft is saving and stopping can cause the Save & Quit screen to hang.
         * When the world is opened again, the login safety check will see there is no active DungeonSession, kill the
         * stale dungeon player, and request cleanup normally.
         */
        if (server.isSingleplayerOwner( player.getGameProfile() )) {
            DebugLog.log(
                    "Single-player owner left dungeon while world "
                            + "is closing. Deferring dungeon reset "
                            + "until next world load."
            );
            return;
        }
        // DEDICATED / LAN PLAYER LOGOUT
        /*
         * For a normal multiplayer disconnect the server remains running, so it is safe to reset once no other players
         * remain inside.
         */
        requestResetIfDungeonEmpty( server, player.getUUID() );

    }
    // PLAYER LOGGED IN
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // PLAYER WAS SAVED INSIDE DUNGEON
        /*
         * A player who logs out while standing in the dungeon may be restored directly into the dungeon when they
         * reconnect.
         * That does not necessarily trigger a normal dimension change event, so login must be checked separately.
         */
        if (!player.level() .dimension() .equals( DungeonDimension .DUNGEON_DIMENSION )) {
            return;
        }
        handleDungeonEntry(player);
    }
    // HANDLE DUNGEON ENTRY
    private static void handleDungeonEntry(ServerPlayer player) {
        // INVALID / EXPIRED SESSION
        /*
         * The player is not allowed to remain in the dungeon if:
         * 1. The timer reached zero.
         * 2. The previous session was invalidated/reset.
         * 3. There is no active dungeon session.
         * The third check is important for single-player.
         * Quitting the world shuts down the integrated server, which destroys the static DungeonSession state.
         * Minecraft still remembers the player's saved dimension and position, however.
         * So if they load back into the dungeon and there is no active session, they belong to an old dungeon run.
         */
        if (DungeonTimer.isExpired() || DungeonSession.isInvalidated() || !DungeonSession.isActive()) {
            DebugLog.log(
                    "Player "
                            + player
                            .getGameProfile()
                            .getName()
                            + " entered an invalid dungeon session. "
                            + "Killing player."
            );
            // MAKE SURE OLD DUNGEON GETS CLEANED UP
            requestReset();
            // FAIL ACTIVE DUNGEON QUESTS
            QuestRuntimeManager.failAllDungeonQuests(player);
            // KILL PLAYER
            player.kill();
            return;
        }
        // VALID ACTIVE SESSION
        /*
         * DungeonTimer.start() already prevents another player from restarting an existing active timer.
         */
        DungeonTimer.start(player.getServer());
    }
    // RESET IF DUNGEON IS EMPTY
    private static void requestResetIfDungeonEmpty(MinecraftServer server, UUID ignoredPlayer) {
        if (server == null) {
            return;
        }
        ServerLevel dungeonLevel = server.getLevel( DungeonDimension .DUNGEON_DIMENSION );
        if (dungeonLevel == null) {
            return;
        }
        // CHECK OTHER ONLINE PLAYERS
        for (ServerPlayer otherPlayer : dungeonLevel.players()) {

            /*
             * Ignore the player who is currently leaving or logging out.
             */
            if (otherPlayer .getUUID() .equals( ignoredPlayer )) {
                continue;
            }

            /*
             * At least one other online player is still inside.
             * The dungeon remains active.
             */
            DebugLog.log(
                    "Dungeon remains active because "
                            + otherPlayer
                            .getGameProfile()
                            .getName()
                            + " is still inside."
            );
            return;
        }
        // NOBODY ONLINE REMAINS
        DebugLog.log( "No online players remain inside dungeon. " + "Requesting reset." );
        requestReset();
    }
    // REQUEST RESET
    public static void requestReset() {

        /*
         * Avoid repeatedly requesting the same reset every tick or from multiple player events.
         */
        if (resetPending) {
            return;
        }
        resetPending = true;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerLevel dungeonLevel = server.getLevel( DungeonDimension.DUNGEON_DIMENSION );
            if (dungeonLevel != null) {
                resetRequestedGameTime = dungeonLevel.getGameTime();
            }
        }
        DebugLog.log( "Dungeon session end requested." );
    }
    // RESET PENDING?
    public static boolean isResetPending() {
        return resetPending;
    }
    // RESET REQUEST TIMED OUT?
    public static boolean hasResetRequestTimedOut(ServerLevel dungeonLevel) {
        if (!resetPending || dungeonLevel == null || resetRequestedGameTime < 0L) {
            return false;
        }
        long elapsedTicks =
                dungeonLevel.getGameTime()
                        - resetRequestedGameTime;
        return elapsedTicks
                >= RESET_TIMEOUT_TICKS;
    }
    // CLEAR RESET REQUEST
    public static void clearResetPending() {
        resetPending = false;
        resetRequestedGameTime = -1L;

    }
}
