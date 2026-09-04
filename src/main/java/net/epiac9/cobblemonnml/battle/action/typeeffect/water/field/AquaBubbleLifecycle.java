package net.epiac9.cobblemonnml.battle.action.typeeffect.water.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;

import java.util.UUID;

public final class AquaBubbleLifecycle {
    private final UUID sessionId;
    private final UUID ownerPokemonUUID;
    private final ActionBattleFieldObject.OwnerSide ownerSide;
    private final long creationTick;
    private final long creationSequence;
    private final long expiryTick;
    private boolean consumed;

    public AquaBubbleLifecycle(UUID sessionId, UUID ownerPokemonUUID, ActionBattleFieldObject.OwnerSide ownerSide,
                               long creationTick, long creationSequence, long expiryTick) {
        if (sessionId == null || ownerPokemonUUID == null || ownerSide == null || creationTick < 0L
                || creationSequence < 0L || expiryTick <= creationTick) {
            throw new IllegalArgumentException("Invalid Aqua Bubble identity or lifetime.");
        }
        this.sessionId = sessionId;
        this.ownerPokemonUUID = ownerPokemonUUID;
        this.ownerSide = ownerSide;
        this.creationTick = creationTick;
        this.creationSequence = creationSequence;
        this.expiryTick = expiryTick;
    }

    public UUID sessionId() { return sessionId; }
    public UUID ownerPokemonUUID() { return ownerPokemonUUID; }
    public ActionBattleFieldObject.OwnerSide ownerSide() { return ownerSide; }
    public long creationTick() { return creationTick; }
    public long creationSequence() { return creationSequence; }
    public long expiryTick() { return expiryTick; }
    public boolean consumed() { return consumed; }
    public boolean activeAt(long currentTick) { return !consumed && currentTick >= creationTick && currentTick < expiryTick; }

    public boolean consumeFirst() {
        if (consumed) return false;
        consumed = true;
        return true;
    }
}
