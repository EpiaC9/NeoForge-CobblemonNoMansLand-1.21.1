package net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassRules;
import java.util.UUID;

public final class GrassSeedLifecycle {
    private final UUID sessionId;
    private final UUID ownerPokemonUUID;
    private final ActionBattleFieldObject.OwnerSide ownerSide;
    private final long creationTick;
    private final long creationSequence;
    private boolean consumed;

    public GrassSeedLifecycle(UUID sessionId, UUID ownerPokemonUUID, ActionBattleFieldObject.OwnerSide ownerSide,
                              long creationTick, long creationSequence) {
        if (sessionId == null || ownerPokemonUUID == null || ownerSide == null
                || creationTick < 0L || creationSequence < 0L) throw new IllegalArgumentException("Invalid Grass Seed identity.");
        this.sessionId = sessionId;
        this.ownerPokemonUUID = ownerPokemonUUID;
        this.ownerSide = ownerSide;
        this.creationTick = creationTick;
        this.creationSequence = creationSequence;
    }

    public boolean readyToBloom(long currentTick) {
        return !consumed && currentTick >= creationTick + ActionBattleGrassRules.SEED_ARM_TICKS;
    }
    public boolean consumeFirst() { if (consumed) return false; consumed = true; return true; }
    public GrassFlowerLifecycle bloom(long currentTick) {
        if (!readyToBloom(currentTick)) throw new IllegalStateException("Grass Seed is not ready to bloom.");
        consumed = true;
        return new GrassFlowerLifecycle(sessionId, ownerPokemonUUID, ownerSide, currentTick, creationTick, creationSequence);
    }
    public UUID sessionId() { return sessionId; }
    public UUID ownerPokemonUUID() { return ownerPokemonUUID; }
    public ActionBattleFieldObject.OwnerSide ownerSide() { return ownerSide; }
    public long creationTick() { return creationTick; }
    public long creationSequence() { return creationSequence; }
    public boolean consumed() { return consumed; }
}
