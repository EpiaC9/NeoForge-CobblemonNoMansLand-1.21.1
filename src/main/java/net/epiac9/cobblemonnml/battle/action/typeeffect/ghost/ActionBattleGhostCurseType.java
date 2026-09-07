package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

public enum ActionBattleGhostCurseType {
    FRAILTY,
    WEAKNESS,
    SILENCE,
    DECAY,
    WITHERING,
    BURDEN,
    TORMENT,
    BINDING,
    HUNGER,
    MISFORTUNE,
    HAUNTING;

    public boolean timed() {
        return switch (this) {
            case DECAY, WITHERING, BINDING, HUNGER, MISFORTUNE, HAUNTING -> true;
            default -> false;
        };
    }

    public boolean pending() {
        return !timed();
    }
}
