package net.epiac9.cobblemonnml.battle.action.compat;

public record ActionBattleMoveEffectData(String effect, String trigger, String target, float chance) {
    public boolean isSupportedBurnOnHit() {
        return "burn".equals(effect) && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }
}
