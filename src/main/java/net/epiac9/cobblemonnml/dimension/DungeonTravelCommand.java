package net.epiac9.cobblemonnml.dimension;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.dimension.generation.DungeonGenerationQueue;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.registry.ModAttachments;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class DungeonTravelCommand {
    private DungeonTravelCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("cobblemonnml")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("enter").executes(context -> enter(context.getSource())))
                        .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
        );
    }

    private static int enter(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        if (player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            source.sendFailure(Component.literal("You're already in a dungeon."));
            return 0;
        }

        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal("You must be in the Overworld to enter the dungeon."));
            return 0;
        }

        if (DungeonSession.isActive() || DungeonGenerationQueue.isGenerating()) {
            source.sendFailure(Component.literal("A dungeon is already active."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        ServerLevel dungeonLevel = server.getLevel(DungeonDimension.DUNGEON_DIMENSION);

        if (overworld == null || dungeonLevel == null) {
            source.sendFailure(Component.literal("The dungeon dimension is not available."));
            return 0;
        }

        if (!DungeonSlotManager.selectNextAvailableSlot()) {
            source.sendFailure(Component.literal("All dungeon slots are currently busy resetting."));
            return 0;
        }

        RandomSource random = overworld.getRandom();
        DungeonTheme theme = DungeonTheme.getRandom(random);
        DungeonTier tier = getWeightedRandomTier(random);

        player.setData(
                ModAttachments.RETURN_POSITION,
                player.blockPosition().immutable()
        );

        DungeonSession.start(
                theme,
                tier,
                player.getUUID()
        );

        DungeonGenerationQueue.setCommandEntryPlayer(player);

        boolean generated =
                DungeonDimension.generateJigsawDungeon(
                        dungeonLevel,
                        overworld,
                        null,
                        tier
                );

        if (!generated) {
            DungeonGenerationQueue.setCommandEntryPlayer(null);
            DungeonSession.end();
            source.sendFailure(Component.literal("Dungeon generation failed to start."));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Generating "
                                + theme.getDisplayName()
                                + " "
                                + tier.getDisplayName()
                                + " dungeon. You will enter when generation is complete."
                ),
                false
        );

        DebugLog.log(
                "[CobblemonNML] Command dungeon entry requested by "
                        + player.getGameProfile().getName()
                        + " | "
                        + theme.getDisplayName()
                        + " | "
                        + tier.getDisplayName()
        );

        return 1;
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            source.sendFailure(Component.literal("You're not in a dungeon."));
            return 0;
        }

        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("The Overworld is not available."));
            return 0;
        }

        BlockPos returnPos =
                player.hasData(ModAttachments.RETURN_POSITION)
                        ? player.getData(ModAttachments.RETURN_POSITION)
                        : overworld.getSharedSpawnPos();

        player.teleportTo(
                overworld,
                returnPos.getX() + 0.5D,
                returnPos.getY() + 1.0D,
                returnPos.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );

        source.sendSuccess(
                () -> Component.literal("You left the dungeon."),
                false
        );

        return 1;
    }

    private static DungeonTier getWeightedRandomTier(RandomSource random) {
        int roll = random.nextInt(100);

        if (roll < 50) {
            return DungeonTier.TIER_1;
        }
        if (roll < 80) {
            return DungeonTier.TIER_2;
        }
        if (roll < 95) {
            return DungeonTier.TIER_3;
        }
        return DungeonTier.TIER_4;
    }
}
