package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.UUID;

public final class ActionBattleBugDiagnostics {
    private ActionBattleBugDiagnostics() {}

    public static String selection(String pokemonName, UUID pokemonId, boolean bugTyped,
                                   TrainingTotals training,
                                   EnumSet<ActionBattleBugTrainingStat> highest,
                                   EnumSet<ActionBattleBugTrainingStat> triggered,
                                   EnumSet<ActionBattleBugTrainingStat> locked) {
        return "[BugAdapt] user=" + safeName(pokemonName) + " uuid=" + pokemonId + " bug=" + bugTyped
                + " training=" + training + " highest=" + format(highest)
                + " triggered=" + format(triggered) + " locked=" + format(locked);
    }

    public static String format(EnumSet<ActionBattleBugTrainingStat> stats) {
        StringJoiner values = new StringJoiner(",", "[", "]");
        if (stats != null) for (ActionBattleBugTrainingStat stat : stats) values.add(shortName(stat));
        return values.toString();
    }

    private static String shortName(ActionBattleBugTrainingStat stat) {
        return switch (stat) {
            case HP -> "HP";
            case ATTACK -> "ATK";
            case DEFENSE -> "DEF";
            case SPECIAL_ATTACK -> "SPATK";
            case SPECIAL_DEFENSE -> "SPDEF";
            case SPEED -> "SPEED";
        };
    }

    private static String safeName(String name) { return name == null || name.isBlank() ? "unknown" : name; }

    public record TrainingTotals(int hp, int attack, int defense, int specialAttack,
                                 int specialDefense, int speed) {
        @Override public String toString() {
            return "{HP:" + hp + ",ATK:" + attack + ",DEF:" + defense + ",SPATK:" + specialAttack
                    + ",SPDEF:" + specialDefense + ",SPEED:" + speed + "}";
        }
    }
}
