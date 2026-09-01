package net.epiac9.cobblemonnml.battle.action.control;

public final class ActionBattleControlRules {
    private ActionBattleControlRules() {}

    public static boolean canUseMove(ActionBattleControlEffect effect, String moveId, boolean damaging, String lastCommittedMoveId) {
        if (effect == null) return true;
        String normalizedMove = normalize(moveId);
        String normalizedLast = normalize(lastCommittedMoveId);
        return switch (effect.type()) {
            case TAUNT -> damaging;
            case DISABLE -> normalizedMove == null || !normalizedMove.equals(effect.moveId());
            case ENCORE -> normalizedMove != null && normalizedMove.equals(effect.moveId());
            case TORMENT -> normalizedMove == null || normalizedLast == null || !normalizedMove.equals(normalizedLast);
            case IMPRISON -> normalizedMove == null || !effect.blockedMoveIds().contains(normalizedMove);
            case HEAL_BLOCK, TRAPPED -> true;
        };
    }

    public static boolean blocksHealing(ActionBattleControlEffect effect) { return effect != null && effect.type() == ActionBattleControlType.HEAL_BLOCK; }
    public static boolean blocksSwap(ActionBattleControlEffect effect) { return effect != null && effect.type() == ActionBattleControlType.TRAPPED; }

    public static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "").replace("cobblemon:", "").replace(" ", "").replace("_", "").replace("-", "");
        return normalized.isEmpty() ? null : normalized;
    }
}
