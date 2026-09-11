package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;

import java.lang.reflect.Method;

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

        String moveId = ActionBattleMoveMetadataRules.canonicalMoveId(move.getName());
        ActionBattleCanonicalMoveMetadata canonical = ActionBattleCanonicalMoveRegistry.lookup(moveId);

        return new ActionBattleMoveDescriptor(
                moveId,
                exposedType,
                normalizeType(effectiveType),
                ActionBattleMoveMetadataRules.damageCategory(damageCategory, power),
                ActionBattleMoveMetadataRules.nonNegative(power),
                rawDouble(move, "getAccuracy"),
                FightOrFlightAdapter.movePriority(move),
                ActionBattleMoveMetadataRules.nonNegative(FightOrFlightAdapter.currentPp(move)),
                ActionBattleMoveMetadataRules.nonNegative(FightOrFlightAdapter.maxPp(move)),
                targetCategory,
                ActionBattleMoveMetadataRules.targetingMode(targetCategory),
                ActionProjectileProfile.deliveryType(move.getName()),
                canonical.flags(),
                rawTemplateDouble(move, "getCritRatio", 0.0D),
                canonical
        );
    }

    public static ActionBattleMoveDescriptor empty() {
        return new ActionBattleMoveDescriptor("", "normal", "normal",
                ActionBattleMoveDescriptor.DamageCategory.STATUS, 0, Double.NaN, 0, 0, 0, "",
                ActionBattleMoveDescriptor.TargetingMode.TARGET,
                net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType.NORMAL_PROJECTILE,
                java.util.Set.of(), 0.0D, ActionBattleCanonicalMoveMetadata.empty());
    }

    private static String rawDamageCategory(Move move) {
        Object category = invoke(move, "getDamageCategory");
        if (category == null) category = invoke(move, "getCategory");
        Object template = invoke(move, "getTemplate");
        if (category == null) category = invoke(template, "getDamageCategory");
        if (category == null) category = invoke(template, "getCategory");
        return category != null ? category.toString() : "";
    }

    private static double rawDouble(Object target, String getter) {
        Object value = invoke(target, getter);
        if (value instanceof Number number) return number.doubleValue();
        Object template = invoke(target, "getTemplate");
        value = invoke(template, getter);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static double rawTemplateDouble(Object target, String getter, double fallback) {
        Object template = invoke(target, "getTemplate");
        Object value = invoke(template, getter);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? "normal" : normalized;
    }
}
