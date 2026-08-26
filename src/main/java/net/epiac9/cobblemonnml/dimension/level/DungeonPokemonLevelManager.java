package net.epiac9.cobblemonnml.dimension.level;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.Config;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonPokemonLevelManager {
    public static final String SCALED_TAG = "cobblemonnml_dungeon_level_scaled";
    public static final String SKIP_SCALING_TAG = "cobblemonnml_skip_dungeon_level_scaling";

    private DungeonPokemonLevelManager() {
    }

    public static int getMinimumLevel(DungeonTier tier) {
        Config.TierConfig config = getTierConfig(tier);
        if (config == null) {
            return defaultMinimum(tier);
        }
        int configuredMin = config.minimumPokemonLevel().get();
        int configuredMax = config.maximumPokemonLevel().get();
        return Math.min(configuredMin, configuredMax);
    }

    public static int getMaximumLevel(DungeonTier tier) {
        Config.TierConfig config = getTierConfig(tier);
        if (config == null) {
            return defaultMaximum(tier);
        }
        int configuredMin = config.minimumPokemonLevel().get();
        int configuredMax = config.maximumPokemonLevel().get();
        return Math.max(configuredMin, configuredMax);
    }

    public static int chooseLevel(DungeonTier tier, RandomSource random) {
        if (tier == null || random == null) {
            return 1;
        }
        int min = getMinimumLevel(tier);
        int max = getMaximumLevel(tier);
        return min + random.nextInt(max - min + 1);
    }

    public static void applyLevel(Pokemon pokemon, DungeonTier tier, RandomSource random) {
        if (pokemon == null || tier == null || random == null) {
            return;
        }
        pokemon.setLevel(chooseLevel(tier, random));
    }

    public static void markExcluded(PokemonEntity entity) {
        if (entity != null) {
            entity.getPersistentData().putBoolean(SKIP_SCALING_TAG, true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof PokemonEntity pokemonEntity)
                || !level.dimension().equals(DungeonDimension.DUNGEON_DIMENSION)
                || !DungeonSession.isActive()) {
            return;
        }

        DungeonTier tier = DungeonSession.getTier();
        if (tier == null
                || !pokemonEntity.getPokemon().isWild()
                || pokemonEntity.getPersistentData().getBoolean(SKIP_SCALING_TAG)
                || pokemonEntity.getPersistentData().getBoolean(SCALED_TAG)
                || isQuestGimmighoul(pokemonEntity.getPokemon())) {
            return;
        }

        applyLevel(pokemonEntity.getPokemon(), tier, level.getRandom());
        pokemonEntity.getPersistentData().putBoolean(SCALED_TAG, true);
        DebugLog.log(
                "[CobblemonNML] Dungeon Pokemon level scaled: "
                        + pokemonEntity.getPokemon().getSpecies().getName()
                        + " Lv."
                        + pokemonEntity.getPokemon().getLevel()
                        + " | "
                        + tier.getDisplayName()
        );
    }

    static int defaultMinimum(DungeonTier tier) {
        if (tier == null) {
            return 1;
        }
        return switch (tier) {
            case TIER_1 -> 40;
            case TIER_2 -> 70;
            case TIER_3 -> 100;
            case TIER_4 -> 150;
        };
    }

    static int defaultMaximum(DungeonTier tier) {
        if (tier == null) {
            return 1;
        }
        return switch (tier) {
            case TIER_1 -> 70;
            case TIER_2 -> 100;
            case TIER_3 -> 150;
            case TIER_4 -> 200;
        };
    }

    private static Config.TierConfig getTierConfig(DungeonTier tier) {
        if (tier == null) {
            return null;
        }
        return switch (tier) {
            case TIER_1 -> Config.TIER_1;
            case TIER_2 -> Config.TIER_2;
            case TIER_3 -> Config.TIER_3;
            case TIER_4 -> Config.TIER_4;
        };
    }

    private static boolean isQuestGimmighoul(Pokemon pokemon) {
        return pokemon != null
                && pokemon.getSpecies() != null
                && "gimmighoul".equalsIgnoreCase(pokemon.getSpecies().getName());
    }
}
