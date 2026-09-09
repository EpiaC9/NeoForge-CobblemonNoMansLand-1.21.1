package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ActionBattleTypeMechanicIdentity {
    private ActionBattleTypeMechanicIdentity() {}

    public static boolean hasActualType(Pokemon pokemon, String type) {
        return ActionBattleTypeMechanicRules.hasActualType(actualTypes(pokemon), type);
    }

    public static boolean hasNormalAdaptiveType(PokemonEntity entity, String type) {
        return adaptiveTypes(entity).contains(normalize(type));
    }

    public static boolean hasMechanicBenefit(PokemonEntity entity, String type) {
        if (entity == null) return false;
        return ActionBattleTypeMechanicRules.hasMechanicBenefit(
                actualTypes(entity.getPokemon()), adaptiveTypes(entity), type);
    }

    public static boolean hasMechanicBenefit(Pokemon pokemon, String type) {
        if (pokemon == null) return false;
        PokemonEntity entity = pokemon.getEntity();
        return ActionBattleTypeMechanicRules.hasMechanicBenefit(
                actualTypes(pokemon), entity != null ? adaptiveTypes(entity) : Set.of(), type);
    }

    public static boolean hasSameMechanicImmunity(PokemonEntity entity, String type) {
        return hasMechanicBenefit(entity, type);
    }

    public static Set<String> adaptiveTypes(PokemonEntity entity) {
        if (entity == null) return Set.of();
        List<String> effectiveMoveTypes = new ArrayList<>(4);
        for (Move move : entity.getPokemon().getMoveSet()) {
            if (move != null) effectiveMoveTypes.add(ActionBattleEffectiveMoveTypeResolver.resolve(entity, move));
        }
        return ActionBattleNormalAdaptationRules.adaptiveTypes(actualTypes(entity.getPokemon()), effectiveMoveTypes);
    }

    public static void logCommitSnapshot(PokemonEntity entity) {
        if (entity == null) return;
        List<String> effectiveMoveTypes = new ArrayList<>(4);
        for (Move move : entity.getPokemon().getMoveSet()) {
            if (move != null) effectiveMoveTypes.add(ActionBattleEffectiveMoveTypeResolver.resolve(entity, move));
        }
        Set<String> adaptiveTypes = ActionBattleNormalAdaptationRules.adaptiveTypes(
                actualTypes(entity.getPokemon()), effectiveMoveTypes);
        DebugLog.log("[CobblemonNML] Normal adaptation commit snapshot. Pokemon="
                + entity.getPokemon().getUuid() + ", moves=" + effectiveMoveTypes + ", adaptive=" + adaptiveTypes);
    }

    private static List<String> actualTypes(Pokemon pokemon) {
        if (pokemon == null) return List.of();
        List<String> types = new ArrayList<>(2);
        if (pokemon.getPrimaryType() != null) types.add(pokemon.getPrimaryType().getName());
        if (pokemon.getSecondaryType() != null) types.add(pokemon.getSecondaryType().getName());
        return List.copyOf(types);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}
