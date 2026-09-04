package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.epiac9.cobblemonnml.block.DungeonMarkerBlock;
import net.epiac9.cobblemonnml.overworld.village.cemetery.GraveMarkerBlock;
import net.epiac9.cobblemonnml.overworld.village.cemetery.GravePlotBlock;
import net.epiac9.cobblemonnml.block.SpecialRoomMarkerBlock;
import net.epiac9.cobblemonnml.block.VillageEntranceMarkerBlock;

import net.epiac9.cobblemonnml.portal.DungeonPortalBlock;
import net.epiac9.cobblemonnml.portal.DungeonPortalCoreBlock;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.AquaBubbleBlock;

import net.minecraft.world.item.BlockItem;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    // REGISTRIES
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks( CobblemonNML.MOD_ID );
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems( CobblemonNML.MOD_ID );
    public static final DeferredBlock<AquaBubbleBlock> AQUA_BUBBLE = BLOCKS.register(
            "aqua_bubble",
            () -> new AquaBubbleBlock(BlockBehaviour.Properties.of()
                    .noCollission().noOcclusion().strength(-1.0F).noLootTable().lightLevel(state -> 6))
    );
    // DUNGEON PORTAL CORE
    public static final DeferredBlock<DungeonPortalCoreBlock>
            DUNGEON_PORTAL_CORE = BLOCKS.register( "dungeon_portal_core", DungeonPortalCoreBlock::new );
    // ACTIVE DUNGEON PORTAL
    /*
     * There is now only ONE active portal block.
     * Tier is stored in DungeonPortalVisualState.TIER and is represented by particles instead of separate blocks or tier-pattern textures.
     */
    public static final DeferredBlock<DungeonPortalBlock>
            DUNGEON_PORTAL = BLOCKS.register( "dungeon_portal", DungeonPortalBlock::new );
    // PORTAL BLOCK ITEMS
    public static final DeferredItem<BlockItem>
            DUNGEON_PORTAL_CORE_ITEM = ITEMS.registerSimpleBlockItem( "dungeon_portal_core", DUNGEON_PORTAL_CORE );
    public static final DeferredItem<BlockItem>
            DUNGEON_PORTAL_ITEM = ITEMS.registerSimpleBlockItem( "dungeon_portal", DUNGEON_PORTAL );
    // DUNGEON ENCOUNTER MARKERS
    public static final DeferredBlock<DungeonMarkerBlock>
            TRAINER_MARKER = registerMarker( "trainer_marker", "trainer" );
    public static final DeferredBlock<DungeonMarkerBlock>
            ALPHA_MARKER = registerMarker( "alpha_marker", "alpha" );
    public static final DeferredBlock<DungeonMarkerBlock>
            RAID_MARKER = registerMarker( "raid_marker", "raid" );
    public static final DeferredBlock<DungeonMarkerBlock>
            TRIAL_SPAWNER_MARKER = registerMarker( "trial_spawner_marker", "trial_spawner" );
    public static final DeferredBlock<DungeonMarkerBlock>
            ELITE_SPAWNER_MARKER = registerMarker( "elite_spawner_marker", "elite_spawner" );
    public static final DeferredBlock<DungeonMarkerBlock>
            BOSS_SPAWNER_MARKER = registerMarker( "boss_spawner_marker", "boss_spawner" );
    public static final DeferredBlock<DungeonMarkerBlock>
            VAULT_MARKER = registerMarker( "vault_marker", "vault" );
    public static final DeferredBlock<DungeonMarkerBlock>
            OMINOUS_VAULT_MARKER = registerMarker( "ominous_vault_marker", "ominous_vault" );
    public static final DeferredBlock<DungeonMarkerBlock>
            QUEST_ITEM_MARKER = registerMarker( "quest_item_marker", "quest_item" );
    // RETURN PORTAL MARKER
    public static final DeferredBlock<DungeonMarkerBlock>
            PORTAL_MARKER = registerMarker( "portal_marker", "portal" );
    // ROOM MARKER
    public static final DeferredBlock<DungeonMarkerBlock>
            ROOM_MARKER =
            BLOCKS.register(
                    "room_marker",
                    () ->
                            new DungeonMarkerBlock(
                                    "room",
                                    BlockBehaviour.Properties
                                            .of()
                                            .strength( -1.0F, 3600000.0F )
                                            .noLootTable()
                            )
            );
    // SPECIAL ROOM MARKER
    public static final DeferredBlock<SpecialRoomMarkerBlock>
            SPECIAL_ROOM_MARKER =
            BLOCKS.register(
                    "special_room_marker",
                    () ->
                            new SpecialRoomMarkerBlock(
                                    BlockBehaviour.Properties
                                            .of()
                                            .strength( -1.0F, 3600000.0F )
                                            .noLootTable()
                            )
            );
    // VILLAGE ENTRANCE MARKER
    public static final DeferredBlock<VillageEntranceMarkerBlock>
            VILLAGE_ENTRANCE_MARKER =
            BLOCKS.register(
                    "village_entrance_marker",
                    () ->
                            new VillageEntranceMarkerBlock(
                                    BlockBehaviour.Properties
                                            .of()
                                            .strength( -1.0F, 3600000.0F )
                                            .noLootTable()
                            )
            );
    // CEMETERY GRAVE MARKER
    public static final DeferredBlock<GraveMarkerBlock>
            GRAVE_MARKER =
            BLOCKS.register(
                    "grave_marker",
                    () -> new GraveMarkerBlock(
                            BlockBehaviour.Properties.of()
                                    .strength( -1.0F, 3600000.0F ).noLootTable()
                    )
            );
    // CEMETERY GRAVE PLOT
    public static final DeferredBlock<GravePlotBlock>
            GRAVE_PLOT =
            BLOCKS.register(
                    "grave_plot",
                    () ->
                            new GravePlotBlock(
                                    BlockBehaviour.Properties
                                            .of()
                                            .strength(0.1F)
                                            .noLootTable()
                                            .noOcclusion()
                            )
            );
    // MARKER BLOCK ITEMS
    public static final DeferredItem<BlockItem>
            TRAINER_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "trainer_marker", TRAINER_MARKER );
    public static final DeferredItem<BlockItem>
            ALPHA_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "alpha_marker", ALPHA_MARKER );
    public static final DeferredItem<BlockItem>
            RAID_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "raid_marker", RAID_MARKER );
    public static final DeferredItem<BlockItem>
            TRIAL_SPAWNER_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "trial_spawner_marker", TRIAL_SPAWNER_MARKER );
    public static final DeferredItem<BlockItem>
            ELITE_SPAWNER_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "elite_spawner_marker", ELITE_SPAWNER_MARKER );
    public static final DeferredItem<BlockItem>
            BOSS_SPAWNER_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "boss_spawner_marker", BOSS_SPAWNER_MARKER );
    public static final DeferredItem<BlockItem>
            VAULT_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "vault_marker", VAULT_MARKER );
    public static final DeferredItem<BlockItem>
            OMINOUS_VAULT_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "ominous_vault_marker", OMINOUS_VAULT_MARKER );
    public static final DeferredItem<BlockItem>
            QUEST_ITEM_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "quest_item_marker", QUEST_ITEM_MARKER );
    public static final DeferredItem<BlockItem>
            PORTAL_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "portal_marker", PORTAL_MARKER );
    public static final DeferredItem<BlockItem>
            ROOM_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "room_marker", ROOM_MARKER );
    public static final DeferredItem<BlockItem>
            SPECIAL_ROOM_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "special_room_marker", SPECIAL_ROOM_MARKER );
    public static final DeferredItem<BlockItem>
            VILLAGE_ENTRANCE_MARKER_ITEM =
            ITEMS.registerSimpleBlockItem( "village_entrance_marker", VILLAGE_ENTRANCE_MARKER );
    public static final DeferredItem<BlockItem>
            GRAVE_MARKER_ITEM = ITEMS.registerSimpleBlockItem( "grave_marker", GRAVE_MARKER );
    public static final DeferredItem<BlockItem>
            GRAVE_PLOT_ITEM = ITEMS.registerSimpleBlockItem( "grave_plot", GRAVE_PLOT );
    // REGISTER NORMAL MARKER
    private static DeferredBlock<DungeonMarkerBlock> registerMarker(String blockId, String markerId) {
        return BLOCKS.register(
                blockId,
                () -> new DungeonMarkerBlock(
                        markerId,
                        BlockBehaviour.Properties
                                .of()
                                .strength(1.0F)
                                .sound(SoundType.STONE)
                )
        );
    }
}
