package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public enum ActionBattleBugTrainingPreset {
    DEF_ONLY,
    SPDEF_ONLY,
    DEF_SPDEF;

    public int ev(ActionBattleBugTrainingStat stat) {
        if (stat == null) return 0;
        return switch (this) {
            case DEF_ONLY -> stat == ActionBattleBugTrainingStat.DEFENSE ? 252 : 0;
            case SPDEF_ONLY -> stat == ActionBattleBugTrainingStat.SPECIAL_DEFENSE ? 252 : 0;
            case DEF_SPDEF -> stat == ActionBattleBugTrainingStat.DEFENSE
                    || stat == ActionBattleBugTrainingStat.SPECIAL_DEFENSE ? 252 : 0;
        };
    }
}
