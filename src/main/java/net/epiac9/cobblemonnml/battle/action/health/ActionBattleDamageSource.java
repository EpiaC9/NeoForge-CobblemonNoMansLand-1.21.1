package net.epiac9.cobblemonnml.battle.action.health;

public record ActionBattleDamageSource(String id, boolean directMove, boolean dot,
                                       boolean extraDotTick, boolean curseEffect,
                                       boolean nonReactive, boolean bypassEndurance) {
    public ActionBattleDamageSource {
        id = id == null ? "unknown" : id;
    }

    public static ActionBattleDamageSource directMove(String id) {
        return new ActionBattleDamageSource(id, true, false, false, false, false, false);
    }

    public static ActionBattleDamageSource dot(String id) {
        return new ActionBattleDamageSource(id, false, true, false, false, false, false);
    }

    public static ActionBattleDamageSource extraDot(String id) {
        return new ActionBattleDamageSource(id, false, true, true, false, true, false);
    }

    public static ActionBattleDamageSource curse(String id) {
        return new ActionBattleDamageSource(id, false, false, false, true, false, false);
    }

    public static ActionBattleDamageSource nonReactiveCurse(String id) {
        return new ActionBattleDamageSource(id, false, false, false, true, true, false);
    }

    public static ActionBattleDamageSource curseKo() {
        return new ActionBattleDamageSource("ghost_curse_ko", false, false,
                false, true, true, true);
    }

    public static ActionBattleDamageSource bugSecondary() {
        return new ActionBattleDamageSource("bug_adaptation_secondary", false, false,
                false, false, true, false);
    }

    public static ActionBattleDamageSource bugSheddingDot() {
        return new ActionBattleDamageSource("bug_adaptation_shedding", false, true,
                true, false, true, false);
    }
}
