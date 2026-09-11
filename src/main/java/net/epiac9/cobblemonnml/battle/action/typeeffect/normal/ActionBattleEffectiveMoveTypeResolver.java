package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaController;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleFieldSideMoveFamily;

import java.util.Locale;

public final class ActionBattleEffectiveMoveTypeResolver {
    private static final String WEATHER_BALL = "weatherball";

    private ActionBattleEffectiveMoveTypeResolver() {}

    public static String resolve(PokemonEntity user, Move move) {
        if (move == null || move.getType() == null) return "normal";
        return resolve(move.getName(), move.getType().getName(), conditionAt(user));
    }

    public static String resolve(String moveName, String exposedType, Condition condition) {
        String type = normalize(exposedType);
        if (!WEATHER_BALL.equals(normalizeId(moveName))) return type;
        return switch (condition != null ? condition : Condition.NONE) {
            case HAIL -> "ice";
            case RAIN -> "water";
            case NONE -> type;
        };
    }

    private static Condition conditionAt(PokemonEntity user) {
        if (user == null || user.level() == null) return Condition.NONE;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(user.getUUID());
        if (session != null) {
            boolean hail = ActionBattlePersistentAreaController.global().statesForBattle(session.battleId()).stream()
                    .anyMatch(area -> ActionBattleFieldSideMoveFamily.MOVE_ID_HAIL.equals(area.effectId())
                            && area.contains(user.getX(), user.getY(), user.getZ()));
            if (hail) return Condition.HAIL;
        }
        return user.level().isRainingAt(user.blockPosition()) ? Condition.RAIN : Condition.NONE;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "normal" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    public enum Condition { NONE, HAIL, RAIN }
}
