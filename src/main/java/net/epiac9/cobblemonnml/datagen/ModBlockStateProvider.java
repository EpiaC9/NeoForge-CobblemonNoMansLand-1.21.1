package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.portal.DungeonPortalVisualState;
import net.epiac9.cobblemonnml.registry.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    // 3x3 PORTAL UV SIZE
    /*
     * Minecraft block-model UV coordinates use 0..16.
     * Our portal textures are 48x48 and represent a full 3x3 portal surface, so each portal cell occupies 1/3 of that:
     * 0 1 2
     * 3 4 5
     * 6 7 8
     */
    private static final float PORTAL_UV_CELL = 16.0F / 3.0F;

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super( output, CobblemonNML.MOD_ID, existingFileHelper );
    }
    // REGISTER STATES AND MODELS
    @Override
    protected void registerStatesAndModels() {
        // PORTAL BASE
        ModelFile[] portalBaseSlices = createPortalBaseSlices( modLoc( "block/dungeon_portal_base" ) );
        // PORTAL GLOW
        ModelFile[] portalGlowSlices =
                createPortalTopOverlaySlices(
                        "dungeon_portal_glow",
                        modLoc( "block/dungeon_portal_glow" ),
                        10.01F,
                        10.02F
                );
        // PORTAL ACTIVATION ANIMATION
        ModelFile[] activationSlices =
                createPortalTopOverlaySlices(
                        "dungeon_portal_activation",
                        modLoc( "block/dungeon_portal_activation" ),
                        10.03F,
                        10.04F
                );
        // PORTAL CORE
        registerPortalBlock(
                ModBlocks
                        .DUNGEON_PORTAL_CORE
                        .get(),
                portalBaseSlices,
                portalGlowSlices,
                activationSlices
        );
        // ACTIVE PORTAL
        registerPortalBlock( ModBlocks .DUNGEON_PORTAL .get(), portalBaseSlices, portalGlowSlices, activationSlices );
        // NORMAL MARKERS
        registerCube( ModBlocks.TRAINER_MARKER.get(), "trainer_marker" );
        registerCube( ModBlocks.ALPHA_MARKER.get(), "alpha_marker" );
        registerCube( ModBlocks.RAID_MARKER.get(), "raid_marker" );
        registerCube( ModBlocks.TRIAL_SPAWNER_MARKER.get(), "trial_spawner_marker" );
        registerCube( ModBlocks.ELITE_SPAWNER_MARKER.get(), "elite_spawner_marker" );
        registerCube( ModBlocks.BOSS_SPAWNER_MARKER.get(), "boss_spawner_marker" );
        registerCube( ModBlocks.VAULT_MARKER.get(), "vault_marker" );
        registerCube( ModBlocks.OMINOUS_VAULT_MARKER.get(), "ominous_vault_marker" );
        registerCube( ModBlocks.QUEST_ITEM_MARKER.get(), "quest_item_marker" );
        registerCube( ModBlocks.PORTAL_MARKER.get(), "portal_marker" );
        registerCube( ModBlocks.ROOM_MARKER.get(), "room_marker" );
        registerCube( ModBlocks.GRAVE_MARKER.get(), "grave_marker" );
        // GRAVE PLOT
        registerGravePlot();
        // SPECIAL ROOM MARKER
        registerSpecialRoomMarker();
        // VILLAGE ENTRANCE MARKER
        registerVillageEntranceMarker();
    }
    // REGISTER PORTAL BLOCK
    private void registerPortalBlock(
            Block block,
            ModelFile[] baseSlices,
            ModelFile[] glowSlices,
            ModelFile[] activationSlices
    ) {
        var multipart = getMultipartBuilder(block);
        // BASE
        for (int cell = 0; cell < 9; cell++) {
            multipart
                    .part()
                    .modelFile( baseSlices[cell] )
                    .addModel()
                    .condition( DungeonPortalVisualState.CELL, cell )
                    .end();
        }
        // GLOW
        for (int cell = 0; cell < 9; cell++) {
            multipart
                    .part()
                    .modelFile( glowSlices[cell] )
                    .addModel()
                    .condition( DungeonPortalVisualState.ACTIVATED, true )
                    .condition( DungeonPortalVisualState.CELL, cell )
                    .end();
        }
        // ACTIVATION ANIMATION
        for (int cell = 0; cell < 9; cell++) {
            multipart
                    .part()
                    .modelFile( activationSlices[cell] )
                    .addModel()
                    .condition( DungeonPortalVisualState.ACTIVATED, true )
                    .condition( DungeonPortalVisualState.CELL, cell )
                    .end();
        }
        // ITEM MODEL
        /*
         * Use the middle slice as the item representation.
         */
        simpleBlockItem( block, baseSlices[4] );
    }
    // BASE SLICES
    private ModelFile[] createPortalBaseSlices(ResourceLocation texture) {
        ModelFile[] slices = new ModelFile[9];

        for (int cell = 0; cell < 9; cell++) {
            int column = cell % 3;
            int row = cell / 3;
            float u0 = column * PORTAL_UV_CELL;
            float v0 = row * PORTAL_UV_CELL;
            float u1 = u0 + PORTAL_UV_CELL;
            float v1 = v0 + PORTAL_UV_CELL;

            slices[cell] =
                    createPortalBaseSliceModel( "dungeon_portal_base" + "_cell_" + cell, texture, u0, v0, u1, v1 );
        }

        return slices;
    }
    // BASE SLICE MODEL
    private ModelFile createPortalBaseSliceModel(
            String name,
            ResourceLocation texture,
            float u0,
            float v0,
            float u1,
            float v1
    ) {
        BlockModelBuilder model =
                models()
                        .getBuilder( name )
                        .texture( "particle", texture )
                        .texture( "portal", texture )
                        .renderType( "minecraft:translucent" )
                        .ao( false );

        /*
         * IMPORTANT:
         * Only render the TOP face.
         * This removes the visible slab-like side walls the player can currently see around the 3x3 portal.
         */
        model.element()
                .from( 0.0F, 10.0F, 0.0F )
                .to( 16.0F, 10.01F, 16.0F )
                .face(Direction.UP)
                .texture("#portal")
                .uvs( u0, v0, u1, v1 )
                .tintindex(0)
                .end()
                .end();

        return model;
    }
    // OVERLAY SLICES
    private ModelFile[] createPortalTopOverlaySlices(
            String modelPrefix,
            ResourceLocation texture,
            float fromY,
            float toY
    ) {
        ModelFile[] slices = new ModelFile[9];

        for (int cell = 0; cell < 9; cell++) {
            int column = cell % 3;
            int row = cell / 3;
            float u0 = column * PORTAL_UV_CELL;
            float v0 = row * PORTAL_UV_CELL;
            float u1 = u0 + PORTAL_UV_CELL;
            float v1 = v0 + PORTAL_UV_CELL;

            slices[cell] =
                    createPortalTopOverlaySliceModel(
                            modelPrefix
                                    + "_cell_"
                                    + cell,
                            texture,
                            fromY,
                            toY,
                            u0,
                            v0,
                            u1,
                            v1
                    );
        }

        return slices;
    }
    // ONE OVERLAY SLICE MODEL
    private ModelFile createPortalTopOverlaySliceModel(
            String name,
            ResourceLocation texture,
            float fromY,
            float toY,
            float u0,
            float v0,
            float u1,
            float v1
    ) {
        BlockModelBuilder model =
                models()
                        .getBuilder( name )
                        .texture( "particle", texture )
                        .texture( "overlay", texture )
                        .renderType( "minecraft:translucent" )
                        .ao( false );

        /*
         * Top face only.
         * This keeps glow + animation flat and prevents any visible side walls.
         */
        model.element()
                .from( 0.0F, fromY, 0.0F )
                .to( 16.0F, toY, 16.0F )
                .face(Direction.UP)
                .texture("#overlay")
                .uvs( u0, v0, u1, v1 )
                .tintindex(0)
                .end()
                .end();

        return model;
    }
    // NORMAL CUBE
    private void registerCube( Block block, String name ) {
        ModelFile model =
                models()
                        .cubeAll( name, modLoc( "block/" + name ) );

        simpleBlock( block, model );
        simpleBlockItem( block, model );
    }
    // GRAVE PLOT
    private void registerGravePlot() {
        ResourceLocation texture = modLoc( "block/grave_plot" );

        BlockModelBuilder model =
                models()
                        .getBuilder( "grave_plot" )
                        .texture( "particle", texture )
                        .texture( "grave_plot", texture )
                        .renderType( "minecraft:cutout" )
                        .ao( false );

        /*
         * Render the grave plot as a one-pixel-high layer,
         * similar to a single snow layer.
         */
        model.element()
                .from( 0.0F, 0.0F, 0.0F )
                .to( 16.0F, 1.0F, 16.0F )
                // TOP
                .face(Direction.UP)
                .texture("#grave_plot")
                .uvs( 0.0F, 0.0F, 16.0F, 16.0F )
                .end()
                // BOTTOM
                .face(Direction.DOWN)
                .texture("#grave_plot")
                .uvs( 0.0F, 0.0F, 16.0F, 16.0F )
                .cullface(Direction.DOWN)
                .end()
                // NORTH
                .face(Direction.NORTH)
                .texture("#grave_plot")
                .uvs( 0.0F, 15.0F, 16.0F, 16.0F )
                .end()
                // SOUTH
                .face(Direction.SOUTH)
                .texture("#grave_plot")
                .uvs( 0.0F, 15.0F, 16.0F, 16.0F )
                .end()
                // WEST
                .face(Direction.WEST)
                .texture("#grave_plot")
                .uvs( 0.0F, 15.0F, 16.0F, 16.0F )
                .end()
                // EAST
                .face(Direction.EAST)
                .texture("#grave_plot")
                .uvs( 0.0F, 15.0F, 16.0F, 16.0F )
                .end()

                .end();

        simpleBlock( ModBlocks .GRAVE_PLOT .get(), model );

        simpleBlockItem( ModBlocks .GRAVE_PLOT .get(), model );
    }
    // SPECIAL ROOM MARKER
    private void registerSpecialRoomMarker() {
        ResourceLocation sideTexture = modLoc( "block/special_room_marker_side" );
        ResourceLocation frontTexture = modLoc( "block/special_room_marker_front" );

        ModelFile model =
                models()
                        .orientable( "special_room_marker", sideTexture, frontTexture, sideTexture );

        horizontalBlock( ModBlocks .SPECIAL_ROOM_MARKER .get(), model );

        simpleBlockItem( ModBlocks .SPECIAL_ROOM_MARKER .get(), model );
    }
    // VILLAGE ENTRANCE MARKER
    private void registerVillageEntranceMarker() {
        ResourceLocation sideTexture = modLoc( "block/village_entrance_marker_side" );
        ResourceLocation frontTexture = modLoc( "block/village_entrance_marker_front" );

        ModelFile model =
                models()
                        .orientable( "village_entrance_marker", sideTexture, frontTexture, sideTexture );

        horizontalBlock( ModBlocks.VILLAGE_ENTRANCE_MARKER.get(), model );

        simpleBlockItem( ModBlocks.VILLAGE_ENTRANCE_MARKER.get(), model );
    }

}
