package net.epiac9.cobblemonnml.battle.action;

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
}
