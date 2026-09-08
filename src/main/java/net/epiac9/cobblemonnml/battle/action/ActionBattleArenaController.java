package net.epiac9.cobblemonnml.battle.action;

public final class ActionBattleArenaController {
    private ActionBattleArenaController() {}

    public static boolean physicalCombatEnabled(int insideParticipants) {
        return insideParticipants > 0;
    }

    public static boolean backgroundStateTicks(boolean sessionActive, int insideParticipants) {
        return sessionActive;
    }
}
