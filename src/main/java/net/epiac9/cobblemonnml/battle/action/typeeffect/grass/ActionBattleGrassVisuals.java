package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleGrassVisuals {
    public static final String EMPOWER_STATUS_ID = "TYPE_GRASS_EMPOWER";
    public static final String LEECH_SEED_STATUS_ID = "TYPE_LEECH_SEED";
    public static final String MOVEMENT_STATUS_ID = "TYPE_GRASS_MOVEMENT";
    private ActionBattleGrassVisuals() {}

    public static boolean hasCountdown(String statusId) {
        return !EMPOWER_STATUS_ID.equals(statusId);
    }
}
