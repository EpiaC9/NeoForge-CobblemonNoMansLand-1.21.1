package net.epiac9.cobblemonnml.overworld.village.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VillageDataManager {
    // IMMUTABLE CURRENT SNAPSHOT
    private static volatile Snapshot snapshot = Snapshot.empty();

    private VillageDataManager() {
    }
    // STRUCTURE LOOKUP
    public static Optional<VillageStructureDefinition> getStructure(ResourceLocation structureId) {
        if (structureId == null) {
            return Optional.empty();
        }

        VillageStructureDefinition definition = snapshot.structures().get( structureId );
        if (definition == null || !definition.enabled()) {
            return Optional.empty();
        }

        return Optional.of( definition );
    }
    // ROAD SURFACE RESOLUTION
    public static BlockState resolveRoadSurface(BlockState original, RegistryAccess registries) {
        if (original == null) {
            return null;
        }

        ResourceLocation inputId = BuiltInRegistries.BLOCK.getKey( original.getBlock() );
        if (inputId == null) {
            return original;
        }

        ResourceLocation exactOutput = snapshot.exactRoadMappings().get( inputId );
        if (exactOutput != null) {
            return resolveBlockStateOrOriginal( exactOutput, original );
        }

        for (RoadMaterialRule rule : snapshot.roadFamilies()) {
            if (rule.inputs().contains(inputId)) {
                return resolveBlockStateOrOriginal( rule.output(), original );
            }
        }

        return original;
    }
    // BRIDGE MATERIAL RESOLUTION
    public static BlockState resolveBridgeDeck(
            VillageBridgeEnvironment environment
    ) {
        BridgeMaterialRule rule = snapshot.bridgeRule();
        ResourceLocation selected;

        if (environment == VillageBridgeEnvironment.WOODED) {
            selected = rule.wooded();
        } else if (environment == VillageBridgeEnvironment.ROCKY) {
            selected = rule.rocky();
        } else {
            selected = rule.fallback();
        }

        Block block = BuiltInRegistries.BLOCK.get( selected );
        return block.defaultBlockState();
    }
    // CURRENT RULES
    public static List<RoadMaterialRule> getRoadFamilies() {
        return snapshot.roadFamilies();
    }

    public static BridgeMaterialRule getBridgeRule() {
        return snapshot.bridgeRule();
    }
    // SNAPSHOT REPLACEMENT
    static void replaceSnapshot(Snapshot replacement) {
        snapshot = replacement == null ? Snapshot.empty() : replacement;
    }
    // BLOCK RESOLUTION
    private static BlockState resolveBlockStateOrOriginal(
            ResourceLocation outputId,
            BlockState original
    ) {
        if (!BuiltInRegistries.BLOCK.containsKey(outputId)) {
            return original;
        }

        return BuiltInRegistries.BLOCK.get( outputId ).defaultBlockState();
    }
    // BRIDGE ENVIRONMENT
    public enum VillageBridgeEnvironment {
        WOODED,
        ROCKY,
        NEUTRAL
    }
    // SNAPSHOT
    record Snapshot(
            Map<ResourceLocation, VillageStructureDefinition> structures,
            Map<ResourceLocation, ResourceLocation> exactRoadMappings,
            List<RoadMaterialRule> roadFamilies,
            BridgeMaterialRule bridgeRule
    ) {
        Snapshot {
            structures = Map.copyOf( structures );
            exactRoadMappings = Map.copyOf( exactRoadMappings );
            roadFamilies = List.copyOf( roadFamilies );
        }

        static Snapshot empty() {
            ResourceLocation oak =
                    ResourceLocation.fromNamespaceAndPath( "minecraft", "oak_planks" );
            ResourceLocation cobble =
                    ResourceLocation.fromNamespaceAndPath( "minecraft", "cobblestone" );

            return new Snapshot(
                    Map.of(),
                    Map.of(),
                    List.of(),
                    new BridgeMaterialRule( oak, cobble, oak )
            );
        }
    }
}
