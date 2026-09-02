package net.epiac9.cobblemonnml.events.trial;

import com.lemenok.cobblemontrialsedition.block.entity.CobblemonTrialSpawnerEntity;
import com.lemenok.cobblemontrialsedition.builder.TrialSpawnerBuilder;
import com.lemenok.cobblemontrialsedition.caches.CacheType;
import com.lemenok.cobblemontrialsedition.platform.Services;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.NotNull;

public final class DungeonTrialSpawnerManager {
    // SPAWNER TYPE
    public enum SpawnerType {
        NORMAL,
        ELITE,
        BOSS
    }
    // SPAWN
    public static boolean spawn( ServerLevel level, BlockPos pos, SpawnerType type ) {
        if (level == null || pos == null || type == null) {
            return false;
        }
        // ACTIVE DUNGEON TIER
        DungeonTier tier =
                DungeonSession
                        .getTier();
        if (tier == null) {
            DebugLog.log( "Cannot create dungeon trial spawner: " + "no dungeon tier is active." );
            return false;
        }
        // ACTIVE DUNGEON THEME
        DungeonTheme theme =
                DungeonSession
                        .getTheme();
        if (theme == null) {
            DebugLog.log( "Cannot create dungeon trial spawner: " + "no dungeon theme is active." );
            return false;
        }
        // THEMED STRUCTURE ID
        ResourceLocation structureId = getStructureId( theme, tier );
        // SPAWNER CLASS PLACEHOLDER
        ResourceLocation placeholderEntity = getPlaceholderEntity( type );
        // CREATE PLACEHOLDER NBT
        CompoundTag nbt = createTrialSpawnerNbt( placeholderEntity );
        // VANILLA TRIAL SPAWNER TEMPLATE INFO
        TrialSpawnerBuilder builder = getBuilder(pos, nbt, structureId);
        // DEBUG
        DebugLog.log( "Creating themed dungeon trial spawner." );
        DebugLog.log( "Theme: " + theme.getDisplayName() );
        DebugLog.log( "Tier: " + tier.getDisplayName());
        DebugLog.log( "Spawner type: " + type );
        DebugLog.log( "Trials structure ID: " + structureId );
        DebugLog.log( "Placeholder entity: " + placeholderEntity );
        DebugLog.log( "Resolved builder entity: " + builder.getEntityId());
        DebugLog.log( "Position: " + pos );
        // FIND TRIALS EDITION CONFIG
        if (!builder.doesConfigurationExistForReplacement( CacheType.STRUCTURE )) {
            DebugLog.log( "FAILED to create " + theme.getDisplayName() + " " + type + " trial spawner." );
            DebugLog.log( "No Trials Edition configuration exists for:" );
            DebugLog.log( "Structure: " + structureId );
            DebugLog.log( "Placeholder: " + placeholderEntity );
            return false;
        }
        // BUILD COBBLEMON TRIAL SPAWNER
        CobblemonTrialSpawnerEntity spawnerEntity =
                builder.buildCobblemonTrialSpawnerBlock( level.registryAccess(), level );
        if (spawnerEntity == null) {
            DebugLog.log( "FAILED to build Cobblemon Trial Spawner." );
            return false;
        }
        // PLACE COBBLEMON SPAWNER BLOCK
        BlockState cobblemonSpawnerState =
                Services.PLATFORM
                        .getCobblemonTrialSpawnerBlock()
                        .defaultBlockState();
        level.setBlock( pos, cobblemonSpawnerState, 3 );
        // INSTALL BLOCK ENTITY
        level.setBlockEntity( spawnerEntity );
        spawnerEntity.setChanged();
        // SYNC
        level.sendBlockUpdated( pos, cobblemonSpawnerState, cobblemonSpawnerState, 3 );
        // SUCCESS
        DebugLog.log( theme.getDisplayName() + " " + type + " Cobblemon Trial Spawner created successfully at " + pos );
        return true;
    }
    private static @NotNull TrialSpawnerBuilder getBuilder(BlockPos pos, CompoundTag nbt, ResourceLocation structureId) {
        BlockState vanillaTrialSpawner =
                Blocks.TRIAL_SPAWNER
                        .defaultBlockState();
        StructureTemplate.StructureBlockInfo blockInfo =
                new StructureTemplate.StructureBlockInfo( pos, vanillaTrialSpawner, nbt );
        // TRIALS EDITION BUILDER
        TrialSpawnerBuilder builder = new TrialSpawnerBuilder( blockInfo );
        builder.setStructureId( structureId );
        builder.setEntityid( nbt );
        return builder;
    }
    // CREATE PLACEHOLDER NBT
    private static CompoundTag createTrialSpawnerNbt( ResourceLocation entityId ) {
        CompoundTag root = new CompoundTag();
        CompoundTag normalConfig = new CompoundTag();
        ListTag spawnPotentials = new ListTag();
        CompoundTag entry = createEntry(entityId);
        spawnPotentials.add( entry );
        normalConfig.put( "spawn_potentials", spawnPotentials );
        root.put( "normal_config", normalConfig );
        return root;
    }
    private static @NotNull CompoundTag createEntry(ResourceLocation entityId) {
        CompoundTag entry = new CompoundTag();
        CompoundTag data = new CompoundTag();
        CompoundTag entity = new CompoundTag();
        entity.putString( "id", entityId.toString() );
        data.put( "entity", entity );
        entry.put( "data", data );
        entry.putInt( "weight", 1 );
        return entry;
    }
    // THEMED STRUCTURE ID
    private static ResourceLocation getStructureId( DungeonTheme theme, DungeonTier tier ) {
        String tierPath = getTierPath( tier );

        /*
         * Examples: cobblemonnml:dungeon/ghost/tier1
         */
        String path =
                "dungeon/"
                        + theme.getId()
                        + "/"
                        + tierPath;
        return ResourceLocation
                .fromNamespaceAndPath( CobblemonNML.MOD_ID, path );
    }
    // TIER PATH
    private static String getTierPath(DungeonTier tier) {
        return switch (tier) {
            case TIER_1 -> "tier1";
            case TIER_2 -> "tier2";
            case TIER_3 -> "tier3";
            case TIER_4 -> "tier4";
        };
    }
    // PLACEHOLDER ENTITY
    private static ResourceLocation getPlaceholderEntity(SpawnerType type) {
        return switch (type) {
            // NORMAL
            case NORMAL ->
                    ResourceLocation.withDefaultNamespace( "zombie" );
            // ELITE
            case ELITE ->
                    ResourceLocation.withDefaultNamespace( "skeleton" );
            // BOSS
            case BOSS ->
                    ResourceLocation.withDefaultNamespace( "breeze" );
        };
    }
}
