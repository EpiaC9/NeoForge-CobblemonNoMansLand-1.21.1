package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaState;

import java.util.UUID;

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

    public static boolean isValidAreaApplication(ActionBattlePersistentAreaState area, UUID battleId, UUID pokemonUUID,
                                                 double x, double y, double z) {
        return area != null && battleId != null && pokemonUUID != null && battleId.equals(area.battleId())
                && area.contains(x, y, z);
    }

    public static String hudStatusId(ActionBattleIceState.Phase phase) {
        return switch (phase) {
            case CHILL -> "TYPE_ICE_CHILL";
            case FREEZE -> "TYPE_ICE_FREEZE";
            case FROSTBITE -> "TYPE_ICE_FROSTBITE";
        };
    }

    public static long hudRemaining(ActionBattleIceState.Phase phase, int currentHits, long frostbiteRemainingTicks) {
        return phase == ActionBattleIceState.Phase.FROSTBITE
                ? Math.max(0L, frostbiteRemainingTicks)
                : Math.max(0, currentHits);
    }

    public static long hudDuration(ActionBattleIceState.Phase phase, int hitsRequired) {
        return phase == ActionBattleIceState.Phase.FROSTBITE
                ? FROSTBITE_DURATION_TICKS
                : Math.max(1, hitsRequired);
    }

    public static boolean shouldDisplayHudState(String statusId, long remainingTicks) {
        return remainingTicks > 0L || "TYPE_ICE_FREEZE".equals(statusId);
    }

    public static boolean isQualifyingDamagingHit(boolean successful, int beforeHp, int afterHp) {
        return successful && beforeHp > 0 && afterHp < beforeHp;
    }
}
