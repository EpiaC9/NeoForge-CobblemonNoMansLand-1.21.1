package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public final class ActionBattleCombatGuard {
    private static boolean registered;

    private ActionBattleCombatGuard() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(ActionBattleCombatGuard::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ActionBattleCombatGuard::onKnockback);
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof PokemonEntity pokemon
                && ActionBattleSwapTransitionGuard.rejectsHit(pokemon.getPokemon().getUuid())) {
            event.setCanceled(true);
        }
    }

    private static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemon)) return;
        long tick = pokemon.level().getGameTime();
        if (ActionBattleSwapTransitionGuard.rejectsHit(pokemon.getPokemon().getUuid())
                || ActionBattleSteelRuntime.isActive(pokemon, ActionBattleSteelRules.Branch.WEIGHTED, tick)) {
            event.setCanceled(true);
        }
    }
}
