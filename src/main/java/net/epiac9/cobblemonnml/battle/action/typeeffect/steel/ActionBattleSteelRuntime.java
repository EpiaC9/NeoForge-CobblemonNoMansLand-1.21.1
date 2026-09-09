package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleSteelRuntime {
    private static final Map<Key, ActionBattleSteelState> STATES = new HashMap<>();

    private ActionBattleSteelRuntime() {}

    public static boolean isQualifyingSelfBuffMove(Move move) {
        return move != null && move.getType() != null && ActionBattleSteelRules.qualifies(
                move.getType().getName(), FightOrFlightAdapter.moveTargetCategory(move),
                ActionBattleMoveEffectResolver.isSelfBuffingMove(move));
    }

    public static ActivationPlan plan(ActivationInput input) {
        if (input == null || !input.successfullyResolved()
                || !ActionBattleSteelRules.qualifies(input.moveType(), input.targetCategory(), input.selfBuffing())) {
            return ActivationPlan.NOT_QUALIFYING;
        }
        ActionBattleSteelWeight.Override itemOverride = ActionBattleSteelWeight.overrideForItem(input.heldItemId());
        var branch = ActionBattleSteelWeight.select(input.staticWeight(), itemOverride);
        return new ActivationPlan(true, new ActionBattleSteelState.Selection(branch, input.staticWeight()), itemOverride);
    }

    public static ActionBattleSteelState.ApplyResult onSuccessfulSelfBuffCommit(PokemonEntity caster, Move move) {
        ActionBattleSession session = caster != null ? ActionBattleManager.findSessionForBattlePokemonEntity(caster.getUUID()) : null;
        if (session == null || move == null || caster.level().isClientSide) return ActionBattleSteelState.ApplyResult.INVALID;
        String type = ActionBattleEffectiveMoveTypeResolver.resolve(caster, move);
        String held = caster.getPokemon().heldItem().isEmpty() ? ""
                : BuiltInRegistries.ITEM.getKey(caster.getPokemon().heldItem().getItem()).toString();
        ActivationPlan plan = plan(new ActivationInput(type, FightOrFlightAdapter.moveTargetCategory(move),
                ActionBattleMoveEffectResolver.isSelfBuffingMove(move), true,
                caster.getPokemon().getForm().getWeight(), held));
        if (!plan.qualifies()) return ActionBattleSteelState.ApplyResult.INVALID;
        long tick = caster.level().getGameTime();
        Key key = new Key(session.battleId(), caster.getPokemon().getUuid());
        ActionBattleSteelState state = STATES.computeIfAbsent(key, ignored -> new ActionBattleSteelState());
        boolean steelTyped = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(caster, "steel");
        ActionBattleSteelState.ApplyResult result = state.apply(plan.selection(), steelTyped, tick);
        if (result == ActionBattleSteelState.ApplyResult.APPLIED) {
            ActionBattleStatApplicationService.global().applyBatch(session.battleId(), caster.getPokemon().getUuid(),
                    ActionBattleSteelRules.statPlan(plan.selection().branch(), steelTyped), tick,
                    ActionBattleStatSource.STEEL_WEIGHT, true);
            DebugLog.log("[CobblemonNML] Steel self-buff committed. Pokemon=" + caster.getPokemon().getUuid()
                    + ", weight=" + plan.selection().staticWeight() + ", itemOverride=" + plan.itemOverride()
                    + ", branch=" + plan.selection().branch() + ", duration=" + state.view(tick).orElseThrow().totalTicks());
        }
        return result;
    }

    public static Optional<ActionBattleSteelState.View> view(UUID battleId, UUID pokemonId, long tick) {
        ActionBattleSteelState state = battleId != null && pokemonId != null ? STATES.get(new Key(battleId, pokemonId)) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleSteelState.View> view = state.view(tick);
        if (state.isEmpty(tick)) STATES.remove(new Key(battleId, pokemonId));
        return view;
    }

    public static boolean isActive(UUID battleId, UUID pokemonId, ActionBattleSteelRules.Branch branch, long tick) {
        return view(battleId, pokemonId, tick).filter(value -> value.branch() == branch).isPresent();
    }

    public static boolean isActive(PokemonEntity pokemon, ActionBattleSteelRules.Branch branch, long tick) {
        ActionBattleSession session = pokemon != null ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID()) : null;
        return session != null && isActive(session.battleId(), pokemon.getPokemon().getUuid(), branch, tick);
    }

    public static double projectileSpeed(PokemonEntity attacker, Move move, double baseSpeed, long tick) {
        boolean steelMove = "steel".equalsIgnoreCase(ActionBattleEffectiveMoveTypeResolver.resolve(attacker, move));
        return ActionBattleSteelRules.projectileSpeed(baseSpeed,
                isActive(attacker, ActionBattleSteelRules.Branch.MAGNET_RISE, tick), steelMove,
                FightOrFlightAdapter.isRangedMove(move));
    }

    public static boolean qualifiesWeightedMelee(PokemonEntity attacker, Move move, long tick) {
        return move != null && "steel".equalsIgnoreCase(ActionBattleEffectiveMoveTypeResolver.resolve(attacker, move))
                && FightOrFlightAdapter.isMeleeMove(move)
                && isActive(attacker, ActionBattleSteelRules.Branch.WEIGHTED, tick);
    }

    public static double effectiveWeight(UUID battleId, UUID pokemonId, double fallback, long tick) {
        ActionBattleSteelState state = battleId != null && pokemonId != null ? STATES.get(new Key(battleId, pokemonId)) : null;
        return state != null && state.view(tick).isPresent() ? state.effectiveWeight(tick) : fallback;
    }

    public static void tickBattle(UUID battleId, long tick) {
        STATES.forEach((key, state) -> { if (key.battleId().equals(battleId)) state.tick(tick); });
        STATES.entrySet().removeIf(entry -> entry.getValue().isEmpty(tick));
    }

    public static void clearPokemon(UUID battleId, UUID pokemonId) { if (battleId != null && pokemonId != null) STATES.remove(new Key(battleId, pokemonId)); }
    public static void clearBattle(UUID battleId) { if (battleId != null) STATES.keySet().removeIf(key -> key.battleId().equals(battleId)); }
    public static void clearAll() { STATES.clear(); }

    public record ActivationInput(String moveType, String targetCategory, boolean selfBuffing,
                                  boolean successfullyResolved, double staticWeight, String heldItemId) {}
    public record ActivationPlan(boolean qualifies, ActionBattleSteelState.Selection selection,
                                 ActionBattleSteelWeight.Override itemOverride) {
        public static final ActivationPlan NOT_QUALIFYING = new ActivationPlan(false, null, ActionBattleSteelWeight.Override.NONE);
    }
    private record Key(UUID battleId, UUID pokemonId) {}
}
