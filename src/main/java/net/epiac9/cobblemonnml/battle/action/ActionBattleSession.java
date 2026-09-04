package net.epiac9.cobblemonnml.battle.action;

import java.util.UUID;

public final class ActionBattleSession {
    private final UUID battleId;
    private final UUID dungeonSessionId;
    private final UUID playerUUID;
    private final UUID trainerUUID;
    private final String runtimeTrainerId;
    private final String trainerPreset;
    private final ActionBattleZone battleZone;
    private ActionBattleState state = ActionBattleState.ACTIVE;
    private ActionBattleResult result = null;
    private int playerActivePartyIndex = -1;
    private int trainerActivePartyIndex = -1;
    private UUID playerActivePokemonUUID;
    private UUID trainerActivePokemonUUID;
    private UUID playerActiveEntityUUID;
    private UUID trainerActiveEntityUUID;
    private boolean playerMoveTargetPending = false;
    private double playerMoveTargetX;
    private double playerMoveTargetY;
    private double playerMoveTargetZ;
    private long playerCommandRevision = 0L;
    private boolean playerMoveCommandPending = false;
    private int playerMoveSlot = -1;
    private UUID playerMoveTargetEntityUUID;
    private long trainerCommandRevision = 0L;
    private boolean trainerMoveCommandPending = false;
    private int trainerMoveSlot = -1;
    private UUID trainerMoveTargetEntityUUID;
    private int trainerRepositionAttempt = 0;
    private boolean trainerRepositionTargetPending = false;
    private double trainerRepositionTargetX;
    private double trainerRepositionTargetY;
    private double trainerRepositionTargetZ;
    private final ActionBattleCommandCooldownState commandCooldowns = new ActionBattleCommandCooldownState();
    private boolean playerSendOutPending = false;
    private boolean trainerSendOutPending = false;
    private long hazeExpiresAtTick = 0L;

    public ActionBattleSession(UUID battleId, UUID dungeonSessionId, UUID playerUUID, UUID trainerUUID, String runtimeTrainerId, String trainerPreset) {
        this(battleId, dungeonSessionId, playerUUID, trainerUUID, runtimeTrainerId, trainerPreset, new ActionBattleZone(0.0D, 0.0D, 20.0D));
    }

    public ActionBattleSession(UUID battleId, UUID dungeonSessionId, UUID playerUUID, UUID trainerUUID, String runtimeTrainerId, String trainerPreset, ActionBattleZone battleZone) {
        if (battleId == null || dungeonSessionId == null || playerUUID == null || trainerUUID == null || battleZone == null) {
            throw new IllegalArgumentException("Action battle identity and zone values cannot be null.");
        }
        if (runtimeTrainerId == null || runtimeTrainerId.isBlank()) {
            throw new IllegalArgumentException("Action battle runtime trainer ID cannot be blank.");
        }
        this.battleId = battleId;
        this.dungeonSessionId = dungeonSessionId;
        this.playerUUID = playerUUID;
        this.trainerUUID = trainerUUID;
        this.runtimeTrainerId = runtimeTrainerId;
        this.trainerPreset = trainerPreset;
        this.battleZone = battleZone;
    }

    public boolean bindPlayerActivePokemon(int partyIndex, UUID pokemonUUID, UUID entityUUID) {
        if (state != ActionBattleState.ACTIVE || partyIndex < 0 || pokemonUUID == null || entityUUID == null) return false;
        playerActivePartyIndex = partyIndex;
        playerActivePokemonUUID = pokemonUUID;
        playerActiveEntityUUID = entityUUID;
        return true;
    }

    public boolean bindTrainerActivePokemon(int partyIndex, UUID pokemonUUID, UUID entityUUID) {
        if (state != ActionBattleState.ACTIVE || partyIndex < 0 || pokemonUUID == null || entityUUID == null) return false;
        trainerActivePartyIndex = partyIndex;
        trainerActivePokemonUUID = pokemonUUID;
        trainerActiveEntityUUID = entityUUID;
        return true;
    }

    public void clearPlayerActivePokemon() {
        playerActivePartyIndex = -1;
        playerActivePokemonUUID = null;
        playerActiveEntityUUID = null;
    }

    public void clearTrainerActivePokemon() {
        trainerActivePartyIndex = -1;
        trainerActivePokemonUUID = null;
        trainerActiveEntityUUID = null;
    }

