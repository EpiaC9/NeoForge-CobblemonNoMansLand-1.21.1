package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;

import java.util.Collection;

public final class ActionBattleMoveMetadataResolver {
    private ActionBattleMoveMetadataResolver() {}

    public static ActionBattleMoveDescriptor resolve(PokemonEntity user, Move move) {
        if (move == null) return empty();
        int power = FightOrFlightAdapter.movePower(move);
        String targetCategory = FightOrFlightAdapter.moveTargetCategory(move);
        String exposedType = move.getType() != null ? normalizeType(move.getType().getName()) : "normal";
        String effectiveType = ActionBattleEffectiveMoveTypeResolver.resolve(user, move);
        String damageCategory = rawDamageCategory(move);
        if (damageCategory.isBlank() && power > 0) {
            damageCategory = FightOrFlightAdapter.isSpecialDamageCategory(move) ? "special" : "physical";
        }
        return new ActionBattleMoveDescriptor(
                ActionBattleMoveMetadataRules.canonicalMoveId(move.getName()),
                exposedType,
                normalizeType(effectiveType),
                ActionBattleMoveMetadataRules.damageCategory(damageCategory, power),
                ActionBattleMoveMetadataRules.nonNegative(power),
                rawDouble(move, "accuracy"),
                FightOrFlightAdapter.movePriority(move),
                ActionBattleMoveMetadataRules.nonNegative(FightOrFlightAdapter.currentPp(move)),
                ActionBattleMoveMetadataRules.nonNegative(FightOrFlightAdapter.maxPp(move)),
                targetCategory,
                ActionBattleMoveMetadataRules.targetingMode(targetCategory),
                ActionProjectileProfile.deliveryType(move.getName()),
                ActionBattleMoveMetadataRules.normalizeFlags(rawFlags(move)),
                rawTemplateDouble(move, "critRatio", 0.0D)
        );
    }

    public static ActionBattleMoveDescriptor empty() {
        return new ActionBattleMoveDescriptor("", "normal", "normal",
                ActionBattleMoveDescriptor.DamageCategory.STATUS, 0, Double.NaN, 0, 0, 0, "",
                ActionBattleMoveDescriptor.TargetingMode.TARGET,
                net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType.NORMAL_PROJECTILE,
                java.util.Set.of(), 0.0D);
    }

    private static String rawDamageCategory(Move move) {
        Object category = ActionBattleMoveReflection.property(move, "damageCategory", "category");
        Object template = ActionBattleMoveReflection.property(move, "template");
        if (category == null) category = ActionBattleMoveReflection.property(template, "damageCategory", "category");
        Object name = ActionBattleMoveReflection.property(category, "name");
        return name != null ? name.toString() : (category != null ? category.toString() : "");
    }

    private static Collection<?> rawFlags(Move move) {
        Object template = ActionBattleMoveReflection.property(move, "template");
        Collection<?> flags = ActionBattleMoveReflection.collectionProperty(template,
                "flags", "moveFlags", "properties", "moveProperties");
        if (!flags.isEmpty()) return flags;
        return ActionBattleMoveReflection.collectionProperty(move,
                "flags", "moveFlags", "properties", "moveProperties");
    }

    private static double rawDouble(Object target, String property) {
        Object value = ActionBattleMoveReflection.property(target, property);
        if (value instanceof Number number) return number.doubleValue();
        Object template = ActionBattleMoveReflection.property(target, "template");
        value = ActionBattleMoveReflection.property(template, property);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static double rawTemplateDouble(Object target, String property, double fallback) {
        Object template = ActionBattleMoveReflection.property(target, "template");
        Object value = ActionBattleMoveReflection.property(template, property);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "normal" : normalized;
    }
}
