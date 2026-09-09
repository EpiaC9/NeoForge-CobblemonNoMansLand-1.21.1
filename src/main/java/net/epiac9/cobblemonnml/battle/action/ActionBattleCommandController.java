package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import java.util.UUID;

public final class ActionBattleCommandController {
    private ActionBattleCommandController() {}

    public enum Side { PLAYER, TRAINER }
    public enum InterruptReason { NEW_COMMAND, SWAP, FAINT, RECALL, TARGET_INVALID, MOVE_FAILED, CONTROL_EFFECT, SLEEP, EXPLICIT_INTERRUPT, BATTLE_END }

    public static boolean onOrdinaryHit(ActionBattleSession session, UUID pokemonUUID) {
        return false;
    }

    public static void onCommandIssued(ActionBattleSession session, UUID pokemonUUID) {
        if (!isActivePokemon(session, pokemonUUID)) return;
        applyCommandHooks(pokemonUUID);
    }

    public static boolean cancelPendingOrders(ActionBattleSession session, UUID pokemonUUID, InterruptReason reason) {
        Side side = sideOf(session, pokemonUUID);
        if (side == null || reason == null) return false;
        if (side == Side.PLAYER) {
            UUID ownerUUID = session.playerOwnerForPokemon(pokemonUUID);
            if (reason == InterruptReason.CONTROL_EFFECT || reason == InterruptReason.SLEEP) applyControlHooks(pokemonUUID);
            boolean hadOrders = session.hasPlayerMovementIntent(ownerUUID);
            session.clearPlayerMoveState(ownerUUID);
            return hadOrders;
        }
        return cancelPendingOrders(session, side, reason);
    }

    public static boolean cancelPendingOrders(ActionBattleSession session, Side side, InterruptReason reason) {
        if (session == null || side == null || reason == null) return false;
        if (reason == InterruptReason.CONTROL_EFFECT || reason == InterruptReason.SLEEP) applyControlEffect(session, side);
        if (reason == InterruptReason.SLEEP) {
            return side == Side.PLAYER ? session.cancelPlayerOrdersForSleep() : session.cancelTrainerOrdersForSleep();
        }
        return cancelOrdersForSide(session, side);
    }

    public static boolean addCooldownPenalty(ActionBattleSession session, UUID pokemonUUID, long currentTick, long penaltyTicks) {
        return session != null && session.addPokemonCommandCooldownPenalty(pokemonUUID, currentTick, penaltyTicks);
    }

    public static boolean isChanneling(UUID pokemonUUID) {
        return pokemonUUID != null && (ActionBattleHailHandler.isChanneling(pokemonUUID) || ActionBattleToxicSpikesHandler.isChanneling(pokemonUUID));
    }

    public static Side sideOf(ActionBattleSession session, UUID pokemonUUID) {
        if (session == null || pokemonUUID == null) return null;
        if (session.isPlayerPokemon(pokemonUUID)) return Side.PLAYER;
        if (pokemonUUID.equals(session.trainerActivePokemonUUID())) return Side.TRAINER;
        return null;
    }

    private static void applyControlEffect(ActionBattleSession session, Side side) {
        UUID pokemonUUID = side == Side.PLAYER ? session.playerActivePokemonUUID() : session.trainerActivePokemonUUID();
        applyControlHooks(pokemonUUID);
    }

    private static void applyCommandHooks(UUID pokemonUUID) {
        if (pokemonUUID == null) return;
        ActionBattleHailHandler.onCommand(pokemonUUID);
        ActionBattleToxicSpikesHandler.onCommand(pokemonUUID);
    }

    private static void applyControlHooks(UUID pokemonUUID) {
        if (pokemonUUID == null) return;
        ActionBattleHailHandler.onControlEffect(pokemonUUID);
        ActionBattleToxicSpikesHandler.onControlEffect(pokemonUUID);
    }

    public static void onExplicitInterrupt(UUID pokemonUUID) { applyControlHooks(pokemonUUID); }

    private static boolean cancelOrdersForSide(ActionBattleSession session, Side side) {
        if (side == Side.PLAYER) {
            if (!session.hasPlayerMovementIntent()) return false;
            session.cancelPlayerOrders();
            return true;
        }
        if (!session.hasTrainerMovementIntent()) return false;
        session.cancelTrainerOrders();
        return true;
    }

    private static boolean isActivePokemon(ActionBattleSession session, UUID pokemonUUID) { return sideOf(session, pokemonUUID) != null; }
}
