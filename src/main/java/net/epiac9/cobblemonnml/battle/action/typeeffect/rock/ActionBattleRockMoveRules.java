package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import com.cobblemon.mod.common.api.moves.Move;
import java.util.Locale;

public final class ActionBattleRockMoveRules {
    private ActionBattleRockMoveRules() {}
    public static boolean qualifies(Move move) {
        return move != null && move.getType() != null
                && "rock".equals(move.getType().getName().toLowerCase(Locale.ROOT));
    }
}
