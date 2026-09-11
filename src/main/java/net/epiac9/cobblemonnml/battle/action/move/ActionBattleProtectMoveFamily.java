package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectStance;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectVariant;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;

import java.util.Map;

public final class ActionBattleProtectMoveFamily {
    public static final long STANCE_TICKS = 40L;
    public static final long GLOBAL_COOLDOWN_TICKS = 60L;

    private static final Map<String, ActionBattleProtectVariant> VARIANTS = Map.ofEntries(
            Map.entry("protect", ActionBattleProtectVariant.PROTECT),
            Map.entry("detect", ActionBattleProtectVariant.DETECT),
            Map.entry("endure", ActionBattleProtectVariant.ENDURE),
            Map.entry("banefulbunker", ActionBattleProtectVariant.BANEFUL_BUNKER),
            Map.entry("burningbulwark", ActionBattleProtectVariant.BURNING_BULWARK),
            Map.entry("kingsshield", ActionBattleProtectVariant.KINGS_SHIELD),
            Map.entry("obstruct", ActionBattleProtectVariant.OBSTRUCT),
            Map.entry("silktrap", ActionBattleProtectVariant.SILK_TRAP),
            Map.entry("spikyshield", ActionBattleProtectVariant.SPIKY_SHIELD),
            Map.entry("maxguard", ActionBattleProtectVariant.MAX_GUARD)
    );

    private ActionBattleProtectMoveFamily() {}

    public static boolean isProtectFamily(Move move) {
        return move != null && VARIANTS.containsKey(move.getName());
    }

    public static boolean isBalefulBunker(Move move) {
        return variant(move) == ActionBattleProtectVariant.BANEFUL_BUNKER;
    }

    public static ActionBattleProtectVariant variant(Move move) {
        return move == null ? null : VARIANTS.get(move.getName());
    }

    public static StartResult tryStart(ActionBattleSession session, PokemonEntity caster, Move move) {
        ActionBattleProtectVariant variant = variant(move);
        if (session == null || caster == null || move == null || variant == null || caster.isRemoved()) return StartResult.INVALID;
        long currentTick = caster.level().getGameTime();
        if (session.isPokemonSharedAbilityOnCooldown(caster.getPokemon().getUuid(), currentTick)) return StartResult.COOLDOWN;
        if (!FightOrFlightAdapter.consumeOnePp(caster, move)) return StartResult.NO_PP;
        caster.getNavigation().stop();
        ActionBattleProtectStance stance = ActionBattleProtectController.global().startProtect(
                session.battleId(), caster.getPokemon().getUuid(), currentTick, variant
        );
        if (stance == null) {
            FightOrFlightAdapter.refundOnePp(move);
            return StartResult.INVALID;
        }
        ActionBattleGhostRuntime.global().applyAbilityCooldown(session, caster,
                ActionBattleGhostRuntime.global().findMoveSlot(caster, move), currentTick);
        return StartResult.STARTED;
    }

    public enum StartResult { STARTED, INVALID, NO_PP, COOLDOWN }
}
