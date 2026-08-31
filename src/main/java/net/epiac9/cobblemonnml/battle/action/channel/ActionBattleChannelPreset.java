package net.epiac9.cobblemonnml.battle.action.channel;

public record ActionBattleChannelPreset(int durationTicks, boolean immobilizeCaster, boolean cancelOnDamage, boolean cancelOnCommand, boolean trackTargetPosition) {
    public ActionBattleChannelPreset {
        if (durationTicks <= 0) throw new IllegalArgumentException("durationTicks must be > 0");
    }
}
