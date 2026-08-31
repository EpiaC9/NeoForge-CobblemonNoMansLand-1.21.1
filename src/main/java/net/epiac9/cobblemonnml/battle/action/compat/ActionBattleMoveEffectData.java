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

    public boolean isSupportedPoisonOnHit() {
        return ("poison".equals(effect) || "toxic".equals(effect) || "badly_poison".equals(effect))
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }


    public boolean isSupportedFlinchOnHit() {
        return "flinch".equals(effect)
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

    public boolean isSupportedParalysisOnHit() {
        return ("paralysis".equals(effect) || "paralyze".equals(effect) || "paralyzed".equals(effect) || "par".equals(effect) || "triattack".equals(effect))
                && "on_hit".equals(trigger) && "target".equals(target) && chance > 0.0F;
    }

    public int poisonProgressionStrength() {
        if (!isSupportedPoisonOnHit()) return 0;
        return "toxic".equals(effect) || "badly_poison".equals(effect) ? 2 : 1;
    }

    public boolean isTriAttack() { return "triattack".equals(effect); }
}
