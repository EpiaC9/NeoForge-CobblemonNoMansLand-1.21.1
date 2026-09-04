package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBattleElectricContributionSource {
    public static final int TEMPORARY_GAMEPLAY_CHARGE = 90;

    public record Contributions(int charge, int paralysisFlinch) {
        public Contributions {
            if (charge < 0 || paralysisFlinch < 0) {
                throw new IllegalArgumentException("Electric contributions cannot be negative.");
            }
        }
    }

    private static final Contributions NONE = new Contributions(0, 0);
    private static final Contributions TEMPORARY_FALLBACK = new Contributions(TEMPORARY_GAMEPLAY_CHARGE, 0);
    private static final Map<String, Contributions> BY_MOVE = new ConcurrentHashMap<>();
    private static volatile int movementFlinch;

    private ActionBattleElectricContributionSource() {}

    public static void register(String moveName, int charge, int paralysisFlinch) {
        String key = normalize(moveName);
        if (key.isEmpty()) throw new IllegalArgumentException("Move name cannot be blank.");
        Contributions contributions = new Contributions(charge, paralysisFlinch);
        BY_MOVE.put(key, contributions);
    }

    public static Contributions forMove(String moveName) {
        String key = normalize(moveName);
        return key.isEmpty() ? NONE : BY_MOVE.getOrDefault(key, TEMPORARY_FALLBACK);
    }

    public static boolean isConfigured(String moveName) {
        return BY_MOVE.containsKey(normalize(moveName));
    }

    public static void setMovementFlinch(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Movement flinch contribution cannot be negative.");
        movementFlinch = amount;
    }

    public static int movementFlinch() {
        return movementFlinch;
    }

    public static void clear() {
        BY_MOVE.clear();
        movementFlinch = 0;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        StringBuilder normalized = new StringBuilder();
        for (char character : value.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(character)) normalized.append(character);
        }
        return normalized.toString();
    }
}
