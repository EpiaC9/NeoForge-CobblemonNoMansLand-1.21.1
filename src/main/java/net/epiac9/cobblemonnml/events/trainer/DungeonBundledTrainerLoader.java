package net.epiac9.cobblemonnml.events.trainer;

import com.gitlab.srcmc.tbcs.api.TBCS;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonBundledTrainerLoader {
    // CONSTANTS
    private static final String MOD_ID = "cobblemonnml";
    private static final String RESOURCE_DIRECTORY = "trainers";

    /*
     * TBCS searches the instance-level trainers directory.
     * Development:
     * run/trainers/
     * Production:
     * <minecraft instance>/trainers/
     */
    private static final Path OUTPUT_DIRECTORY =
            FMLPaths.GAMEDIR
                    .get()
                    .resolve( "trainers" )
                    .normalize();
    private static final String MANIFEST_FILE = ".cobblemonnml-trainers.txt";
    private static final String MANIFEST_HEADER = "# CobblemonNML bundled trainer manifest v2";
    private static final String JSON_SUFFIX = ".json";
    // CURRENT SERVER / STARTUP EXPORT STATE
    /*
     * During the initial datapack reload the server may not yet be fully started.
     * Once ServerStartedEvent fires, the active server is kept here so a later /reload can immediately refresh TBCS.
     */
    private static MinecraftServer currentServer;

    /*
     * The initial server resource reload happens before ServerStartedEvent.
     * Remember whether that initial reload already exported our bundled trainer files so ServerStartedEvent does not perform
     * the same export a second time.
     */
    private static boolean startupExportAttempted = false;
    private static boolean startupExportSucceeded = false;
    // SERVER STARTED
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        currentServer = event.getServer();
        DebugLog.log( "[CobblemonNML] Bundled trainer loader starting." );

        /*
         * A new server/resource lifecycle must not reuse trainer preset discovery results from an earlier session.
         */
        DungeonTrainerPresets.invalidateCache();
        // DEFENSIVE FALLBACK EXPORT
        /*
         * Normal path:
         * 1. Initial resource reload exports the bundled files.
         * 2. ServerStartedEvent skips duplicate disk I/O.
         * 3. TBCS is loaded once below.
         * If the initial resource reload listener did not run or failed, perform one fallback export here so dungeon
         * trainers still work.
         */
        if (!startupExportAttempted || !startupExportSucceeded) {
            ExportResult fallbackResult = exportBundledTrainers( currentServer .getResourceManager() );
            logExportResult( "server-start fallback", fallbackResult );
            startupExportAttempted = true;
            startupExportSucceeded =
                    fallbackResult.success()
                            && fallbackResult.managed() > 0;
        } else {
            DebugLog.log(
                    "[CobblemonNML] Bundled trainers were already synchronized "
                            + "during the initial resource reload; "
                            + "skipping duplicate startup export."
            );
        }
        // FORCE TBCS TO LOAD THEM
        /*
         * TBCS must see the instance-level files after the initial resource reload has exported them.
         */
        reloadTBCSTrainers();
    }
    // SERVER STOPPED
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        currentServer = null;
        startupExportAttempted = false;
        startupExportSucceeded = false;
        DungeonTrainerPresets.invalidateCache();
    }
    // RESOURCE / DATAPACK RELOAD
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(
                (ResourceManagerReloadListener)
                        resourceManager -> {

                            /*
                             * EasyNPC trainer resources may have changed.
                             * Clear the Theme + Tier preset cache before performing another lookup.
                             */
                            DungeonTrainerPresets.invalidateCache();
                            ExportResult result = exportBundledTrainers( resourceManager );
                            boolean initialResourceReload = currentServer == null;
                            if (initialResourceReload) {
                                startupExportAttempted = true;
                                startupExportSucceeded =
                                        result.success()
                                                && result.managed() > 0;
                                logExportResult( "initial resource reload", result );
                            } else {
                                logExportResult( "resource reload", result );
                            }

                            /*
                             * During initial server creation, currentServer is still null.
                             * ServerStartedEvent will perform the initial TBCS load afterward.
                             * During /reload, currentServer already exists. Preserve the previous behavior
                             * and refresh TBCS after the synchronized files have been updated.
                             */
                            if (currentServer != null && currentServer.isRunning()) {
                                reloadTBCSTrainers();
                            }
                        }
        );
    }
    // INCREMENTAL BUNDLED TRAINER EXPORT
    /*
     * Old behavior deleted every previously exported trainer and rewrote every bundled JSON on every export.
     * New behavior:
     * - read the previous filename + SHA-256 manifest
     * - hash each current bundled resource
     * - create only new files
     * - rewrite only changed/missing files
     * - leave unchanged files untouched
     * - delete only stale files no longer present in resources
     * - write the manifest only if its contents changed
     */
    private static ExportResult exportBundledTrainers(ResourceManager resourceManager) {
        long startedNanos = System.nanoTime();
        if (resourceManager == null) {
            return ExportResult.failure( elapsedMillis( startedNanos ) );
        }
        try {
            // CREATE OUTPUT DIRECTORY
            Files.createDirectories( OUTPUT_DIRECTORY );
            // READ PREVIOUS MANIFEST
            Map<String, String> previousManifest = readManifest();
            // FIND BUNDLED TRAINER JSON FILES
            Map<ResourceLocation, Resource> resources =
                    resourceManager.listResources(
                            RESOURCE_DIRECTORY,
                            id ->
                                    id.getNamespace().equals(MOD_ID) &&
                                            id.getPath().endsWith(JSON_SUFFIX)
                    );
            // DETERMINISTIC RESOURCE ORDER
            List<Map.Entry<ResourceLocation, Resource>>
                    sortedResources = new ArrayList<>( resources.entrySet() );
            sortedResources.sort( Comparator.comparing( entry -> entry .getKey() .toString() ) );
            // NEW MANIFEST + COUNTERS
            Map<String, String> currentManifest = new TreeMap<>();
            int created = 0;
            int updated = 0;
            int unchanged = 0;
            int invalid = 0;
            // SYNCHRONIZE EACH RESOURCE
            for (Map.Entry<ResourceLocation, Resource> entry : sortedResources) {
                ResourceLocation resourceId = entry.getKey();
                Resource resource = entry.getValue();
                String outputFileName = getOutputFileName( resourceId );
                if (outputFileName == null || outputFileName.isBlank()) {
                    invalid++;
                    DebugLog.log( "[CobblemonNML] WARNING: Skipped invalid bundled trainer path: " + resourceId );
                    continue;
                }
                Path outputFile = resolveSafeOutputFile( outputFileName );
                if (outputFile == null) {
                    invalid++;
                    DebugLog.log( "[CobblemonNML] WARNING: Skipped unsafe bundled trainer path: " + resourceId );
                    continue;
                }

                /*
                 * Two resource paths must never flatten into the same TBCS filename. Keep the first deterministic
                 * resource and report the collision.
                 */
                if (currentManifest.containsKey(outputFileName)) {
                    invalid++;
                    DebugLog.log(
                            "[CobblemonNML] WARNING: Bundled trainer filename collision for "
                                    + outputFileName
                                    + " from "
                                    + resourceId
                    );
                    continue;
                }
                byte[] resourceBytes;
                try (InputStream inputStream = resource.open()) {
                    resourceBytes = inputStream.readAllBytes();
                }
                String resourceHash = sha256( resourceBytes );
                currentManifest.put( outputFileName, resourceHash );
                String previousHash = previousManifest.get( outputFileName );
                boolean outputExists = Files.isRegularFile( outputFile );
                // FAST UNCHANGED PATH (V2 MANIFEST)
                if (outputExists && resourceHash.equals(previousHash)) {
                    unchanged++;
                    continue;
                }
                // V1 MANIFEST MIGRATION
                /*
                 * Old manifests stored filenames only.
                 * On the first Pass-10 launch, compare the existing generated file with the bundled resource. If they
                 * already match, keep the file untouched and simply upgrade the manifest to v2.
                 */
                if (outputExists && previousManifest.containsKey(outputFileName) && (previousHash == null || previousHash.isBlank())) {
                    String existingHash = sha256( Files.readAllBytes( outputFile ) );
                    if (resourceHash.equals(existingHash)) {
                        unchanged++;
                        continue;
                    }
                }
                // CREATE OR UPDATE
                Files.write( outputFile, resourceBytes );
                if (outputExists) {
                    updated++;
                } else {
                    created++;
                }
            }
            // REMOVE ONLY STALE PREVIOUS EXPORTS
            int removed = removeStaleExports( previousManifest.keySet(), currentManifest.keySet() );
            // WRITE V2 MANIFEST ONLY IF NEEDED
            boolean manifestUpdated = writeManifestIfChanged( currentManifest );
            return new ExportResult(
                    true,
                    resources.size(),
                    currentManifest.size(),
                    created,
                    updated,
                    unchanged,
                    removed,
                    invalid,
                    manifestUpdated,
                    elapsedMillis( startedNanos )
            );
        } catch (Exception exception) {
            DebugLog.log( "[CobblemonNML] Failed to synchronize bundled trainers." );
            exception.printStackTrace();
            return ExportResult.failure( elapsedMillis( startedNanos ) );
        }
    }
    // CREATE TBCS OUTPUT FILENAME
    private static String getOutputFileName(ResourceLocation resourceId) {
        if (resourceId == null) {
            return null;
        }
        String resourcePath =
                resourceId
                        .getPath()
                        .replace( "\\", "/" );
        String expectedPrefix =
                RESOURCE_DIRECTORY
                        + "/";
        if (!resourcePath.startsWith(expectedPrefix)) {
            return null;
        }
        String relativePath = resourcePath.substring( expectedPrefix.length() );
        if (!relativePath.endsWith(JSON_SUFFIX)) {
            return null;
        }
        // REMOVE .json
        String withoutExtension = relativePath.substring( 0, relativePath.length() - JSON_SUFFIX.length() );
        String[] parts = withoutExtension.split( "/" );

        /*
         * We require at least:
         * <trainer>/<team>
         * or:
         * <theme>/<trainer>/<team>
         */
        if (parts.length < 2) {
            return null;
        }
        // FINAL SEGMENT = TEAM NAME
        String teamName =
                parts[
                        parts.length - 1
                        ];
        if (teamName == null || teamName.isBlank()) {
            return null;
        }
        // EVERYTHING BEFORE TEAM = TRAINER KEY
        StringBuilder trainerKey = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) {
                return null;
            }
            if (!trainerKey.isEmpty()) {
                trainerKey.append( "_" );
            }
            trainerKey.append( part );
        }
        if (trainerKey.isEmpty()) {
            return null;
        }
        // FINAL FLAT TBCS FILENAME
        return trainerKey
                + "__"
                + teamName
                + JSON_SUFFIX;
    }
    // MANIFEST READ
    private static Map<String, String> readManifest() {
        Map<String, String> manifestEntries = new TreeMap<>();
        Path manifest = OUTPUT_DIRECTORY.resolve( MANIFEST_FILE );
        if (!Files.exists(manifest)) {
            return manifestEntries;
        }
        try {
            List<String> lines = Files.readAllLines( manifest, StandardCharsets.UTF_8 );
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf( '\t' );

                if (separator >= 0) {
                    String fileName =
                            line.substring(0, separator)
                                    .trim();
                    String hash =
                            line.substring(separator + 1)
                                    .trim();
                    if (!fileName.isBlank()) {
                        manifestEntries.put( fileName, hash.isBlank() ? null : hash );
                    }
                    continue;
                }
                manifestEntries.put( trimmed, null );
            }
        } catch (IOException exception) {
            DebugLog.log(
                    "[CobblemonNML] WARNING: Failed to read bundled trainer manifest; "
                            + "current resources will be synchronized normally."
            );
        }
        return manifestEntries;
    }
    // REMOVE STALE EXPORTS
    private static int removeStaleExports( Set<String> previousFiles, Set<String> currentFiles ) throws IOException {
        int removed = 0;
        for (String fileName : previousFiles) {
            if (fileName == null || fileName.isBlank() || currentFiles.contains(fileName)) {
                continue;
            }
            Path staleFile = resolveSafeOutputFile( fileName );

            /*
             * Never allow an old/corrupt manifest to delete
             * anything outside trainers/.
             */
            if (staleFile == null) {
                DebugLog.log( "[CobblemonNML] WARNING: Ignored unsafe stale trainer manifest entry: " + fileName );
                continue;
            }
            if (Files.deleteIfExists(staleFile)) {
                removed++;
            }
        }
        return removed;
    }
    // WRITE V2 MANIFEST ONLY WHEN CHANGED
    private static boolean writeManifestIfChanged(Map<String, String> manifestEntries) throws IOException {
        Path manifest = OUTPUT_DIRECTORY.resolve( MANIFEST_FILE );
        List<String> newLines = createNewLines(manifestEntries);
        if (Files.exists(manifest)) {
            List<String> existingLines = Files.readAllLines( manifest, StandardCharsets.UTF_8 );
            if (existingLines.equals(newLines)) {
                return false;
            }
        }
        Files.write( manifest, newLines, StandardCharsets.UTF_8 );
        return true;
    }
    private static @NotNull List<String> createNewLines(Map<String, String> manifestEntries) {
        List<String> newLines = new ArrayList<>();
        newLines.add( MANIFEST_HEADER );
        for (Map.Entry<String, String> entry : manifestEntries.entrySet()) {
            String fileName = entry.getKey();
            String hash = entry.getValue();
            if (fileName == null || fileName.isBlank() || hash == null || hash.isBlank()) {
                continue;
            }
            newLines.add( fileName + "\t" + hash );
        }
        return newLines;
    }
    // SAFE OUTPUT PATH
    private static Path resolveSafeOutputFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        Path outputFile =
                OUTPUT_DIRECTORY.resolve(fileName)
                        .normalize();

        /*
         * Generated TBCS files must live directly in trainers/.
         * This blocks nested paths and path traversal.
         */
        if (!OUTPUT_DIRECTORY.equals(outputFile.getParent())) {
            return null;
        }
        return outputFile;
    }
    // SHA-256
    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance( "SHA-256" );
            return HexFormat
                    .of()
                    .formatHex( digest.digest( bytes ) );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException( "SHA-256 is unavailable.", exception );
        }
    }
    // TIMING
    private static double elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0D;
    }
    // EXPORT SUMMARY
    private static void logExportResult( String reason, ExportResult result ) {
        if (result == null) {
            return;
        }
        if (!result.success()) {
            DebugLog.log( "[CobblemonNML] Bundled trainer synchronization failed during " + reason + "." );
            return;
        }
        DebugLog.logf(
                "[CobblemonNML] Bundled trainer synchronization (%s): "
                        + "discovered=%d, managed=%d, created=%d, updated=%d, "
                        + "unchanged=%d, removed=%d, invalid=%d, "
                        + "manifestUpdated=%s, time=%.2f ms.%n",
                reason,
                result.discovered(),
                result.managed(),
                result.created(),
                result.updated(),
                result.unchanged(),
                result.removed(),
                result.invalid(),
                result.manifestUpdated(),
                result.elapsedMillis()
        );
    }
    // RELOAD TBCS
    private static void reloadTBCSTrainers() {
        try {
            TBCS.getInstance()
                    .loadTrainers();
            DebugLog.log( "[CobblemonNML] TBCS trainer registry reloaded after " + "bundled trainer synchronization." );
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] Failed to reload TBCS trainer registry after "
                            + "bundled trainer synchronization."
            );
            exception.printStackTrace();
        }
    }
    // RESULT
    private record ExportResult(
            boolean success,
            int discovered,
            int managed,
            int created,
            int updated,
            int unchanged,
            int removed,
            int invalid,
            boolean manifestUpdated,
            double elapsedMillis
    ) {
        private static ExportResult failure(double elapsedMillis) {
            return new ExportResult( false, 0, 0, 0, 0, 0, 0, 0, false, elapsedMillis );
        }
    }
}
