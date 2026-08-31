package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
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
    private final Map<UUID, Long> pokemonMoveCooldownEndTicks = new HashMap<>();
    private final Map<UUID, Long> pokemonMoveCooldownDurationTicks = new HashMap<>();
    private final Map<UUID, Long> pokemonMovementCommandCooldownEndTicks = new HashMap<>();
    private final Map<UUID, Long> pokemonMovementCommandCooldownDurationTicks = new HashMap<>();
    private long playerSwapCooldownEndTick = 0L;
    private long playerSwapCooldownDurationTicks = 0L;
    private long trainerSwapCooldownEndTick = 0L;
    private long trainerSwapCooldownDurationTicks = 0L;
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
        playerMoveTargetPending = false;
        clearPlayerMoveCommandInternal();
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

    public void cancelTrainerOrders() {
        clearTrainerMoveCommand();
        resetTrainerRepositionState();
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
        if (state != ActionBattleState.ACTIVE || pokemonUUID == null || currentTick < 0L || durationTicks <= 0L) return false;
        pokemonMoveCooldownEndTicks.put(pokemonUUID, currentTick + durationTicks);
        pokemonMoveCooldownDurationTicks.put(pokemonUUID, durationTicks);
        return true;
    }

    public boolean isPokemonMoveOnCooldown(UUID pokemonUUID, long currentTick) {
        if (pokemonUUID == null || currentTick < 0L) return false;
        return currentTick < pokemonMoveCooldownEndTicks.getOrDefault(pokemonUUID, 0L);
    }

    public long pokemonMoveCooldownEndTick(UUID pokemonUUID) {
        return pokemonUUID != null ? pokemonMoveCooldownEndTicks.getOrDefault(pokemonUUID, 0L) : 0L;
    }

    public long pokemonMoveCooldownDurationTicks(UUID pokemonUUID) {
        return pokemonUUID != null ? pokemonMoveCooldownDurationTicks.getOrDefault(pokemonUUID, 0L) : 0L;
    }

    public boolean startPokemonMovementCommandCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || pokemonUUID == null || currentTick < 0L || durationTicks <= 0L) return false;
        pokemonMovementCommandCooldownEndTicks.put(pokemonUUID, currentTick + durationTicks);
        pokemonMovementCommandCooldownDurationTicks.put(pokemonUUID, durationTicks);
        return true;
    }

    public boolean isPokemonMovementCommandOnCooldown(UUID pokemonUUID, long currentTick) {
        if (pokemonUUID == null || currentTick < 0L) return false;
        return currentTick < pokemonMovementCommandCooldownEndTicks.getOrDefault(pokemonUUID, 0L);
    }

    public long pokemonMovementCommandCooldownEndTick(UUID pokemonUUID) {
        return pokemonUUID != null ? pokemonMovementCommandCooldownEndTicks.getOrDefault(pokemonUUID, 0L) : 0L;
    }

    public long pokemonMovementCommandCooldownDurationTicks(UUID pokemonUUID) {
        return pokemonUUID != null ? pokemonMovementCommandCooldownDurationTicks.getOrDefault(pokemonUUID, 0L) : 0L;
    }

    public boolean setPokemonAllCommandCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || pokemonUUID == null || currentTick < 0L || durationTicks <= 0L) return false;
        boolean playerPokemon = pokemonUUID.equals(playerActivePokemonUUID);
        boolean trainerPokemon = pokemonUUID.equals(trainerActivePokemonUUID);
        if (!playerPokemon && !trainerPokemon) return false;
        pokemonMoveCooldownEndTicks.put(pokemonUUID, safeAdd(currentTick, durationTicks));
        pokemonMoveCooldownDurationTicks.put(pokemonUUID, durationTicks);
        pokemonMovementCommandCooldownEndTicks.put(pokemonUUID, safeAdd(currentTick, durationTicks));
        pokemonMovementCommandCooldownDurationTicks.put(pokemonUUID, durationTicks);
        if (playerPokemon) {
            playerSwapCooldownEndTick = safeAdd(currentTick, durationTicks);
            playerSwapCooldownDurationTicks = durationTicks;
        } else {
            trainerSwapCooldownEndTick = safeAdd(currentTick, durationTicks);
            trainerSwapCooldownDurationTicks = durationTicks;
        }
        return true;
    }

    public boolean addPokemonCommandCooldownPenalty(UUID pokemonUUID, long currentTick, long penaltyTicks) {
        if (state != ActionBattleState.ACTIVE || pokemonUUID == null || currentTick < 0L || penaltyTicks <= 0L) return false;
        boolean playerPokemon = pokemonUUID.equals(playerActivePokemonUUID);
        boolean trainerPokemon = pokemonUUID.equals(trainerActivePokemonUUID);
        if (!playerPokemon && !trainerPokemon) return false;
        extendPokemonCooldown(pokemonMoveCooldownEndTicks, pokemonMoveCooldownDurationTicks, pokemonUUID, currentTick, penaltyTicks);
        extendPokemonCooldown(pokemonMovementCommandCooldownEndTicks, pokemonMovementCommandCooldownDurationTicks, pokemonUUID, currentTick, penaltyTicks);
        if (playerPokemon) {
            if (playerSwapCooldownEndTick > currentTick) {
                playerSwapCooldownEndTick = safeAdd(playerSwapCooldownEndTick, penaltyTicks);
                playerSwapCooldownDurationTicks = safeAdd(playerSwapCooldownDurationTicks, penaltyTicks);
            } else {
                playerSwapCooldownEndTick = safeAdd(currentTick, penaltyTicks);
                playerSwapCooldownDurationTicks = penaltyTicks;
            }
        } else {
            if (trainerSwapCooldownEndTick > currentTick) {
                trainerSwapCooldownEndTick = safeAdd(trainerSwapCooldownEndTick, penaltyTicks);
                trainerSwapCooldownDurationTicks = safeAdd(trainerSwapCooldownDurationTicks, penaltyTicks);
            } else {
                trainerSwapCooldownEndTick = safeAdd(currentTick, penaltyTicks);
                trainerSwapCooldownDurationTicks = penaltyTicks;
            }
        }
        return true;
    }

    private static void extendPokemonCooldown(Map<UUID, Long> endTicks, Map<UUID, Long> durationTicks, UUID pokemonUUID, long currentTick, long penaltyTicks) {
        long currentEnd = endTicks.getOrDefault(pokemonUUID, 0L);
        if (currentEnd > currentTick) {
            endTicks.put(pokemonUUID, safeAdd(currentEnd, penaltyTicks));
            durationTicks.put(pokemonUUID, safeAdd(durationTicks.getOrDefault(pokemonUUID, Math.max(0L, currentEnd - currentTick)), penaltyTicks));
        } else {
            endTicks.put(pokemonUUID, safeAdd(currentTick, penaltyTicks));
            durationTicks.put(pokemonUUID, penaltyTicks);
        }
    }

    private static long safeAdd(long left, long right) {
        if (left < 0L || right < 0L) return Long.MAX_VALUE;
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    public boolean startPlayerSwapCooldown(long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || currentTick < 0L || durationTicks <= 0L) return false;
        playerSwapCooldownEndTick = currentTick + durationTicks;
        playerSwapCooldownDurationTicks = durationTicks;
        return true;
    }

    public boolean isPlayerSwapOnCooldown(long currentTick) {
        return currentTick >= 0L && currentTick < playerSwapCooldownEndTick;
    }

    public long playerSwapCooldownEndTick() { return playerSwapCooldownEndTick; }
    public long playerSwapCooldownDurationTicks() { return playerSwapCooldownDurationTicks; }

    public boolean startTrainerSwapCooldown(long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || currentTick < 0L || durationTicks <= 0L) return false;
        trainerSwapCooldownEndTick = currentTick + durationTicks;
        trainerSwapCooldownDurationTicks = durationTicks;
        return true;
    }

    public boolean isTrainerSwapOnCooldown(long currentTick) {
        return currentTick >= 0L && currentTick < trainerSwapCooldownEndTick;
    }

    public long trainerSwapCooldownEndTick() { return trainerSwapCooldownEndTick; }
    public long trainerSwapCooldownDurationTicks() { return trainerSwapCooldownDurationTicks; }
    public boolean isPlayerSendOutPending() { return playerSendOutPending; }
    public boolean isTrainerSendOutPending() { return trainerSendOutPending; }
    public void setPlayerSendOutPending(boolean pending) { playerSendOutPending = pending; }
    public void setTrainerSendOutPending(boolean pending) { trainerSendOutPending = pending; }


    public boolean activateHaze(long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || currentTick < 0L || durationTicks <= 0L) return false;
        hazeExpiresAtTick = safeAdd(currentTick, durationTicks);
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
    public long playerMoveCommandRevision() { return playerCommandRevision; }
    public long playerCommandRevision() { return playerCommandRevision; }
    public boolean hasPlayerMoveCommand() { return playerMoveCommandPending; }
    public int playerMoveSlot() { return playerMoveSlot; }
    public UUID playerMoveTargetEntityUUID() { return playerMoveTargetEntityUUID; }
    public long trainerCommandRevision() { return trainerCommandRevision; }
    public boolean hasTrainerMoveCommand() { return trainerMoveCommandPending; }
    public int trainerMoveSlot() { return trainerMoveSlot; }
    public UUID trainerMoveTargetEntityUUID() { return trainerMoveTargetEntityUUID; }
    public int trainerRepositionAttempt() { return trainerRepositionAttempt; }
    public boolean hasTrainerRepositionTarget() { return trainerRepositionTargetPending; }
    public double trainerRepositionTargetX() { return trainerRepositionTargetX; }
    public double trainerRepositionTargetY() { return trainerRepositionTargetY; }
    public double trainerRepositionTargetZ() { return trainerRepositionTargetZ; }
}
