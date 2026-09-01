package net.epiac9.cobblemonnml.client.battle.action;

public enum ActionBattleStatStageDisplay {
    NEUTRAL("--", 0, 0),
    UP_ONE("", 1, 1),
    UP_TWO("", 1, 2),
    DOWN_ONE("", -1, 1),
    DOWN_TWO("", -1, 2);

    private final String glyph;
    private final int direction;
    private final int arrowCount;

    ActionBattleStatStageDisplay(String glyph, int direction, int arrowCount) {
        this.glyph = glyph;
        this.direction = direction;
        this.arrowCount = arrowCount;
    }

    public static ActionBattleStatStageDisplay fromStage(int stage) {
        if (stage == 0) return NEUTRAL;
        if (stage > 0) return stage == 1 ? UP_ONE : UP_TWO;
        return stage == -1 ? DOWN_ONE : DOWN_TWO;
    }

    public String glyph() { return glyph; }
    public int direction() { return direction; }
    public int arrowCount() { return arrowCount; }
}
