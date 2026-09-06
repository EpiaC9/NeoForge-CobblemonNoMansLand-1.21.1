package net.epiac9.cobblemonnml.battle.action.compat;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;

import java.util.Locale;
import java.util.Map;

public final class ActionBattleStatMoveMetadata {
    private ActionBattleStatMoveMetadata() {}

    public static Map<ActionBattleStat, Integer> translate(String metadataName, int stages) {
        if (metadataName == null || stages == 0) return Map.of();
        return switch (metadataName.trim().toLowerCase(Locale.ROOT)) {
            case "attack" -> Map.of(ActionBattleStat.ATTACK, stages);
            case "defense" -> Map.of(ActionBattleStat.DEFENSE, stages);
            case "special_attack" -> Map.of(ActionBattleStat.SPECIAL_ATTACK, stages);
            case "special_defense" -> Map.of(ActionBattleStat.SPECIAL_DEFENSE, stages);
            case "speed" -> Map.of(ActionBattleStat.SPEED, stages);
            case "accuracy" -> Map.of(ActionBattleStat.ACCURACY, stages);
            case "all" -> Map.of(ActionBattleStat.ATTACK, stages,
                    ActionBattleStat.DEFENSE, stages, ActionBattleStat.SPEED, stages);
            default -> Map.of();
        };
    }

    public static boolean passesChance(float chance, boolean sereneGrace, boolean sheerForce,
                                       boolean canActivateSheerForce, float randomRoll) {
        if (sheerForce && canActivateSheerForce) return false;
        float adjustedChance = sereneGrace ? chance * 2.0F : chance;
        return adjustedChance > randomRoll;
    }
}
