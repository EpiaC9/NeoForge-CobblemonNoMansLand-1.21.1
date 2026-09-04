package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

public final class ActionBattlePoisonVisuals {
    private ActionBattlePoisonVisuals() {}

    public static String hudStatusId(ActionBattlePoisonRules.PoisonLevel level) {
        return switch (level) {
            case POISON -> "TYPE_POISON";
            case POISON_LV1 -> "TYPE_POISON_LV1";
            case POISON_LV2 -> "TYPE_POISON_LV2";
            case TOXIC -> "TYPE_TOXIC";
            case NONE -> "";
        };
    }

    public static long hudRemaining(ActionBattlePoisonRules.PoisonLevel level, int accumulation,
                                    long toxicRemainingTicks) {
        return level == ActionBattlePoisonRules.PoisonLevel.TOXIC
                ? Math.max(0L, toxicRemainingTicks)
                : Math.clamp(accumulation, 0, ActionBattlePoisonRules.MAX_ACCUMULATION);
    }

    public static long hudDuration(ActionBattlePoisonRules.PoisonLevel level) {
        return level == ActionBattlePoisonRules.PoisonLevel.TOXIC
                ? ActionBattlePoisonRules.TOXIC_DURATION_TICKS : ActionBattlePoisonRules.MAX_ACCUMULATION;
    }

    public static int greenParticleCount(ActionBattlePoisonRules.PoisonLevel level) {
        return switch (level) {
            case POISON, POISON_LV1, POISON_LV2 -> 1;
            case NONE, TOXIC -> 0;
        };
    }

    public static int purpleParticleCount(ActionBattlePoisonRules.PoisonLevel level) {
        return switch (level) {
            case POISON_LV1 -> 1;
            case POISON_LV2 -> 2;
            case TOXIC -> 3;
            case NONE, POISON -> 0;
        };
    }
}
