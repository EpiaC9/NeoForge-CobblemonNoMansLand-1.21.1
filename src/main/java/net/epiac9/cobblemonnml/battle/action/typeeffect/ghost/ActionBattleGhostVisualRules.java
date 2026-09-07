package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

public final class ActionBattleGhostVisualRules {
    private ActionBattleGhostVisualRules() {}

    public static Cue cue(ActionBattleGhostCurseType type) {
        if (type == null) return null;
        return switch (type) {
            case FRAILTY -> new Cue(Style.ASH, 7, 10, 0.45D);
            case WEAKNESS -> new Cue(Style.SOUL, 6, 9, 0.55D);
            case SILENCE -> new Cue(Style.WITCH, 5, 12, 0.78D);
            case DECAY -> new Cue(Style.ASH, 8, 8, 0.50D);
            case WITHERING -> new Cue(Style.SMOKE, 6, 7, 0.55D);
            case BURDEN -> new Cue(Style.SMOKE, 9, 11, 0.28D);
            case TORMENT -> new Cue(Style.WITCH, 8, 13, 0.52D);
            case BINDING -> new Cue(Style.PORTAL, 7, 8, 0.18D);
            case HUNGER -> new Cue(Style.DAMAGE, 7, 10, 0.50D);
            case MISFORTUNE -> new Cue(Style.WITCH, 10, 15, 0.62D);
            case HAUNTING -> new Cue(Style.SOUL, 10, 14, 0.64D);
        };
    }

    public static boolean ambientDue(long currentTick) {
        return currentTick >= 0L && currentTick % 20L == 0L;
    }

    public enum Style { SOUL, WITCH, SMOKE, ASH, PORTAL, DAMAGE }
    public record Cue(Style style, int applyCount, int eventCount, double heightFraction) {}
}
