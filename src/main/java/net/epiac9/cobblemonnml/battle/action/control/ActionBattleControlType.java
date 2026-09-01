package net.epiac9.cobblemonnml.battle.action.control;

public enum ActionBattleControlType {
    TAUNT(true),
    DISABLE(true),
    ENCORE(true),
    HEAL_BLOCK(true),
    TORMENT(false),
    IMPRISON(false),
    TRAPPED(false);

    private final boolean timed;

    ActionBattleControlType(boolean timed) { this.timed = timed; }
    public boolean timed() { return timed; }
}
