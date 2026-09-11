package net.epiac9.cobblemonnml.battle.action.compat;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class ActionBattleStatMoveMetadata {
    private ActionBattleStatMoveMetadata() {}

    public static Map<ActionBattleStat, Integer> translate(String metadataName, int stages) {
        if (metadataName == null || stages == 0) return Map.of();
        return switch (metadataName.trim().toLowerCase(Locale.ROOT)) {
            case "attack", "atk" -> Map.of(ActionBattleStat.ATTACK, stages);
            case "defense", "def" -> Map.of(ActionBattleStat.DEFENSE, stages);
            case "special_attack", "spa" -> Map.of(ActionBattleStat.SPECIAL_ATTACK, stages);
            case "special_defense", "spd" -> Map.of(ActionBattleStat.SPECIAL_DEFENSE, stages);
            case "speed", "spe" -> Map.of(ActionBattleStat.SPEED, stages);
            case "accuracy" -> Map.of(ActionBattleStat.ACCURACY, stages);
            case "evasion" -> Map.of(ActionBattleStat.EVASION, stages);
            case "all" -> Map.of(ActionBattleStat.ATTACK, stages,
                    ActionBattleStat.DEFENSE, stages, ActionBattleStat.SPEED, stages);
            default -> Map.of();
        };
    }

    public static Map<ActionBattleStat, Integer> translateCanonical(Map<String, Integer> boosts) {
        if (boosts == null || boosts.isEmpty()) return Map.of();
        EnumMap<ActionBattleStat, Integer> translated = new EnumMap<>(ActionBattleStat.class);
        for (Map.Entry<String, Integer> entry : boosts.entrySet()) {
            if (entry.getValue() == null || entry.getValue() == 0) continue;
            translate(entry.getKey(), entry.getValue()).forEach(
                    (stat, value) -> translated.merge(stat, value, Integer::sum));
        }
        return translated.isEmpty() ? Map.of() : Map.copyOf(translated);
    }

    public static boolean passesChance(float chance, boolean sereneGrace, boolean sheerForce,
                                       boolean canActivateSheerForce, float randomRoll) {
        if (sheerForce && canActivateSheerForce) return false;
        float adjustedChance = sereneGrace ? chance * 2.0F : chance;
        return adjustedChance > randomRoll;
    }
}
