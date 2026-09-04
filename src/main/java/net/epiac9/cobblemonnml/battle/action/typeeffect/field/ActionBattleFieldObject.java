package net.epiac9.cobblemonnml.battle.action.typeeffect.field;

import java.util.UUID;

public record ActionBattleFieldObject(
        UUID sessionId,
        UUID ownerPokemonUUID,
        OwnerSide ownerSide,
        String dimensionId,
        Position position,
        long creationTick,
        long creationSequence,
        long expiryTick
) {
    public enum OwnerSide { PLAYER, TRAINER }
    public record Position(int x, int y, int z) {}

    public ActionBattleFieldObject {
        if (sessionId == null || ownerPokemonUUID == null || ownerSide == null || position == null
                || dimensionId == null || dimensionId.isBlank() || creationTick < 0L
                || creationSequence < 0L || expiryTick <= creationTick) {
            throw new IllegalArgumentException("Invalid ACTION field-object identity or lifetime.");
        }
    }

    public boolean activeAt(long currentTick) {
        return currentTick >= creationTick && currentTick < expiryTick;
    }
}
