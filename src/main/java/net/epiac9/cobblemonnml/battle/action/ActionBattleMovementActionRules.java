package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;

public final class ActionBattleMovementActionRules {
    private ActionBattleMovementActionRules() {}

    public static boolean requiresMovementKind(boolean melee, boolean dashOrRush, boolean stationary) {
        return !stationary && (melee || dashOrRush);
    }

    public static boolean requiresMovement(Move move) {
        return move != null && requiresMovementKind(FightOrFlightAdapter.isMeleeMove(move),
                ActionProjectileProfile.isDashRush(move.getName()), false);
    }
}
