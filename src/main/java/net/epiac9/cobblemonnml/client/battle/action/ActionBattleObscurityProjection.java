package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleObscurityProjection {
    public static final int ELEMENT_COUNT = 12;

    public enum Side {
        ALLY,
        ENEMY
    }

    private final int screenStage;

    public ActionBattleObscurityProjection(int screenStage) {
        this.screenStage = clamp(screenStage);
    }

    public int stage(Side side, int elementIndex) {
        if (elementIndex < 0 || elementIndex >= ELEMENT_COUNT) return 0;
        return screenStage;
    }

    public int commandStage(int elementIndex) {
        return stage(Side.ALLY, elementIndex);
    }

    public int partyStage() {
        return screenStage;
    }

    private static int clamp(int stage) {
        return Math.max(0, Math.min(4, stage));
    }
}