    public long replacePlayerMoveTarget(double x, double y, double z) {
        if (state != ActionBattleState.ACTIVE || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return playerCommandRevision;
        playerMoveTargetX = x;
        playerMoveTargetY = y;
        playerMoveTargetZ = z;
        playerMoveTargetPending = true;
        clearPlayerMoveCommandInternal();
        return ++playerCommandRevision;
    }

    public void clearPlayerMoveTarget() {
        playerMoveTargetPending = false;
    }

    public void clearPlayerMoveState() {
        clearPlayerMoveTarget();
        clearPlayerMoveCommandInternal();
    }

    public long replacePlayerMoveCommand(int moveSlot, UUID targetEntityUUID) {
        if (state != ActionBattleState.ACTIVE || moveSlot < 0 || moveSlot > 3 || targetEntityUUID == null) return playerCommandRevision;
        playerMoveTargetPending = false;
        playerMoveCommandPending = true;
        playerMoveSlot = moveSlot;
        playerMoveTargetEntityUUID = targetEntityUUID;
        return ++playerCommandRevision;
    }

    public void clearPlayerMoveCommand() {
        clearPlayerMoveCommandInternal();
    }

    public void cancelPlayerOrders() {
        clearPlayerMoveState();
    }

    public long replaceTrainerMoveCommand(int moveSlot, UUID targetEntityUUID) {
        if (state != ActionBattleState.ACTIVE || moveSlot < 0 || moveSlot > 3 || targetEntityUUID == null) return trainerCommandRevision;
        trainerMoveCommandPending = true;
        trainerMoveSlot = moveSlot;
        trainerMoveTargetEntityUUID = targetEntityUUID;
        return ++trainerCommandRevision;
    }

    public void clearTrainerMoveCommand() {
        trainerMoveCommandPending = false;
        trainerMoveSlot = -1;
        trainerMoveTargetEntityUUID = null;
    }

    public void clearTrainerMoveState() {
        clearTrainerMoveCommand();
        resetTrainerRepositionState();
    }

    public void cancelTrainerOrders() {
        clearTrainerMoveState();
    }

    public void setTrainerRepositionTarget(double x, double y, double z) {
        if (state != ActionBattleState.ACTIVE || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        trainerRepositionTargetX = x;
        trainerRepositionTargetY = y;
        trainerRepositionTargetZ = z;
        trainerRepositionTargetPending = true;
    }

    public void clearTrainerRepositionTarget() { trainerRepositionTargetPending = false; }

    public int advanceTrainerRepositionAttempt() {
        trainerRepositionTargetPending = false;
        return ++trainerRepositionAttempt;
    }

    public void resetTrainerRepositionState() {
        trainerRepositionAttempt = 0;
        trainerRepositionTargetPending = false;
    }

    private void clearPlayerMoveCommandInternal() {
        playerMoveCommandPending = false;
        playerMoveSlot = -1;
        playerMoveTargetEntityUUID = null;
    }


    public boolean startPokemonMoveCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE && commandCooldowns.startMove(pokemonUUID, currentTick, durationTicks);
    }

    public boolean isPokemonMoveOnCooldown(UUID pokemonUUID, long currentTick) { return commandCooldowns.moveOnCooldown(pokemonUUID, currentTick); }
    public long pokemonMoveCooldownEndTick(UUID pokemonUUID) { return commandCooldowns.moveEndTick(pokemonUUID); }
    public long pokemonMoveCooldownDurationTicks(UUID pokemonUUID) { return commandCooldowns.moveDurationTicks(pokemonUUID); }

    public boolean startPokemonMovementCommandCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE && commandCooldowns.startMovement(pokemonUUID, currentTick, durationTicks);
    }

    public boolean isPokemonMovementCommandOnCooldown(UUID pokemonUUID, long currentTick) { return commandCooldowns.movementOnCooldown(pokemonUUID, currentTick); }
    public long pokemonMovementCommandCooldownEndTick(UUID pokemonUUID) { return commandCooldowns.movementEndTick(pokemonUUID); }
    public long pokemonMovementCommandCooldownDurationTicks(UUID pokemonUUID) { return commandCooldowns.movementDurationTicks(pokemonUUID); }

