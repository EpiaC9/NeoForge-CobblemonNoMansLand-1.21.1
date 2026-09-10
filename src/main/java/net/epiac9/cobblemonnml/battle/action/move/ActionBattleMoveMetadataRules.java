package net.epiac9.cobblemonnml.battle.action.move;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ActionBattleMoveMetadataRules {
    private ActionBattleMoveMetadataRules() {}

    public static String canonicalMoveId(String value) { return normalizeToken(value); }

    public static String normalizeToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
    }

    public static ActionBattleMoveDescriptor.TargetingMode targetingMode(String category) {
        String normalized = normalizeToken(category);
        if (normalized.equals("user") || normalized.equals("self") || normalized.contains("ally")
                || normalized.equals("userorally") || normalized.equals("allallies")) {
            return ActionBattleMoveDescriptor.TargetingMode.SELF_OR_ALLY;
        }
        return ActionBattleMoveDescriptor.TargetingMode.TARGET;
    }

    public static ActionBattleMoveDescriptor.DamageCategory damageCategory(String category, int power) {
        String normalized = normalizeToken(category);
        if (normalized.contains("physical")) return ActionBattleMoveDescriptor.DamageCategory.PHYSICAL;
        if (normalized.contains("special")) return ActionBattleMoveDescriptor.DamageCategory.SPECIAL;
        if (normalized.contains("status") || power <= 0) return ActionBattleMoveDescriptor.DamageCategory.STATUS;
        return ActionBattleMoveDescriptor.DamageCategory.PHYSICAL;
    }

    public static Set<String> normalizeFlags(Collection<?> rawFlags) {
        if (rawFlags == null || rawFlags.isEmpty()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object flag : rawFlags) {
            String normalized = normalizeToken(flag != null ? flag.toString() : null);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return Set.copyOf(result);
    }


    public static boolean hasCanonicalFlag(Set<String> flags, ActionBattleMoveFlag flag) {
        if (flags == null || flags.isEmpty() || flag == null) return false;
        for (String raw : flags) {
            if (flag.matches(normalizeToken(raw))) return true;
        }
        return false;
    }

    public static ActionBattleMoveDescriptor.ProtectInteraction protectInteraction(Set<String> flags) {
        if (containsAny(flags, "bypassprotect", "protectbypass", "ignoreprotect", "breaksprotect")) {
            return ActionBattleMoveDescriptor.ProtectInteraction.BYPASS;
        }
        if (containsAny(flags, "protectblocked", "blockedbyprotect", "protectable")) {
            return ActionBattleMoveDescriptor.ProtectInteraction.BLOCKED;
        }
        return ActionBattleMoveDescriptor.ProtectInteraction.NORMAL;
    }

    private static boolean containsAny(Set<String> flags, String... candidates) {
        if (flags == null || flags.isEmpty()) return false;
        for (String raw : flags) {
            String normalized = normalizeToken(raw);
            for (String candidate : candidates) {
                if (normalized.equals(candidate)) return true;
            }
        }
        return false;
    }

    public static double accuracy(double value) {
        if (!Double.isFinite(value)) return 100.0D;
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    public static int nonNegative(int value) { return Math.max(0, value); }
}
