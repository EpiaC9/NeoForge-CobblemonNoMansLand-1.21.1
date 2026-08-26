package net.epiac9.cobblemonnml.overworld.village.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.IOException;
import java.io.Reader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class VillageDataReloadListener {
    // RESOURCE DIRECTORIES
    private static final String STRUCTURE_DIRECTORY = "village_structures";
    private static final String ROAD_DIRECTORY = "road_materials";
    private static final String BRIDGE_DIRECTORY = "bridge_materials";
    private static final String JSON_SUFFIX = ".json";

    private VillageDataReloadListener() {
    }
    // RESOURCE RELOAD
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(
                (ResourceManagerReloadListener)
                        VillageDataReloadListener::reload
        );
    }
    // RELOAD ALL VILLAGE DATA
    private static void reload(ResourceManager resourceManager) {
        Map<ResourceLocation, VillageStructureDefinition> structures = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> exactRoadMappings = new LinkedHashMap<>();
        List<RoadMaterialRule> roadFamilies = new ArrayList<>();
        BridgeMaterialRule bridgeRule = VillageDataManager.Snapshot.empty().bridgeRule();

        int invalid = 0;
        // STRUCTURES
        for (Map.Entry<ResourceLocation, Resource> entry :
                sortedResources(resourceManager, STRUCTURE_DIRECTORY)) {
            try {
                JsonObject root = readObject( entry.getValue() );
                VillageStructureDefinition definition = parseStructureDefinition( root );
                structures.put( definition.structureId(), definition );
            } catch (RuntimeException | IOException exception) {
                invalid++;
                warnInvalid( entry.getKey(), exception );
            }
        }
        // ROAD MATERIALS
        for (Map.Entry<ResourceLocation, Resource> entry :
                sortedResources(resourceManager, ROAD_DIRECTORY)) {
            try {
                JsonObject root = readObject( entry.getValue() );

                JsonArray exact = getArrayOrEmpty( root, "exact" );
                for (JsonElement element : exact) {
                    JsonObject mapping = requireObject( element, "road exact mapping" );
                    ResourceLocation input = requireBlockId( mapping, "input" );
                    ResourceLocation output = requireBlockId( mapping, "output" );
                    exactRoadMappings.put( input, output );
                }

                JsonArray families = getArrayOrEmpty( root, "families" );
                for (JsonElement element : families) {
                    JsonObject family = requireObject( element, "road family" );
                    String familyName = requireString( family, "family" );
                    ResourceLocation output = requireBlockId( family, "output" );
                    JsonArray inputArray = requireArray( family, "inputs" );

                    List<ResourceLocation> inputs = new ArrayList<>();
                    for (JsonElement inputElement : inputArray) {
                        if (!inputElement.isJsonPrimitive()
                                || !inputElement.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException(
                                    "Road family input must be a block ID string."
                            );
                        }

                        ResourceLocation inputId =
                                requireBlockId( inputElement.getAsString() );
                        inputs.add( inputId );
                    }

                    roadFamilies.add(
                            new RoadMaterialRule(
                                    familyName,
                                    inputs,
                                    output
                            )
                    );
                }
            } catch (RuntimeException | IOException exception) {
                invalid++;
                warnInvalid( entry.getKey(), exception );
            }
        }
        // BRIDGE MATERIALS
        List<Map.Entry<ResourceLocation, Resource>> bridgeResources =
                sortedResources(resourceManager, BRIDGE_DIRECTORY);

        if (!bridgeResources.isEmpty()) {
            Map.Entry<ResourceLocation, Resource> selected =
                    bridgeResources.get( bridgeResources.size() - 1 );

            if (bridgeResources.size() > 1) {
                DebugLog.log(
                        "[CobblemonNML] WARNING: Multiple bridge material files were found. "
                                + "Using the last deterministic resource: "
                                + selected.getKey()
                );
            }

            try {
                JsonObject root = readObject( selected.getValue() );
                bridgeRule =
                        new BridgeMaterialRule(
                                requireBlockId( root, "wooded" ),
                                requireBlockId( root, "rocky" ),
                                requireBlockId( root, "default" )
                        );
            } catch (RuntimeException | IOException exception) {
                invalid++;
                warnInvalid( selected.getKey(), exception );
            }
        }

        VillageDataManager.replaceSnapshot(
                new VillageDataManager.Snapshot(
                        structures,
                        exactRoadMappings,
                        roadFamilies,
                        bridgeRule
                )
        );

        DebugLog.log(
                "[CobblemonNML] Village data loaded: "
                        + structures.size()
                        + " structure definition(s), "
                        + exactRoadMappings.size()
                        + " exact road mapping(s), "
                        + roadFamilies.size()
                        + " road family rule(s), "
                        + invalid
                        + " invalid resource(s)."
        );
    }
    // STRUCTURE PARSER
    private static VillageStructureDefinition parseStructureDefinition(JsonObject root) {
        ResourceLocation structureId = requireResourceLocation( root, "structure" );
        boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();

        return new VillageStructureDefinition(
                structureId,
                enabled
        );
    }
    // RESOURCE COLLECTION
    private static List<Map.Entry<ResourceLocation, Resource>> sortedResources(
            ResourceManager resourceManager,
            String directory
    ) {
        List<Map.Entry<ResourceLocation, Resource>> result =
                new ArrayList<>(
                        resourceManager
                                .listResources(
                                        directory,
                                        id ->
                                                id.getPath().endsWith(JSON_SUFFIX)
                                )
                                .entrySet()
                );

        result.sort( Comparator.comparing( entry -> entry.getKey().toString() ) );
        return result;
    }
    // JSON HELPERS
    private static JsonObject readObject(Resource resource) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            JsonElement parsed = JsonParser.parseReader( reader );

            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException( "Root JSON value must be an object." );
            }

            return parsed.getAsJsonObject();
        }
    }

    private static JsonObject requireObject(JsonElement element, String description) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException( description + " must be a JSON object." );
        }

        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject root, String key) {
        JsonElement element = root.get( key );

        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException( "'" + key + "' must be a JSON array." );
        }

        return element.getAsJsonArray();
    }

    private static JsonArray getArrayOrEmpty(JsonObject root, String key) {
        if (!root.has(key)) {
            return new JsonArray();
        }

        return requireArray( root, key );
    }

    private static String requireString(JsonObject root, String key) {
        JsonElement element = root.get( key );

        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException( "'" + key + "' must be a string." );
        }

        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException( "'" + key + "' cannot be blank." );
        }

        return value;
    }

    private static ResourceLocation requireResourceLocation(JsonObject root, String key) {
        return requireResourceLocation( requireString(root, key) );
    }

    private static ResourceLocation requireResourceLocation(String raw) {
        ResourceLocation id = ResourceLocation.tryParse( raw );

        if (id == null) {
            throw new IllegalArgumentException( "Invalid resource location: " + raw );
        }

        return id;
    }

    private static ResourceLocation requireBlockId(JsonObject root, String key) {
        return requireBlockId( requireString(root, key) );
    }

    private static ResourceLocation requireBlockId(String raw) {
        ResourceLocation id = requireResourceLocation( raw );

        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException( "Unknown block ID: " + id );
        }

        return id;
    }
    // WARNING
    private static void warnInvalid(
            ResourceLocation resourceId,
            Exception exception
    ) {
        DebugLog.log(
                "[CobblemonNML] WARNING: Skipped invalid village data resource "
                        + resourceId
                        + ": "
                        + exception.getMessage()
        );
    }
}
