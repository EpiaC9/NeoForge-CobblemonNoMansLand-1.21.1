package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleGrassFlowerRuntime {
    private static final ActionBattleStat[] STATS = {
            ActionBattleStat.ATTACK, ActionBattleStat.DEFENSE, ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.SPECIAL_DEFENSE, ActionBattleStat.SPEED, ActionBattleStat.ACCURACY
    };
    private static final Map<UUID, Map<UUID, Hot>> HOTS = new HashMap<>();
    private static Tuning tuning = new Tuning(0, 20L, 1L);

    private ActionBattleGrassFlowerRuntime() {}

    public static ActionBattleStat consume(UUID battleId, PokemonEntity pokemon, long tick) {
        if (battleId == null || pokemon == null || tick < 0L) return null;
        UUID pokemonId = pokemon.getPokemon().getUuid();
        ActionBattleStat selected = selectStat(pokemonId, tick);
        EnumMap<ActionBattleStat, Integer> change = new EnumMap<>(ActionBattleStat.class);
        change.put(selected, 1);
        ActionBattleStatApplicationService.global().applyBatch(
                battleId, pokemonId, change, tick, ActionBattleStatSource.GRASS_FLOWER, true);
        if (tuning.healAmount() > 0) HOTS.computeIfAbsent(battleId, ignored -> new HashMap<>())
                .put(pokemonId, new Hot(tick + tuning.intervalTicks(), tick + tuning.durationTicks()));
        return selected;
    }

    public static void tick(ServerLevel level, UUID battleId, long tick) {
        if (level == null || battleId == null || tick < 0L) return;
        Map<UUID, Hot> battle = HOTS.get(battleId);
        if (battle == null) return;
        for (Map.Entry<UUID, Hot> entry : new ArrayList<>(battle.entrySet())) {
            Hot hot = entry.getValue();
            if (tick >= hot.endTick()) {
                battle.remove(entry.getKey());
                continue;
            }
            if (tick < hot.nextHealTick()) continue;
            PokemonEntity pokemon = find(level, entry.getKey());
            if (pokemon != null) ActionBattleGrassController.healPokemon(pokemon.getPokemon(), tuning.healAmount());
            battle.put(entry.getKey(), new Hot(tick + tuning.intervalTicks(), hot.endTick()));
        }
        if (battle.isEmpty()) HOTS.remove(battleId);
    }

    public static ActionBattleStat selectStat(UUID pokemonId, long tick) {
        long mixed = (pokemonId != null ? pokemonId.getMostSignificantBits() ^ pokemonId.getLeastSignificantBits() : 0L) ^ tick;
        return STATS[Math.floorMod(Long.hashCode(mixed), STATS.length)];
    }

    public static void configure(Tuning value) { if (value != null) tuning = value; }
    public static void clearBattle(UUID battleId) { if (battleId != null) HOTS.remove(battleId); }
    public static void clearAll() { HOTS.clear(); }

    private static PokemonEntity find(ServerLevel level, UUID pokemonId) {
        for (var entity : level.getAllEntities()) {
            if (entity instanceof PokemonEntity pokemon && pokemonId.equals(pokemon.getPokemon().getUuid())) return pokemon;
        }
        return null;
    }

    public record Tuning(int healAmount, long intervalTicks, long durationTicks) {
        public Tuning {
            healAmount = Math.max(0, healAmount);
            intervalTicks = Math.max(1L, intervalTicks);
            durationTicks = Math.max(1L, durationTicks);
        }
    }

    private record Hot(long nextHealTick, long endTick) {}
}
