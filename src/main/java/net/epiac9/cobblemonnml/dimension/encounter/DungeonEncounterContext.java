package net.epiac9.cobblemonnml.dimension.encounter;

/**
 * Immutable context describing where a dungeon encounter marker came from.
 * This is deliberately independent from the marker block itself because marker
 * blocks are removed before encounter setup runs.
 */
public record DungeonEncounterContext(boolean fromSpecialRoom, String roomId,
                                      net.epiac9.cobblemonnml.battle.action.ActionBattleRoomBounds roomBounds) {
    private static final DungeonEncounterContext NORMAL = new DungeonEncounterContext(false, "", null);
    private static final DungeonEncounterContext SPECIAL = new DungeonEncounterContext(true, "", null);

    public DungeonEncounterContext {
        roomId = roomId != null ? roomId : "";
    }

    public static DungeonEncounterContext normalRoom() {
        return NORMAL;
    }

    public static DungeonEncounterContext specialRoom() {
        return SPECIAL;
    }

    public static DungeonEncounterContext normalRoom(String roomId,
            net.epiac9.cobblemonnml.battle.action.ActionBattleRoomBounds roomBounds) {
        return new DungeonEncounterContext(false, roomId, roomBounds);
    }

    public static DungeonEncounterContext specialRoom(String roomId,
            net.epiac9.cobblemonnml.battle.action.ActionBattleRoomBounds roomBounds) {
        return new DungeonEncounterContext(true, roomId, roomBounds);
    }
}
