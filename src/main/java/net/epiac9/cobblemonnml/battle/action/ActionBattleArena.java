package net.epiac9.cobblemonnml.battle.action;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleArena {
    private final UUID battleId;
    private final UUID dungeonSessionId;
    private final String roomId;
    private final ActionBattleRoomBounds roomBounds;
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> inside = new HashSet<>();
    private boolean active = true;

    public ActionBattleArena(UUID battleId, UUID dungeonSessionId, String roomId,
                             ActionBattleRoomBounds roomBounds, UUID initialPlayer, boolean initiallyInside) {
        if (battleId == null || dungeonSessionId == null || roomBounds == null || initialPlayer == null) {
            throw new IllegalArgumentException("Arena identity, bounds, and initial player are required");
        }
        this.battleId = battleId;
        this.dungeonSessionId = dungeonSessionId;
        this.roomId = roomId != null ? roomId : "";
        this.roomBounds = roomBounds;
        participants.add(initialPlayer);
        if (initiallyInside) inside.add(initialPlayer);
    }

    public ActionBattleArenaTransition enter(UUID playerUUID) {
        if (!active || playerUUID == null || inside.contains(playerUUID)) return none(playerUUID);
        boolean wasEmpty = inside.isEmpty();
        boolean joined = participants.add(playerUUID);
        inside.add(playerUUID);
        java.util.EnumSet<ActionBattleArenaTransition.Action> actions = java.util.EnumSet.of(
                ActionBattleArenaTransition.Action.SEND_OUT_PLAYER,
                ActionBattleArenaTransition.Action.SHOW_HUD);
        if (joined) actions.add(ActionBattleArenaTransition.Action.JOIN_PARTICIPANT);
        if (wasEmpty) actions.add(ActionBattleArenaTransition.Action.SEND_OUT_TRAINER);
        return new ActionBattleArenaTransition(playerUUID, actions);
    }

    public ActionBattleArenaTransition exit(UUID playerUUID) {
        if (!active || playerUUID == null || !inside.remove(playerUUID)) return none(playerUUID);
        java.util.EnumSet<ActionBattleArenaTransition.Action> actions = java.util.EnumSet.of(
                ActionBattleArenaTransition.Action.RECALL_PLAYER,
                ActionBattleArenaTransition.Action.HIDE_HUD);
        if (inside.isEmpty()) actions.add(ActionBattleArenaTransition.Action.RECALL_TRAINER);
        return new ActionBattleArenaTransition(playerUUID, actions);
    }

    public boolean contains(double x, double z) { return roomBounds.contains(x, z); }
    public boolean isParticipant(UUID playerUUID) { return playerUUID != null && participants.contains(playerUUID); }
    public boolean isInside(UUID playerUUID) { return playerUUID != null && inside.contains(playerUUID); }
    public int insideCount() { return inside.size(); }
    public boolean isEmpty() { return inside.isEmpty(); }
    public Set<UUID> participants() { return Set.copyOf(participants); }
    public Set<UUID> insideParticipants() { return Set.copyOf(inside); }
    public UUID battleId() { return battleId; }
    public UUID dungeonSessionId() { return dungeonSessionId; }
    public String roomId() { return roomId; }
    public ActionBattleRoomBounds roomBounds() { return roomBounds; }
    public boolean active() { return active; }
    public void deactivate() { active = false; inside.clear(); participants.clear(); }

    private static ActionBattleArenaTransition none(UUID playerUUID) {
        return new ActionBattleArenaTransition(playerUUID, Set.of());
    }
}
