package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveTargetRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockMoveRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime;

import java.util.Locale;
import java.util.Set;

public final class ActionBattleBoostedMoveRules {
    private static final Set<String> MECHANIC_TYPES = Set.of(
            "water", "grass", "ground", "psychic", "rock", "ghost", "dragon", "dark", "bug", "flying", "steel"
    );

    private ActionBattleBoostedMoveRules() {}

    public static boolean isMechanicallyBoosted(boolean hasTypeMechanicBenefit, String effectiveMoveType) {
        return isMechanicallyBoosted(hasTypeMechanicBenefit, effectiveMoveType, true);
    }

    public static boolean isMechanicallyBoosted(
            boolean hasTypeMechanicBenefit,
            String effectiveMoveType,
            boolean moveHasEnhancedBranch
    ) {
        return hasTypeMechanicBenefit && moveHasEnhancedBranch
                && MECHANIC_TYPES.contains(normalize(effectiveMoveType));
    }

    public static boolean isMechanicallyBoosted(PokemonEntity user, Move move) {
        if (user == null || move == null) return false;
        String type = ActionBattleEffectiveMoveTypeResolver.resolve(user, move);
        return isMechanicallyBoosted(ActionBattleTypeMechanicIdentity.hasMechanicBenefit(user, type),
                type, moveHasEnhancedBranch(user, move, type));
    }

    public static boolean shouldRender(boolean mechanicallyBoosted, int obscurityStage) {
        return mechanicallyBoosted && obscurityStage < 4;
    }

    private static boolean moveHasEnhancedBranch(PokemonEntity user, Move move, String type) {
        boolean damaging = FightOrFlightAdapter.isNativeDamageMove(move)
                || FightOrFlightAdapter.movePower(move) > 0;
        boolean enemyTarget = ActionBattleMoveTargetRules.targetsEnemy(
                FightOrFlightAdapter.moveTargetCategory(move));
        return switch (normalize(type)) {
            case "dark", "bug" -> damaging && enemyTarget;
            case "water" -> true;
            case "grass" -> ActionBattleGrassController.isQualifyingMove(move);
            case "ground" -> ActionBattleGroundController.isQualifyingMove(user, move);
            case "psychic" -> enemyTarget;
            case "rock" -> ActionBattleRockMoveRules.qualifies(move);
            case "ghost", "dragon", "flying" -> true;
            case "steel" -> ActionBattleSteelRuntime.isQualifyingSelfBuffMove(move);
            case "fairy" -> false;
            default -> false;
        };
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
