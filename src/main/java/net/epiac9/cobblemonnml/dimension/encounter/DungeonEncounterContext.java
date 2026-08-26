package net.epiac9.cobblemonnml.dimension.encounter;

/**
 * Immutable context describing where a dungeon encounter marker came from.
 * This is deliberately independent from the marker block itself because marker
 * blocks are removed before encounter setup runs.
 */
public record DungeonEncounterContext(boolean fromSpecialRoom) {
    private static final DungeonEncounterContext NORMAL = new DungeonEncounterContext(false);
    private static final DungeonEncounterContext SPECIAL = new DungeonEncounterContext(true);

    public static DungeonEncounterContext normalRoom() {
        return NORMAL;
    }

    public static DungeonEncounterContext specialRoom() {
        return SPECIAL;
    }
}
