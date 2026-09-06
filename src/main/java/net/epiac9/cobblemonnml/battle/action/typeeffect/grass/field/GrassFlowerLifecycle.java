package net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassRules;
import java.util.UUID;

public final class GrassFlowerLifecycle {
    private final UUID sessionId;
    private final UUID ownerPokemonUUID;
    private final ActionBattleFieldObject.OwnerSide ownerSide;
    private final long bloomTick;
    private final long originalCreationTick;
    private final long creationSequence;
    private boolean consumed;

    public GrassFlowerLifecycle(UUID sessionId, UUID ownerPokemonUUID, ActionBattleFieldObject.OwnerSide ownerSide,
                                long bloomTick, long originalCreationTick, long creationSequence) {
        if (sessionId == null || ownerPokemonUUID == null || ownerSide == null || bloomTick < 0L
                || originalCreationTick < 0L || creationSequence < 0L) throw new IllegalArgumentException("Invalid Grass Flower identity.");
        this.sessionId = sessionId;
        this.ownerPokemonUUID = ownerPokemonUUID;
        this.ownerSide = ownerSide;
        this.bloomTick = bloomTick;
        this.originalCreationTick = originalCreationTick;
        this.creationSequence = creationSequence;
    }

    public boolean activeAt(long currentTick) {
        return !consumed && currentTick >= bloomTick && currentTick < bloomTick + ActionBattleGrassRules.FLOWER_LIFETIME_TICKS;
    }
    public boolean consumeFirst() { if (consumed) return false; consumed = true; return true; }
    public UUID sessionId() { return sessionId; }
    public UUID ownerPokemonUUID() { return ownerPokemonUUID; }
    public ActionBattleFieldObject.OwnerSide ownerSide() { return ownerSide; }
    public long bloomTick() { return bloomTick; }
    public long originalCreationTick() { return originalCreationTick; }
    public long creationSequence() { return creationSequence; }
    public boolean consumed() { return consumed; }
}
