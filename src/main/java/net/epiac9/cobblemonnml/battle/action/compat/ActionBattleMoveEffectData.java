package net.epiac9.cobblemonnml.battle.action.compat;

public record ActionBattleMoveEffectData(String effect, String trigger, String target, float chance, boolean secondary) {
    public boolean isSupportedFlinchOnHit() {
        return "flinch".equals(effect)
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

    public boolean isSupportedConfusionOnHit() {
        return ("confusion".equals(effect) || "confuse".equals(effect) || "confused".equals(effect))
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

}