    public boolean setPokemonAllCommandCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE) return false;
        ActionBattleCommandCooldownState.Side side = cooldownSide(pokemonUUID);
        return side != null && commandCooldowns.setAll(pokemonUUID, side, currentTick, durationTicks);
    }

    public boolean addPokemonCommandCooldownPenalty(UUID pokemonUUID, long currentTick, long penaltyTicks) {
        if (state != ActionBattleState.ACTIVE) return false;
        ActionBattleCommandCooldownState.Side side = cooldownSide(pokemonUUID);
        return side != null && commandCooldowns.addPenalty(pokemonUUID, side, currentTick, penaltyTicks);
    }

    public boolean addPokemonMovementCooldownPenalty(UUID pokemonUUID, long currentTick, long penaltyTicks) {
        return state == ActionBattleState.ACTIVE
                && cooldownSide(pokemonUUID) != null
                && commandCooldowns.addMovementPenalty(pokemonUUID, currentTick, penaltyTicks);
    }

    public boolean startPlayerSwapCooldown(long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE && commandCooldowns.startSwap(ActionBattleCommandCooldownState.Side.PLAYER, currentTick, durationTicks);
    }

    public boolean isPlayerSwapOnCooldown(long currentTick) { return commandCooldowns.swapOnCooldown(ActionBattleCommandCooldownState.Side.PLAYER, currentTick); }
    public long playerSwapCooldownEndTick() { return commandCooldowns.swapEndTick(ActionBattleCommandCooldownState.Side.PLAYER); }
    public long playerSwapCooldownDurationTicks() { return commandCooldowns.swapDurationTicks(ActionBattleCommandCooldownState.Side.PLAYER); }

    public boolean startTrainerSwapCooldown(long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE && commandCooldowns.startSwap(ActionBattleCommandCooldownState.Side.TRAINER, currentTick, durationTicks);
    }

    public boolean isTrainerSwapOnCooldown(long currentTick) { return commandCooldowns.swapOnCooldown(ActionBattleCommandCooldownState.Side.TRAINER, currentTick); }
    public long trainerSwapCooldownEndTick() { return commandCooldowns.swapEndTick(ActionBattleCommandCooldownState.Side.TRAINER); }
    public long trainerSwapCooldownDurationTicks() { return commandCooldowns.swapDurationTicks(ActionBattleCommandCooldownState.Side.TRAINER); }

    private ActionBattleCommandCooldownState.Side cooldownSide(UUID pokemonUUID) {
        if (pokemonUUID == null) return null;
        if (pokemonUUID.equals(playerActivePokemonUUID)) return ActionBattleCommandCooldownState.Side.PLAYER;
        if (pokemonUUID.equals(trainerActivePokemonUUID)) return ActionBattleCommandCooldownState.Side.TRAINER;
        return null;
    }

    public boolean isPlayerSendOutPending() { return playerSendOutPending; }
    public boolean isTrainerSendOutPending() { return trainerSendOutPending; }
    public void setPlayerSendOutPending(boolean pending) { playerSendOutPending = pending; }
    public void setTrainerSendOutPending(boolean pending) { trainerSendOutPending = pending; }


    public boolean activateHaze(long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || currentTick < 0L || durationTicks <= 0L) return false;
        hazeExpiresAtTick = ActionBattleTiming.safeAdd(currentTick, durationTicks);
        return true;
    }

    public boolean isHazeActive(long currentTick) {
        return state == ActionBattleState.ACTIVE && currentTick >= 0L && currentTick < hazeExpiresAtTick;
    }

    public long hazeExpiresAtTick() { return hazeExpiresAtTick; }

    public long hazeRemainingTicks(long currentTick) {
        return isHazeActive(currentTick) ? Math.max(0L, hazeExpiresAtTick - currentTick) : 0L;
    }

    public boolean end(ActionBattleResult result) {
        if (state == ActionBattleState.ENDED || result == null) return false;
        this.result = result;
        this.state = ActionBattleState.ENDED;
        return true;
    }

    public UUID battleId() { return battleId; }
    public UUID dungeonSessionId() { return dungeonSessionId; }
    public UUID playerUUID() { return playerUUID; }
    public UUID trainerUUID() { return trainerUUID; }
    public String runtimeTrainerId() { return runtimeTrainerId; }
    public String trainerPreset() { return trainerPreset; }
    public ActionBattleZone battleZone() { return battleZone; }
    public ActionBattleState state() { return state; }
    public ActionBattleResult result() { return result; }
    public int playerActivePartyIndex() { return playerActivePartyIndex; }
    public int trainerActivePartyIndex() { return trainerActivePartyIndex; }
    public UUID playerActivePokemonUUID() { return playerActivePokemonUUID; }
    public UUID trainerActivePokemonUUID() { return trainerActivePokemonUUID; }
    public UUID playerActiveEntityUUID() { return playerActiveEntityUUID; }
    public UUID trainerActiveEntityUUID() { return trainerActiveEntityUUID; }
    public boolean hasPlayerMoveTarget() { return playerMoveTargetPending; }
    public double playerMoveTargetX() { return playerMoveTargetX; }
    public double playerMoveTargetY() { return playerMoveTargetY; }
    public double playerMoveTargetZ() { return playerMoveTargetZ; }
    public long playerCommandRevision() { return playerCommandRevision; }
    public boolean hasPlayerMoveCommand() { return playerMoveCommandPending; }
    public boolean hasPlayerMovementIntent() { return hasPlayerMoveTarget() || hasPlayerMoveCommand(); }
    public int playerMoveSlot() { return playerMoveSlot; }
    public UUID playerMoveTargetEntityUUID() { return playerMoveTargetEntityUUID; }
    public long trainerCommandRevision() { return trainerCommandRevision; }
    public boolean hasTrainerMoveCommand() { return trainerMoveCommandPending; }
    public boolean hasTrainerMovementIntent() { return hasTrainerMoveCommand() || hasTrainerRepositionTarget(); }
    public int trainerMoveSlot() { return trainerMoveSlot; }
    public UUID trainerMoveTargetEntityUUID() { return trainerMoveTargetEntityUUID; }
    public int trainerRepositionAttempt() { return trainerRepositionAttempt; }
    public boolean hasTrainerRepositionTarget() { return trainerRepositionTargetPending; }
    public double trainerRepositionTargetX() { return trainerRepositionTargetX; }
    public double trainerRepositionTargetY() { return trainerRepositionTargetY; }
    public double trainerRepositionTargetZ() { return trainerRepositionTargetZ; }
}
