package net.epiac9.cobblemonnml.battle.action.move;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ActionBattleMoveReflection {
    private ActionBattleMoveReflection() {}

    public static Object property(Object source, String... candidates) {
        if (source == null || candidates == null || candidates.length == 0) return null;
        Map<String, String> wanted = new LinkedHashMap<>();
        for (String candidate : candidates) {
            String normalized = normalizeMember(candidate);
            if (!normalized.isEmpty()) wanted.put(normalized, candidate);
        }
        if (wanted.isEmpty()) return null;

        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
            if (!wanted.containsKey(normalizeMember(method.getName()))) continue;
            try {
                return method.invoke(source);
            } catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        for (Class<?> type = source.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (!wanted.containsKey(normalizeMember(field.getName()))) continue;
                try {
                    field.setAccessible(true);
                    return field.get(source);
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        return null;
    }

    public static Collection<?> collectionProperty(Object source, String... candidates) {
        Object value = property(source, candidates);
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) return collection;
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            for (Object item : iterable) copy.add(item);
            return copy;
        }
        if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) copy.add(Array.get(value, i));
            return copy;
        }
        if (value instanceof Map<?, ?> map) return map.entrySet();
        return List.of(value);
    }

    public static List<String> publicZeroArgMembers(Object source) {
        if (source == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
            if (method.getDeclaringClass() == Object.class) continue;
            result.add(method.getName() + ":" + method.getReturnType().getTypeName());
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    public static List<String> declaredFieldNames(Object source) {
        if (source == null) return List.of();
        List<String> result = new ArrayList<>();
        for (Class<?> type = source.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                result.add(type.getSimpleName() + "." + field.getName() + ":" + field.getType().getTypeName());
            }
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    public static String normalizeMember(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
        if (normalized.startsWith("get") && normalized.length() > 3) normalized = normalized.substring(3);
        if (normalized.startsWith("is") && normalized.length() > 2) normalized = normalized.substring(2);
        return normalized;
    }
}
