package net.epiac9.cobblemonnml.dimension.timer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class DungeonTimerCommand {
    private DungeonTimerCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("cobblemonnml")
                        .requires(source -> source.hasPermission(2))
                        .then(
                                Commands.literal("timer")
                                        .then(Commands.literal("pause").executes(context -> pause(context.getSource())))
                                        .then(Commands.literal("unpause").executes(context -> unpause(context.getSource())))
                                        .then(
                                                Commands.literal("advance")
                                                        .then(
                                                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                        .then(Commands.literal("seconds").executes(context ->
                                                                                advance(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))
                                                                        .then(Commands.literal("minutes").executes(context ->
                                                                                advance(context.getSource(), IntegerArgumentType.getInteger(context, "amount") * 60)))
                                                        )
                                        )
                                        .then(
                                                Commands.literal("rewind")
                                                        .then(
                                                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                        .then(Commands.literal("seconds").executes(context ->
                                                                                rewind(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))
                                                                        .then(Commands.literal("minutes").executes(context ->
                                                                                rewind(context.getSource(), IntegerArgumentType.getInteger(context, "amount") * 60)))
                                                        )
                                        )
                                        .then(Commands.literal("end").executes(context -> end(context.getSource())))
                        )
        );
    }

    private static int pause(CommandSourceStack source) {
        if (!DungeonTimer.pause(source.getServer())) {
            source.sendFailure(Component.literal("The dungeon timer is not running or is already paused."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Dungeon timer paused at " + formatTime(DungeonTimer.getSecondsRemaining()) + "."
        ), false);
        return 1;
    }

    private static int unpause(CommandSourceStack source) {
        if (!DungeonTimer.unpause(source.getServer())) {
            source.sendFailure(Component.literal("The dungeon timer is not paused."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Dungeon timer resumed with " + formatTime(DungeonTimer.getSecondsRemaining()) + " remaining."
        ), false);
        return 1;
    }

    private static int advance(CommandSourceStack source, int seconds) {
        if (!DungeonTimer.advance(source.getServer(), seconds)) {
            source.sendFailure(Component.literal("The dungeon timer is not running."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Dungeon timer advanced by " + formatTime(seconds)
                        + ". Remaining: " + formatTime(DungeonTimer.getSecondsRemaining()) + "."
        ), false);
        return 1;
    }

    private static int rewind(CommandSourceStack source, int seconds) {
        if (!DungeonTimer.rewind(source.getServer(), seconds)) {
            source.sendFailure(Component.literal("The dungeon timer is not running."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Dungeon timer rewound by " + formatTime(seconds)
                        + ". Remaining: " + formatTime(DungeonTimer.getSecondsRemaining()) + "."
        ), false);
        return 1;
    }

    private static int end(CommandSourceStack source) {
        if (!DungeonTimer.end(source.getServer())) {
            source.sendFailure(Component.literal("The dungeon timer is not running."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dungeon timer ended."), false);
        return 1;
    }

    private static String formatTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
