package net.epiac9.cobblemonnml.battle.action.health;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;

public final class ActionBattleStatusDotRules {
    public static final long TICK_INTERVAL_TICKS = 40L;

    private ActionBattleStatusDotRules() {}

    public static ActionBattleDotSpec spec(ActionBattleStatus status, long durationTicks) {
        if (status == null || durationTicks <= 0L) return null;
        return switch (status) {
            case BURN -> new ActionBattleDotSpec("burn", TICK_INTERVAL_TICKS, durationTicks,
                    context -> ceilPerThousand(context.maxHealth(), 20));
            case FREEZE -> new ActionBattleDotSpec("freeze", TICK_INTERVAL_TICKS, durationTicks,
                    context -> ceilPerThousand(context.maxHealth(), 15));
            case POISON -> new ActionBattleDotSpec("poison", TICK_INTERVAL_TICKS, durationTicks,
                    context -> ceilPerThousand(context.maxHealth(), 10));
            case TOXIC -> new ActionBattleDotSpec("toxic", TICK_INTERVAL_TICKS, durationTicks,
                    context -> ceilPerThousand(context.maxHealth(),
                            10 * (int) Math.min(5L, context.tickIndex())));
            default -> null;
        };
    }

    public static boolean supported(ActionBattleStatus status) {
        return status == ActionBattleStatus.BURN || status == ActionBattleStatus.FREEZE
                || status == ActionBattleStatus.POISON || status == ActionBattleStatus.TOXIC;
    }

    private static int ceilPerThousand(int maxHealth, int perThousand) {
        if (maxHealth <= 0 || perThousand <= 0) return 0;
        return Math.max(1, (int) Math.ceil(maxHealth * (perThousand / 1000.0D)));
    }
}
