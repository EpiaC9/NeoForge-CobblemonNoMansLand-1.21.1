package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleFireRuntime {
    private static final Map<Key, ActionBattleFirePressureState> STATES = new HashMap<>();
    private static int infernoAmmoCost;
    private static float infernoExplosionDamage;

    private ActionBattleFireRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.pokemon() == null || context.battleId() == null) return;
        PokemonEntity pokemon = context.pokemon();
        Key key = new Key(context.battleId(), pokemon.getPokemon().getUuid());
        ActionBattleFirePressureState state = STATES.computeIfAbsent(key,
                ignored -> new ActionBattleFirePressureState());
        long tick = pokemon.level().getGameTime();
        if (state.addPressure(1, tick).enteredInferno()) applyInfernoStats(context.battleId(), pokemon, tick);
        if (!state.view(tick).inferno() || infernoAmmoCost <= 0 || infernoExplosionDamage <= 0.0F) return;
        Vec3 center = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY
                || context.target() == null ? pokemon.position() : context.target().position();
        explode(pokemon, center, infernoExplosionDamage);
        if (state.consumeInfernoAmmo(infernoAmmoCost) && !state.view(tick).inferno()) {
            clearInfernoStats(context.battleId(), pokemon, tick);
        }
    }

    public static void onDamageTaken(PokemonEntity pokemon, int actualDamage) {
        ActionBattleSession session = pokemon != null
                ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID()) : null;
        boolean fireIdentity = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemon, "fire");
        if (session == null || !ActionBattleFirePressureState.acceptsDamageGain(fireIdentity, actualDamage)) return;
        Key key = new Key(session.battleId(), pokemon.getPokemon().getUuid());
        long tick = pokemon.level().getGameTime();
        if (STATES.computeIfAbsent(key, ignored -> new ActionBattleFirePressureState())
                .addPressure(actualDamage, tick).enteredInferno()) applyInfernoStats(session.battleId(), pokemon, tick);
    }

    public static void tickBattle(UUID battleId, long tick) {
        STATES.forEach((key, state) -> { if (key.battleId().equals(battleId)) state.tick(tick); });
        STATES.entrySet().removeIf(entry -> entry.getValue().empty());
    }

    public static void tickAll(long tick) {
        STATES.values().forEach(state -> state.tick(tick));
        STATES.entrySet().removeIf(entry -> entry.getValue().empty());
    }

    public static ActionBattleFirePressureState.View view(UUID battleId, UUID pokemonId, long tick) {
        ActionBattleFirePressureState state = battleId != null && pokemonId != null
                ? STATES.get(new Key(battleId, pokemonId)) : null;
        return state != null ? state.view(tick) : new ActionBattleFirePressureState.View(0, false);
    }

    public static void configureInferno(int ammoCost, float explosionDamage) {
        infernoAmmoCost = Math.max(0, ammoCost);
        infernoExplosionDamage = Math.max(0.0F, explosionDamage);
    }
    public static void clearBattle(UUID battleId) { STATES.keySet().removeIf(key -> key.battleId().equals(battleId)); }
    public static void clearAll() { STATES.clear(); }

    private static void applyInfernoStats(UUID battleId, PokemonEntity entity, long tick) {
        Pokemon pokemon = entity.getPokemon();
        ActionBattleStat offense = score(pokemon, Stats.ATTACK) >= score(pokemon, Stats.SPECIAL_ATTACK)
                ? ActionBattleStat.ATTACK : ActionBattleStat.SPECIAL_ATTACK;
        ActionBattleStat defense = score(pokemon, Stats.DEFENCE) >= score(pokemon, Stats.SPECIAL_DEFENCE)
                ? ActionBattleStat.DEFENSE : ActionBattleStat.SPECIAL_DEFENSE;
        ActionBattleStatApplicationService.global().applyBatch(battleId, pokemon.getUuid(),
                Map.of(offense, 2, defense, 2), tick, ActionBattleStatSource.FIRE_INFERNO, true);
    }

    private static void clearInfernoStats(UUID battleId, PokemonEntity entity, long tick) {
        ActionBattleEffectController.global().clearStatContributionsFromSource(
                battleId, entity.getPokemon().getUuid(), ActionBattleStatSource.FIRE_INFERNO, tick);
    }

    private static int score(Pokemon pokemon, Stats stat) {
        return pokemon.getIvs().getOrDefault(stat) + pokemon.getEvs().getOrDefault(stat);
    }

    private static void explode(PokemonEntity attacker, Vec3 center, float damage) {
        if (!(attacker.level() instanceof ServerLevel level)) return;
        for (PokemonEntity target : level.getEntitiesOfClass(PokemonEntity.class,
                new AABB(center, center).inflate(1.5D), candidate -> candidate != attacker && candidate.isAlive())) {
            UUID battle = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
            UUID attackerBattle = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
            if (attackerBattle != null && attackerBattle.equals(battle)) {
                target.hurt(level.damageSources().indirectMagic(attacker, attacker), damage);
            }
        }
    }

    private record Key(UUID battleId, UUID pokemonId) {}
}
