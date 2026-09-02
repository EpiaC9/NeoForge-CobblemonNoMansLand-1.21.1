package net.epiac9.cobblemonnml.battle.action.effect;

import com.cobblemon.mod.common.api.moves.Move;

public final class ActionBattleConfusionRules {
    public static final long DURATION_TICKS = 180L;
    public static final long COOLDOWN_PENALTY_TICKS = 60L;
    public static final float CORRUPTION_CHANCE = 0.33F;
    public static final float CHANNEL_SELF_CANCEL_CHANCE = 0.30F;
    public static final int CHANNEL_BONUS_MIN_SECONDS = 2;
    public static final int CHANNEL_BONUS_MAX_SECONDS = 8;

    private ActionBattleConfusionRules() {}

    public static boolean shouldCorrupt(CommandKind kind, float roll) {
        return kind != null && kind != CommandKind.SWAP_OUT && roll >= 0.0F && roll < CORRUPTION_CHANCE;
    }

    public static long channelBonusTicks(int randomInclusiveIndex) {
        int bounded = Math.max(0, Math.min(CHANNEL_BONUS_MAX_SECONDS - CHANNEL_BONUS_MIN_SECONDS, randomInclusiveIndex));
        return (CHANNEL_BONUS_MIN_SECONDS + bounded) * 20L;
    }

    public static boolean shouldSelfCancelChannel(float roll) {
        return roll >= 0.0F && roll < CHANNEL_SELF_CANCEL_CHANCE;
    }

    public static CommandKind commandKindFor(Move move) {
        if (move == null) return CommandKind.SUPPORT;
        if (net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler.isBalefulBunker(move)) return CommandKind.PROTECT;
        if (net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler.isHail(move)
                || net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler.isToxicSpikes(move)) return CommandKind.CHANNEL;
        if (net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter.isMeleeMove(move)) return CommandKind.MELEE;
        if (net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter.isRangedMove(move)) return CommandKind.RANGED;
        return CommandKind.SUPPORT;
    }

    public enum CommandKind { MOVE_HERE, RANGED, MELEE, CHANNEL, SUPPORT, PROTECT, SWAP_OUT }
}
