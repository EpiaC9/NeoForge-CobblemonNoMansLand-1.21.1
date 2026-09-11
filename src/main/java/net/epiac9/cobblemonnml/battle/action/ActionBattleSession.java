package net.epiac9.cobblemonnml.battle.action;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleSession {
    private final UUID battleId;
    private final UUID dungeonSessionId;
    private final UUID playerUUID;
    private final UUID trainerUUID;
    private final String runtimeTrainerId;
    private final String trainerPreset;
    private final ActionBattleZone battleZone;
    private final ActionBattleArena arena;
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
    private long playerMoveCommitReadySinceTick = -1L;
    private long trainerCommandRevision = 0L;
    private boolean trainerMoveCommandPending = false;
    private int trainerMoveSlot = -1;
    private UUID trainerMoveTargetEntityUUID;
    private long trainerMoveCommitReadySinceTick = -1L;
    private int trainerRepositionAttempt = 0;
    private boolean trainerRepositionTargetPending = false;
    private double trainerRepositionTargetX;
    private double trainerRepositionTargetY;
    private double trainerRepositionTargetZ;
    private final ActionBattleCommandCooldownState commandCooldowns = new ActionBattleCommandCooldownState();
    private boolean playerSendOutPending = false;
    private boolean trainerSendOutPending = false;
    private long hazeExpiresAtTick = 0L;
    private final Map<UUID, AdditionalPlayerState> additionalPlayers = new HashMap<>();
    private final Map<UUID, Vec3> lastAcceptedMoveHereDirectives = new HashMap<>();
    private long lastBackgroundTick = Long.MIN_VALUE;
    private long lastPhysicalTick = Long.MIN_VALUE;

    private static final class AdditionalPlayerState {
        int activePartyIndex = -1;
        UUID activePokemonUUID;
        UUID activeEntityUUID;
        boolean moveTargetPending;
        double moveTargetX;
        double moveTargetY;
        double moveTargetZ;
        long commandRevision;
        boolean moveCommandPending;
        int moveSlot = -1;
        UUID moveTargetEntityUUID;
        long moveCommitReadySinceTick = -1L;
        boolean sendOutPending;
    }

    public boolean joinPlayer(UUID joiningPlayerUUID) {
        return joiningPlayerUUID != null && (joiningPlayerUUID.equals(playerUUID)
                || additionalPlayers.putIfAbsent(joiningPlayerUUID, new AdditionalPlayerState()) == null);
    }

    public boolean hasPlayer(UUID candidate) {
        return candidate != null && (candidate.equals(playerUUID) || additionalPlayers.containsKey(candidate));
    }

    public java.util.Set<UUID> playerUUIDs() {
        java.util.HashSet<UUID> players = new java.util.HashSet<>(additionalPlayers.keySet());
        players.add(playerUUID);
        return java.util.Set.copyOf(players);
    }

    public boolean bindPlayerActivePokemon(UUID ownerUUID, int partyIndex, UUID pokemonUUID, UUID entityUUID) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) return bindPlayerActivePokemon(partyIndex, pokemonUUID, entityUUID);
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (state != ActionBattleState.ACTIVE || player == null || partyIndex < 0 || pokemonUUID == null || entityUUID == null) return false;
        player.activePartyIndex = partyIndex;
        player.activePokemonUUID = pokemonUUID;
        player.activeEntityUUID = entityUUID;
        return true;
    }

    public void clearPlayerActivePokemon(UUID ownerUUID) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) { clearPlayerActivePokemon(); return; }
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (player == null) return;
        player.activePartyIndex = -1;
        player.activePokemonUUID = null;
        player.activeEntityUUID = null;
    }

    public long replacePlayerMoveTarget(UUID ownerUUID, double x, double y, double z) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) return replacePlayerMoveTarget(x, y, z);
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (state != ActionBattleState.ACTIVE || player == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return 0L;
        player.moveTargetX = x;
        player.moveTargetY = y;
        player.moveTargetZ = z;
        player.moveTargetPending = true;
        player.moveCommandPending = false;
        player.moveSlot = -1;
        player.moveTargetEntityUUID = null;
        player.moveCommitReadySinceTick = -1L;
        if (player.activePokemonUUID != null) lastAcceptedMoveHereDirectives.put(
                player.activePokemonUUID, new Vec3(x, y, z));
        return ++player.commandRevision;
    }

    public long replacePlayerMoveCommand(UUID ownerUUID, int moveSlot, UUID targetEntityUUID) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) return replacePlayerMoveCommand(moveSlot, targetEntityUUID);
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (state != ActionBattleState.ACTIVE || player == null || moveSlot < 0 || moveSlot > 3 || targetEntityUUID == null) return 0L;
        player.moveTargetPending = false;
        player.moveCommandPending = true;
        player.moveSlot = moveSlot;
        player.moveTargetEntityUUID = targetEntityUUID;
        player.moveCommitReadySinceTick = -1L;
        return ++player.commandRevision;
    }

    public void clearPlayerMoveTarget(UUID ownerUUID) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) { clearPlayerMoveTarget(); return; }
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (player != null) player.moveTargetPending = false;
    }

    public void clearPlayerMoveCommand(UUID ownerUUID) {
        if (ownerUUID == null || ownerUUID.equals(playerUUID)) { clearPlayerMoveCommand(); return; }
        AdditionalPlayerState player = additionalPlayers.get(ownerUUID);
        if (player == null) return;
        player.moveCommandPending = false;
        player.moveSlot = -1;
        player.moveTargetEntityUUID = null;
        player.moveCommitReadySinceTick = -1L;
    }

    public void clearPlayerMoveState(UUID ownerUUID) {
        clearPlayerMoveTarget(ownerUUID);
        clearPlayerMoveCommand(ownerUUID);
    }

    public boolean hasPlayerMoveCommand(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveCommandPending : ownerUUID != null && ownerUUID.equals(playerUUID) && playerMoveCommandPending;
    }

    public int playerMoveSlot(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveSlot : ownerUUID != null && ownerUUID.equals(playerUUID) ? playerMoveSlot : -1;
    }

    public UUID playerMoveTargetEntityUUID(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveTargetEntityUUID : ownerUUID != null && ownerUUID.equals(playerUUID) ? playerMoveTargetEntityUUID : null;
    }

    public boolean hasPlayerMoveTarget(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveTargetPending : ownerUUID != null && ownerUUID.equals(playerUUID) && playerMoveTargetPending;
    }

    public boolean hasPlayerMovementIntent(UUID ownerUUID) {
        return hasPlayerMoveTarget(ownerUUID) || hasPlayerMoveCommand(ownerUUID);
    }

    public boolean hasPokemonMovementIntent(UUID pokemonUUID) {
        UUID ownerUUID = playerOwnerForPokemon(pokemonUUID);
        if (ownerUUID != null) return hasPlayerMovementIntent(ownerUUID);
        return pokemonUUID != null && pokemonUUID.equals(trainerActivePokemonUUID)
                && hasTrainerMovementIntent();
    }

    public boolean hasDirectionalMovementIntent(UUID pokemonUUID) {
        UUID ownerUUID = playerOwnerForPokemon(pokemonUUID);
        if (ownerUUID != null) return hasPlayerMoveTarget(ownerUUID);
        return pokemonUUID != null && pokemonUUID.equals(trainerActivePokemonUUID)
                && hasTrainerRepositionTarget();
    }

    public UUID playerOwnerForPokemon(UUID pokemonUUID) {
        if (pokemonUUID == null) return null;
        if (pokemonUUID.equals(playerActivePokemonUUID)) return playerUUID;
        for (Map.Entry<UUID, AdditionalPlayerState> entry : additionalPlayers.entrySet()) {
            if (pokemonUUID.equals(entry.getValue().activePokemonUUID)) return entry.getKey();
        }
        return null;
    }

    public UUID playerEntityForPokemon(UUID pokemonUUID) {
        UUID owner = playerOwnerForPokemon(pokemonUUID);
        return owner != null ? playerActiveEntityUUID(owner) : null;
    }

    public boolean isPlayerPokemon(UUID pokemonUUID) { return playerOwnerForPokemon(pokemonUUID) != null; }

    public double playerMoveTargetX(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveTargetX : playerMoveTargetX;
    }
    public double playerMoveTargetY(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveTargetY : playerMoveTargetY;
    }
    public double playerMoveTargetZ(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.moveTargetZ : playerMoveTargetZ;
    }

    public Optional<Vec3> lastAcceptedMoveHereDirective(UUID pokemonId) {
        return Optional.ofNullable(pokemonId != null ? lastAcceptedMoveHereDirectives.get(pokemonId) : null);
    }

    public void clearLastAcceptedMoveHereDirective(UUID pokemonId) {
        if (pokemonId != null) lastAcceptedMoveHereDirectives.remove(pokemonId);
    }

    public int playerActivePartyIndex(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.activePartyIndex : ownerUUID != null && ownerUUID.equals(playerUUID) ? playerActivePartyIndex : -1;
    }

    public UUID playerActivePokemonUUID(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.activePokemonUUID : ownerUUID != null && ownerUUID.equals(playerUUID) ? playerActivePokemonUUID : null;
    }

    public UUID playerActiveEntityUUID(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.activeEntityUUID : ownerUUID != null && ownerUUID.equals(playerUUID) ? playerActiveEntityUUID : null;
    }

    public boolean isPlayerSendOutPending(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        return player != null ? player.sendOutPending : ownerUUID != null && ownerUUID.equals(playerUUID) && playerSendOutPending;
    }

    public void setPlayerSendOutPending(UUID ownerUUID, boolean pending) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        if (player != null) player.sendOutPending = pending;
        else if (ownerUUID != null && ownerUUID.equals(playerUUID)) playerSendOutPending = pending;
    }

    public boolean claimBackgroundTick(long currentTick) {
        if (currentTick < 0L || currentTick == lastBackgroundTick) return false;
        lastBackgroundTick = currentTick;
        return true;
    }

    public boolean claimPhysicalTick(long currentTick) {
        if (currentTick < 0L || currentTick == lastPhysicalTick) return false;
        lastPhysicalTick = currentTick;
        return true;
    }

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
        this.arena = null;
    }

    public ActionBattleSession(UUID battleId, UUID dungeonSessionId, UUID playerUUID, UUID trainerUUID,
                               String runtimeTrainerId, String trainerPreset, String roomId,
                               ActionBattleRoomBounds roomBounds, boolean playerInitiallyInside) {
        if (battleId == null || dungeonSessionId == null || playerUUID == null || trainerUUID == null || roomBounds == null) {
            throw new IllegalArgumentException("Action battle identity and room bounds cannot be null.");
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
        this.battleZone = null;
        this.arena = new ActionBattleArena(battleId, dungeonSessionId, roomId, roomBounds,
                playerUUID, playerInitiallyInside);
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
        if (playerActivePokemonUUID != null) lastAcceptedMoveHereDirectives.put(
                playerActivePokemonUUID, new Vec3(x, y, z));
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
        playerMoveCommitReadySinceTick = -1L;
        return ++playerCommandRevision;
    }

    public void clearPlayerMoveCommand() {
        clearPlayerMoveCommandInternal();
    }

    public void cancelPlayerOrders() {
        clearPlayerMoveState();
    }

    public boolean cancelPlayerOrdersForSleep() {
        boolean hadOrders = hasPlayerMovementIntent();
        clearPlayerMoveState();
        return hadOrders;
    }

    public long replaceTrainerMoveCommand(int moveSlot, UUID targetEntityUUID) {
        if (state != ActionBattleState.ACTIVE || moveSlot < 0 || moveSlot > 3 || targetEntityUUID == null) return trainerCommandRevision;
        trainerMoveCommandPending = true;
        trainerMoveSlot = moveSlot;
        trainerMoveTargetEntityUUID = targetEntityUUID;
        trainerMoveCommitReadySinceTick = -1L;
        return ++trainerCommandRevision;
    }

    public void clearTrainerMoveCommand() {
        trainerMoveCommandPending = false;
        trainerMoveSlot = -1;
        trainerMoveTargetEntityUUID = null;
        trainerMoveCommitReadySinceTick = -1L;
    }

    public void clearTrainerMoveState() {
        clearTrainerMoveCommand();
        resetTrainerRepositionState();
    }

    public void cancelTrainerOrders() {
        clearTrainerMoveState();
    }

    public boolean cancelTrainerOrdersForSleep() {
        boolean hadOrders = hasTrainerMovementIntent();
        clearTrainerMoveCommand();
        clearTrainerRepositionTarget();
        return hadOrders;
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
        playerMoveCommitReadySinceTick = -1L;
    }

    public long markPlayerMoveCommitReady(UUID ownerUUID, long currentTick) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        if (player != null) {
            if (player.moveCommitReadySinceTick < 0L) player.moveCommitReadySinceTick = currentTick;
            return player.moveCommitReadySinceTick;
        }
        if (ownerUUID == null || !ownerUUID.equals(playerUUID)) return -1L;
        if (playerMoveCommitReadySinceTick < 0L) playerMoveCommitReadySinceTick = currentTick;
        return playerMoveCommitReadySinceTick;
    }

    public void resetPlayerMoveCommitReady(UUID ownerUUID) {
        AdditionalPlayerState player = ownerUUID != null ? additionalPlayers.get(ownerUUID) : null;
        if (player != null) { player.moveCommitReadySinceTick = -1L; return; }
        if (ownerUUID != null && ownerUUID.equals(playerUUID)) playerMoveCommitReadySinceTick = -1L;
    }

    public long markTrainerMoveCommitReady(long currentTick) {
        if (trainerMoveCommitReadySinceTick < 0L) trainerMoveCommitReadySinceTick = currentTick;
        return trainerMoveCommitReadySinceTick;
    }

    public void resetTrainerMoveCommitReady() { trainerMoveCommitReadySinceTick = -1L; }


    public boolean startPokemonMoveCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE && commandCooldowns.startMove(pokemonUUID, currentTick, durationTicks);
    }

    public boolean isPokemonMoveOnCooldown(UUID pokemonUUID, long currentTick) { return commandCooldowns.moveOnCooldown(pokemonUUID, currentTick); }
    public long pokemonMoveCooldownEndTick(UUID pokemonUUID) { return commandCooldowns.moveEndTick(pokemonUUID); }
    public long pokemonMoveCooldownDurationTicks(UUID pokemonUUID) { return commandCooldowns.moveDurationTicks(pokemonUUID); }

    public boolean startPokemonSharedAbilityCooldown(UUID pokemonUUID, long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE
                && commandCooldowns.startSharedAbility(pokemonUUID, currentTick, durationTicks);
    }

    public boolean isPokemonSharedAbilityOnCooldown(UUID pokemonUUID, long currentTick) {
        return commandCooldowns.sharedAbilityOnCooldown(pokemonUUID, currentTick);
    }

    public boolean clearPokemonSharedAbilityCooldown(UUID pokemonUUID) {
        return commandCooldowns.clearSharedAbility(pokemonUUID);
    }

    public boolean startPokemonPersonalMoveCooldown(UUID pokemonUUID, int moveSlot,
                                                     long currentTick, long durationTicks) {
        return state == ActionBattleState.ACTIVE
                && commandCooldowns.startPersonalMove(pokemonUUID, moveSlot, currentTick, durationTicks);
    }

    public boolean isPokemonPersonalMoveOnCooldown(UUID pokemonUUID, int moveSlot, long currentTick) {
        return commandCooldowns.personalMoveOnCooldown(pokemonUUID, moveSlot, currentTick);
    }

    public boolean clearPokemonPersonalMoveCooldown(UUID pokemonUUID, int moveSlot) {
        return commandCooldowns.clearPersonalMove(pokemonUUID, moveSlot);
    }

    public boolean clearPokemonPersonalMoveCooldowns(UUID pokemonUUID) {
        return commandCooldowns.clearAllPersonalMoves(pokemonUUID);
    }

    public boolean clearPokemonMovementCommandCooldown(UUID pokemonUUID) {
        return commandCooldowns.clearMovement(pokemonUUID);
    }

    public boolean clearPokemonSwapCooldown(UUID pokemonUUID) {
        ActionBattleCommandCooldownState.Side side = cooldownSide(pokemonUUID);
        return side != null && commandCooldowns.clearSwap(side);
    }

    public void clearPokemonAllCommandCooldowns(UUID pokemonUUID) {
        clearPokemonSharedAbilityCooldown(pokemonUUID);
        clearPokemonPersonalMoveCooldowns(pokemonUUID);
        clearPokemonMovementCommandCooldown(pokemonUUID);
        clearPokemonSwapCooldown(pokemonUUID);
    }

    public boolean isPokemonAbilitySlotOnCooldown(UUID pokemonUUID, int moveSlot, long currentTick) {
        return isPokemonSharedAbilityOnCooldown(pokemonUUID, currentTick)
                || isPokemonPersonalMoveOnCooldown(pokemonUUID, moveSlot, currentTick);
    }

    public long pokemonAbilitySlotCooldownRemainingTicks(UUID pokemonUUID, int moveSlot, long currentTick) {
        return commandCooldowns.effectiveMoveCooldown(pokemonUUID, moveSlot, currentTick).remainingTicks();
    }

    public long pokemonAbilitySlotCooldownDurationTicks(UUID pokemonUUID, int moveSlot, long currentTick) {
        return commandCooldowns.effectiveMoveCooldown(pokemonUUID, moveSlot, currentTick).durationTicks();
    }

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

    public boolean addPokemonAbilityCooldownPenalty(UUID pokemonUUID, long currentTick, long penaltyTicks) {
        return state == ActionBattleState.ACTIVE
                && cooldownSide(pokemonUUID) != null
                && commandCooldowns.addAbilityPenalty(pokemonUUID, currentTick, penaltyTicks);
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
        if (isPlayerPokemon(pokemonUUID)) return ActionBattleCommandCooldownState.Side.PLAYER;
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
    public ActionBattleArena arena() { return arena; }
    public boolean containsArena(double x, double z) {
        return arena != null ? arena.contains(x, z) : battleZone != null && battleZone.contains(x, z);
    }
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
