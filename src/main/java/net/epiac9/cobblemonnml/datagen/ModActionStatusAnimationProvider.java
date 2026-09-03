package net.epiac9.cobblemonnml.datagen;

import com.google.gson.JsonObject;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class ModActionStatusAnimationProvider implements DataProvider {
    private static final int FRAME_SIZE = 16;
    private static final int FRAME_TIME = 4;

    private final Path sourceStatusRoot;
    private final Path generatedStatusRoot;

    public ModActionStatusAnimationProvider(PackOutput output) {
        Path generatedResourcesRoot = output.getOutputFolder().toAbsolutePath().normalize();
        Path srcRoot = generatedResourcesRoot.getParent().getParent();
        Path mainResourcesRoot = srcRoot.resolve("main").resolve("resources").normalize();
        this.sourceStatusRoot = mainResourcesRoot
                .resolve("assets")
                .resolve(CobblemonNML.MOD_ID)
                .resolve("textures")
                .resolve("gui")
                .resolve("action")
                .resolve("status")
                .normalize();
        this.generatedStatusRoot = generatedResourcesRoot
                .resolve("assets")
                .resolve(CobblemonNML.MOD_ID)
                .resolve("textures")
                .resolve("gui")
                .resolve("action")
                .resolve("status")
                .normalize();
    }

    @Override
    public @NotNull CompletableFuture<?> run(@NotNull CachedOutput cachedOutput) {
        if (!Files.isDirectory(sourceStatusRoot)) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<?>> futures = new ArrayList<>();
        try (Stream<Path> files = Files.list(sourceStatusRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(ModActionStatusAnimationProvider::isPng)
                    .sorted()
                    .forEach(png -> futures.add(DataProvider.saveStable(
                            cachedOutput,
                            createAnimationMetadata(),
                            generatedStatusRoot.resolve(png.getFileName().toString() + ".mcmeta")
                    )));
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    static JsonObject createAnimationMetadata() {
        JsonObject animation = new JsonObject();
        animation.addProperty("width", FRAME_SIZE);
        animation.addProperty("height", FRAME_SIZE);
        animation.addProperty("frametime", FRAME_TIME);
        animation.addProperty("interpolate", false);

        JsonObject root = new JsonObject();
        root.add("animation", animation);
        return root;
    }

    private static boolean isPng(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".png");
    }

    @Override
    public @NotNull String getName() {
        return "CobblemonNML ACTION status animation metadata";
    }
}
