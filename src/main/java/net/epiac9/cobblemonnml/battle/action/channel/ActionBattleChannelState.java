package net.epiac9.cobblemonnml.battle.action.channel;

import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import java.util.UUID;

public final class ActionBattleChannelState {
    private final UUID battleId;
    private final UUID casterPokemonUUID;
    private final UUID targetPokemonUUID;
    private final String moveId;
    private final ActionBattleChannelPreset preset;
    private int elapsedTicks;
    private ActionBattlePosition lastTargetablePosition;
    private int lastObservedHealth;
    private boolean completed;
    private ActionBattleChannelCancelReason queuedCancelReason;

    ActionBattleChannelState(UUID battleId, UUID casterPokemonUUID, UUID targetPokemonUUID, String moveId, ActionBattleChannelPreset preset,
                             ActionBattlePosition initialTargetPosition, int initialHealth) {
        this.battleId = battleId;
        this.casterPokemonUUID = casterPokemonUUID;
        this.targetPokemonUUID = targetPokemonUUID;
        this.moveId = moveId;
        this.preset = preset;
        this.lastTargetablePosition = initialTargetPosition;
        this.lastObservedHealth = Math.max(0, initialHealth);
    }

    void advance() { elapsedTicks++; }
    void markCompleted() { completed = true; queuedCancelReason = null; }
    void updateLastTargetablePosition(ActionBattlePosition position) { if (position != null) lastTargetablePosition = position; }
    void setLastObservedHealth(int health) { lastObservedHealth = Math.max(0, health); }
    void queueCancel(ActionBattleChannelCancelReason reason) { if (!completed && queuedCancelReason == null) queuedCancelReason = reason; }
    ActionBattleChannelCancelReason consumeQueuedCancel() { ActionBattleChannelCancelReason value = queuedCancelReason; queuedCancelReason = null; return value; }

    public UUID battleId() { return battleId; }
    public UUID casterPokemonUUID() { return casterPokemonUUID; }
    public UUID targetPokemonUUID() { return targetPokemonUUID; }
    public String moveId() { return moveId; }
    public ActionBattleChannelPreset preset() { return preset; }
    public int elapsedTicks() { return elapsedTicks; }
    public int remainingTicks() { return Math.max(0, preset.durationTicks() - elapsedTicks); }
    public ActionBattlePosition lastTargetablePosition() { return lastTargetablePosition; }
    public int lastObservedHealth() { return lastObservedHealth; }
    public boolean completed() { return completed; }
}
