package net.epiac9.cobblemonnml.battle.action.audit;

import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Developer-audit helper for optional/required local visual addon jars.
 * It does not change move execution and never copies addon resources into NML.
 */
public final class ActionBattleAddonVisualAudit {
    private static final String EXTRA_MOVE_ANIMS_PREFIX = "extra-move-anims-cobblemon-";
    private static final String ACTION_EFFECT_PREFIX = "data/cobblemon/action_effects/moves/";

    private ActionBattleAddonVisualAudit() {}

    public static Catalog scan(Path gameDirectory) {
        Path libs = locateProjectLibs(gameDirectory);
        if (libs == null) return Catalog.empty();

        List<Path> candidates;
        try (var stream = Files.list(libs)) {
            candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(EXTRA_MOVE_ANIMS_PREFIX) && name.endsWith(".jar");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException ignored) {
            return Catalog.empty();
        }

        if (candidates.isEmpty()) return Catalog.empty();
        Path jar = candidates.getLast();
        LinkedHashMap<String, MutableMoveVisuals> byMove = new LinkedHashMap<>();

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory()) names.add(entry.getName());
            }
            for (String name : names) {
                String moveId = moveIdFromActionEffect(name);
                if (!moveId.isBlank()) {
                    byMove.computeIfAbsent(moveId, ignored -> new MutableMoveVisuals()).actionEffects.add(name);
                }
            }
            for (String name : names) attachRelatedResource(byMove, name);
        } catch (IOException ignored) {
            return Catalog.empty();
        }

        LinkedHashMap<String, MoveVisuals> immutable = new LinkedHashMap<>();
        byMove.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> immutable.put(entry.getKey(), entry.getValue().freeze()));
        return new Catalog(jar.getFileName().toString(), Map.copyOf(immutable));
    }

    private static Path locateProjectLibs(Path gameDirectory) {
        if (gameDirectory == null) return null;
        Path absolute = gameDirectory.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(absolute.resolve("libs"));
        Path parent = absolute.getParent();
        if (parent != null) candidates.add(parent.resolve("libs"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    private static String moveIdFromActionEffect(String entryName) {
        if (!entryName.startsWith(ACTION_EFFECT_PREFIX) || !entryName.endsWith(".json")) return "";
        String file = entryName.substring(ACTION_EFFECT_PREFIX.length(), entryName.length() - 5);
        if (file.contains("/")) return "";
        return ActionBattleMoveMetadataRules.canonicalMoveId(file);
    }

    private static void attachRelatedResource(Map<String, MutableMoveVisuals> byMove, String entryName) {
        String lower = entryName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, MutableMoveVisuals> move : byMove.entrySet()) {
            String id = move.getKey();
            if (!isVisualResourceForMove(lower, id)) continue;
            if (lower.endsWith(".particle.json")) move.getValue().particles.add(entryName);
            else if (lower.endsWith(".animation.json")) move.getValue().animations.add(entryName);
            else if (lower.endsWith(".ogg")) move.getValue().sounds.add(entryName);
            else if (lower.endsWith(".png")) move.getValue().textures.add(entryName);
        }
    }

    private static boolean isVisualResourceForMove(String path, String moveId) {
        return path.contains("/moves/" + moveId + "/")
                || path.contains("/moves/" + moveId + ".")
                || path.contains("/" + moveId + "_")
                || path.contains("/" + moveId + ".");
    }

    public record Catalog(String jarFile, Map<String, MoveVisuals> moves) {
        public static Catalog empty() {
            return new Catalog("", Map.of());
        }

        public boolean present() {
            return !jarFile.isBlank();
        }

        public MoveVisuals forMove(String moveId) {
            return moves.get(moveId);
        }
    }

    public record MoveVisuals(List<String> actionEffects,
                              List<String> particles,
                              List<String> animations,
                              List<String> sounds,
                              List<String> textures) {
        public boolean hasVisuals() {
            return !actionEffects.isEmpty() || !particles.isEmpty() || !animations.isEmpty() || !sounds.isEmpty() || !textures.isEmpty();
        }
    }

    private static final class MutableMoveVisuals {
        private final List<String> actionEffects = new ArrayList<>();
        private final List<String> particles = new ArrayList<>();
        private final List<String> animations = new ArrayList<>();
        private final List<String> sounds = new ArrayList<>();
        private final List<String> textures = new ArrayList<>();

        private MoveVisuals freeze() {
            actionEffects.sort(String::compareTo);
            particles.sort(String::compareTo);
            animations.sort(String::compareTo);
            sounds.sort(String::compareTo);
            textures.sort(String::compareTo);
            return new MoveVisuals(List.copyOf(actionEffects), List.copyOf(particles), List.copyOf(animations), List.copyOf(sounds), List.copyOf(textures));
        }
    }
}
