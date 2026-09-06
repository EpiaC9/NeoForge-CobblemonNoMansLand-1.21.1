package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;

public final class ActionBattleMovementActionRules {
    private ActionBattleMovementActionRules() {}

    public static boolean requiresMovementKind(boolean melee, boolean dashOrRush, boolean stationary) {
        return !stationary && (melee || dashOrRush);
    }

    public static boolean requiresMovement(Move move) {
        return move != null && requiresMovementKind(FightOrFlightAdapter.isMeleeMove(move),
                ActionProjectileProfile.isDashRush(move.getName()), false);
    }

    public static double composeMovementSpeed(double baseSpeed, double statMultiplier,
                                              double grassMultiplier, double groundMultiplier) {
        return baseSpeed * statMultiplier * grassMultiplier * groundMultiplier;
    }

    public static boolean blocksMovement(boolean waterImmobilized, boolean groundImmobilized) {
        return waterImmobilized || groundImmobilized;
    }

    public static boolean canUseAction(boolean movementBlocked, boolean requiresMovement) {
        return !movementBlocked || !requiresMovement;
    }

    public static boolean blocksVoluntaryRecall(boolean groundActive) {
        return groundActive;
    }

    public static boolean canReplaceAfterFaint(boolean groundActive) {
        return true;
    }

    public static boolean isMovementBlocked(ActionBattleSession session, java.util.UUID pokemonUUID,
                                            long currentTick) {
        if (session == null || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
        boolean waterImmobilized = effects.immobilizedView(
                session.dungeonSessionId(), pokemonUUID, currentTick).isPresent();
        boolean groundImmobilized = effects.groundBlocksMovement(
                session.dungeonSessionId(), pokemonUUID, currentTick);
        return blocksMovement(waterImmobilized, groundImmobilized);
    }

    public static boolean isVoluntaryRecallBlocked(ActionBattleSession session, java.util.UUID pokemonUUID,
                                                    long currentTick) {
        return session != null && pokemonUUID != null && currentTick >= 0L
                && blocksVoluntaryRecall(ActionBattleTypeEffectController.global().groundBlocksRecall(
                session.dungeonSessionId(), pokemonUUID, currentTick));
    }
}
