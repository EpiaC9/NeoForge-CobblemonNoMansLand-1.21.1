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
    private final Map<UUID, Long> pokemonMoveCooldownEndTicks = new HashMap<>();
    private final Map<UUID, Long> pokemonMoveCooldownDurationTicks = new HashMap<>();
    private long playerSwapCooldownEndTick = 0L;
    private boolean playerSendOutPending = false;
    private boolean trainerSendOutPending = false;

    public ActionBattleSession(UUID battleId, UUID dungeonSessionId, UUID playerUUID, UUID trainerUUID, String runtimeTrainerId, String trainerPreset) {
        if (battleId == null || dungeonSessionId == null || playerUUID == null || trainerUUID == null) {
            throw new IllegalArgumentException("Action battle identity values cannot be null.");
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

    public boolean startPlayerSwapCooldown(long currentTick, long durationTicks) {
        if (state != ActionBattleState.ACTIVE || currentTick < 0L || durationTicks <= 0L) return false;
        playerSwapCooldownEndTick = currentTick + durationTicks;
        return true;
    }

    public boolean isPlayerSwapOnCooldown(long currentTick) {
        return currentTick >= 0L && currentTick < playerSwapCooldownEndTick;
    }

    public long playerSwapCooldownEndTick() { return playerSwapCooldownEndTick; }
    public boolean isPlayerSendOutPending() { return playerSendOutPending; }
    public boolean isTrainerSendOutPending() { return trainerSendOutPending; }
    public void setPlayerSendOutPending(boolean pending) { playerSendOutPending = pending; }
    public void setTrainerSendOutPending(boolean pending) { trainerSendOutPending = pending; }

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
}
