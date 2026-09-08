package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleDragonRules {
    public static final long MAINTENANCE_DURATION_TICKS = 120L;
    public static final long ACTIVATION_DURATION_TICKS = 240L;
    public static final long ABILITY_SUSTAIN_TICKS = 40L;
    public static final long DAMAGE_SUSTAIN_TICKS = 60L;
    public static final long ROAR_DURATION_TICKS = 30L;
    public static final long ACTIVE_DURATION_TICKS = 240L;
    public static final long NORMAL_STAT_DECAY_TICKS = 120L;
    public static final long DRAGON_STAT_DECAY_TICKS = 60L;
    public static final int NORMAL_STAT_LEVEL = 2;
    public static final int DRAGON_STAT_LEVEL = 4;
    public static final long NORMAL_PERSONAL_COOLDOWN_TICKS = 60L;
    public static final long DRAGON_PERSONAL_COOLDOWN_TICKS = 30L;
    public static final double ROAR_RADIUS = 20.0D;

    private ActionBattleDragonRules() {}

    static long add(long currentTick, long durationTicks) {
        return ActionBattleTiming.safeAdd(currentTick, durationTicks);
    }

    public static long personalCooldownTicks(boolean dragonHolder) {
        return dragonHolder ? DRAGON_PERSONAL_COOLDOWN_TICKS : NORMAL_PERSONAL_COOLDOWN_TICKS;
    }
}
