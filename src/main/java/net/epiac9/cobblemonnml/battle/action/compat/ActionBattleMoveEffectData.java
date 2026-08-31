package net.epiac9.cobblemonnml.battle.action.compat;

public record ActionBattleMoveEffectData(String effect, String trigger, String target, float chance, boolean secondary) {
    public boolean isSupportedBurnOnHit() {
        return ("burn".equals(effect) || "triattack".equals(effect))
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

    public boolean isSupportedFreezeOnHit() {
        return ("freeze".equals(effect) || "triattack".equals(effect))
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

    public boolean isTriAttack() { return "triattack".equals(effect); }
}
