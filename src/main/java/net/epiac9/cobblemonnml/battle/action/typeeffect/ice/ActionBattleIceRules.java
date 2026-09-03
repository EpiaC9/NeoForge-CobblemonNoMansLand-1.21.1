package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleIceRules {
    public static final int BASE_HITS_REQUIRED = 3;
    public static final long STACK_WINDOW_TICKS = ActionBattleTiming.seconds(3L);
    public static final long LIFECYCLE_TICKS = ActionBattleTiming.seconds(18L);
    public static final long FROSTBITE_DURATION_TICKS = ActionBattleTiming.seconds(9L);
    public static final long REAPPLICATION_RESET_TICKS = ActionBattleTiming.seconds(18L);
    public static final int FREEZE_NORMAL_DEFENSE_STAGES = -1;
    public static final int FREEZE_ICE_DEFENSE_STAGES = 1;
    public static final int FROSTBITE_NORMAL_DEFENSE_STAGES = -2;
    public static final int FROSTBITE_ICE_DEFENSE_STAGES = 2;
    public static final double FROSTBITE_ICE_DAMAGE_MULTIPLIER = 1.20D;

    private ActionBattleIceRules() {}

    public static int defenseStages(ActionBattleIceState.Phase phase, boolean iceTyped) {
        if (phase == ActionBattleIceState.Phase.FREEZE) {
            return iceTyped ? FREEZE_ICE_DEFENSE_STAGES : FREEZE_NORMAL_DEFENSE_STAGES;
        }
        if (phase == ActionBattleIceState.Phase.FROSTBITE) {
            return iceTyped ? FROSTBITE_ICE_DEFENSE_STAGES : FROSTBITE_NORMAL_DEFENSE_STAGES;
        }
        return 0;
    }

    public static double modifyIncomingDamage(double damage, boolean iceMove, boolean frostbittenTarget) {
        if (!(damage > 0.0D) || !iceMove || !frostbittenTarget) return damage;
        return damage * FROSTBITE_ICE_DAMAGE_MULTIPLIER;
    }
}
