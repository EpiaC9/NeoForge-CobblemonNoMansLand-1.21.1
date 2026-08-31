package net.epiac9.cobblemonnml.battle.action.area;

import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import java.util.UUID;

public final class ActionBattlePersistentAreaState {
    private final UUID areaId;
    private final UUID battleId;
    private final UUID ownerPokemonUUID;
    private final String effectId;
    private final ActionBattlePosition anchor;
    private final ActionBattlePersistentAreaPreset preset;
    private int ageTicks;
    private int nextPulseTick;

    public ActionBattlePersistentAreaState(UUID areaId, UUID battleId, UUID ownerPokemonUUID, String effectId, ActionBattlePosition anchor, ActionBattlePersistentAreaPreset preset) {
        if (areaId == null || battleId == null || ownerPokemonUUID == null || effectId == null || effectId.isBlank() || anchor == null || preset == null) throw new IllegalArgumentException("Persistent area values cannot be null/blank.");
        this.areaId = areaId;
        this.battleId = battleId;
        this.ownerPokemonUUID = ownerPokemonUUID;
        this.effectId = effectId;
        this.anchor = anchor;
        this.preset = preset;
        this.nextPulseTick = preset.pulseImmediately() ? preset.pulseIntervalTicks() : preset.pulseIntervalTicks();
    }

    void advance() { ageTicks++; }
    boolean shouldExpire() { return ageTicks >= preset.durationTicks(); }
    boolean shouldPulse() { return ageTicks == nextPulseTick; }
    void advancePulse() { nextPulseTick += preset.pulseIntervalTicks(); }

    public boolean contains(double x, double y, double z) {
        double dx = x - anchor.x();
        double dz = z - anchor.z();
        boolean horizontal = dx * dx + dz * dz <= preset.horizontalRadius() * preset.horizontalRadius();
        boolean vertical = y >= anchor.y() && y <= anchor.y() + preset.verticalHeight();
        return horizontal && vertical;
    }

    public UUID areaId() { return areaId; }
    public UUID battleId() { return battleId; }
    public UUID ownerPokemonUUID() { return ownerPokemonUUID; }
    public String effectId() { return effectId; }
    public ActionBattlePosition anchor() { return anchor; }
    public ActionBattlePersistentAreaPreset preset() { return preset; }
    public int ageTicks() { return ageTicks; }
    public int remainingTicks() { return Math.max(0, preset.durationTicks() - ageTicks); }
}
