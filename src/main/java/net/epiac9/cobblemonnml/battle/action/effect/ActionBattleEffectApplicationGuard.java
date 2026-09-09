package net.epiac9.cobblemonnml.battle.action.effect;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;

public final class ActionBattleEffectApplicationGuard {
    private ActionBattleEffectApplicationGuard() {}

    public static boolean allowsNewApplication(ActionBattleSession session, PokemonEntity target, long tick) {
        return session != null && target != null
                && !ActionBattleBugRuntime.protectsFromNewEffects(session, target, tick);
    }
}
