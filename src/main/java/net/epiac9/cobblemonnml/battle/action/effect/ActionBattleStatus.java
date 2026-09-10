package net.epiac9.cobblemonnml.battle.action.effect;

public enum ActionBattleStatus {
    BURN,
    FREEZE,
    POISON,
    TOXIC,
    PARALYSIS,
    SLEEP,
    CONFUSION,
    EVASION;

    public boolean directTimedStatus() {
        return this == BURN || this == FREEZE || this == POISON || this == TOXIC || this == PARALYSIS;
    }
}
