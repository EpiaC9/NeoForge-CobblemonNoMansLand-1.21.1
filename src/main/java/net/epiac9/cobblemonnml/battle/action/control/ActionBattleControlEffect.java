package net.epiac9.cobblemonnml.battle.action.control;

import java.util.Set;
import java.util.UUID;

public record ActionBattleControlEffect(ActionBattleControlType type, UUID sourcePokemonUUID, String moveId, Set<String> blockedMoveIds) {
    public ActionBattleControlEffect {
        if (type == null) throw new IllegalArgumentException("Control type cannot be null.");
        moveId = normalize(moveId);
        blockedMoveIds = blockedMoveIds == null ? Set.of() : blockedMoveIds.stream().filter(value -> value != null && !value.isBlank()).map(ActionBattleControlEffect::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static ActionBattleControlEffect taunt() { return new ActionBattleControlEffect(ActionBattleControlType.TAUNT, null, null, Set.of()); }
    public static ActionBattleControlEffect disable(String moveId) { return new ActionBattleControlEffect(ActionBattleControlType.DISABLE, null, moveId, Set.of()); }
    public static ActionBattleControlEffect encore(String moveId) { return new ActionBattleControlEffect(ActionBattleControlType.ENCORE, null, moveId, Set.of()); }
    public static ActionBattleControlEffect healBlock() { return new ActionBattleControlEffect(ActionBattleControlType.HEAL_BLOCK, null, null, Set.of()); }
    public static ActionBattleControlEffect torment(UUID sourcePokemonUUID) { return new ActionBattleControlEffect(ActionBattleControlType.TORMENT, sourcePokemonUUID, null, Set.of()); }
    public static ActionBattleControlEffect imprison(UUID sourcePokemonUUID, Set<String> blockedMoveIds) { return new ActionBattleControlEffect(ActionBattleControlType.IMPRISON, sourcePokemonUUID, null, blockedMoveIds); }
    public static ActionBattleControlEffect trapped(UUID sourcePokemonUUID) { return new ActionBattleControlEffect(ActionBattleControlType.TRAPPED, sourcePokemonUUID, null, Set.of()); }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "").replace("cobblemon:", "").replace(" ", "").replace("_", "").replace("-", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
