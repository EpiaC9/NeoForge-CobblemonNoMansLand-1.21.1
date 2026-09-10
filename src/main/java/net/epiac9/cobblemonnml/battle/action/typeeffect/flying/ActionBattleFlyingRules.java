package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import com.cobblemon.mod.common.pokemon.Pokemon;

public final class ActionBattleFlyingRules {
    public static final int MAX_MOMENTUM = 6;
    public static final int BASE_CHANNEL_TICKS = 40;
    public static final int MIN_CHANNEL_TICKS = 20;

    private ActionBattleFlyingRules() {}

    public static double projectileMultiplier(boolean flyingIdentity, int momentum) {
        return flyingIdentity ? 1.0D + clampMomentum(momentum) / (double) MAX_MOMENTUM : 1.0D;
    }

    public static int channelTicks(boolean flyingIdentity, int momentum) {
        if (!flyingIdentity) return BASE_CHANNEL_TICKS;
        double duration = BASE_CHANNEL_TICKS
                - (BASE_CHANNEL_TICKS - MIN_CHANNEL_TICKS) * clampMomentum(momentum) / (double) MAX_MOMENTUM;
        return (int) Math.round(duration);
    }

    public static double propulsionBlocksPerSecond(boolean flyingIdentity, boolean meleeMove, int momentum) {
        return flyingIdentity && meleeMove ? clampMomentum(momentum) : 0.0D;
    }

    public static boolean usesPropulsion(boolean flyingIdentity, boolean meleeMove, int momentum) {
        return flyingIdentity && meleeMove && clampMomentum(momentum) > 0;
    }

    public static int criticalStageBonus(boolean flyingIdentity, int momentum) {
        return flyingIdentity && clampMomentum(momentum) == MAX_MOMENTUM ? 1 : 0;
    }

    public static int clampMomentum(int momentum) {
        return Math.max(0, Math.min(MAX_MOMENTUM, momentum));
    }

    public static boolean isFlyingPokemon(Pokemon pokemon) {
        return pokemon != null
                && (pokemon.getPrimaryType() != null
                && "flying".equalsIgnoreCase(pokemon.getPrimaryType().getName())
                || pokemon.getSecondaryType() != null
                && "flying".equalsIgnoreCase(pokemon.getSecondaryType().getName()));
    }
}
