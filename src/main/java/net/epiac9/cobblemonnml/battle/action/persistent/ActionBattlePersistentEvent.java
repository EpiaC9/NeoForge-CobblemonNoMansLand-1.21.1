package net.epiac9.cobblemonnml.battle.action.persistent;

import java.util.UUID;

public record ActionBattlePersistentEvent(ActionBattlePersistentType type, Kind kind, UUID sourcePokemonUUID, float maxHealthFraction) {
    public enum Kind { DAMAGE, FAINT, ENDED }
    public static ActionBattlePersistentEvent damage(ActionBattlePersistentType type, UUID sourcePokemonUUID, float maxHealthFraction) {
        return new ActionBattlePersistentEvent(type, Kind.DAMAGE, sourcePokemonUUID, maxHealthFraction);
    }
    public static ActionBattlePersistentEvent faint(ActionBattlePersistentType type, UUID sourcePokemonUUID) {
        return new ActionBattlePersistentEvent(type, Kind.FAINT, sourcePokemonUUID, 0.0F);
    }
    public static ActionBattlePersistentEvent ended(ActionBattlePersistentType type, UUID sourcePokemonUUID) {
        return new ActionBattlePersistentEvent(type, Kind.ENDED, sourcePokemonUUID, 0.0F);
    }
}
