package net.epiac9.cobblemonnml.dimension.timer;

import com.mojang.brigadier.CommandDispatcher;
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
    private DungeonTimerCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event
    ) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                event.getDispatcher();

        dispatcher.register(
                Commands.literal("cobblemonnml")
                        .requires(
                                source ->
                                        source.hasPermission(2)
                        )
                        .then(
                                Commands.literal("timer")
                                        .then(
                                                Commands.literal("pause")
                                                        .executes(
                                                                context ->
                                                                        pause(
                                                                                context.getSource()
                                                                        )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("unpause")
                                                        .executes(
                                                                context ->
                                                                        unpause(
                                                                                context.getSource()
                                                                        )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("advance")
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                IntegerArgumentType.integer(1)
                                                                        )
                                                                        .then(
                                                                                Commands.literal("seconds")
                                                                                        .executes(
                                                                                                context ->
                                                                                                        adjust(
                                                                                                                context.getSource(),
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                ),
                                                                                                                false,
                                                                                                                false
                                                                                                        )
                                                                                        )
                                                                        )
                                                                        .then(
                                                                                Commands.literal("minutes")
                                                                                        .executes(
                                                                                                context ->
                                                                                                        adjust(
                                                                                                                context.getSource(),
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                ),
                                                                                                                true,
                                                                                                                false
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("rewind")
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                IntegerArgumentType.integer(1)
                                                                        )
                                                                        .then(
                                                                                Commands.literal("seconds")
                                                                                        .executes(
                                                                                                context ->
                                                                                                        adjust(
                                                                                                                context.getSource(),
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                ),
                                                                                                                false,
                                                                                                                true
                                                                                                        )
                                                                                        )
                                                                        )
                                                                        .then(
                                                                                Commands.literal("minutes")
                                                                                        .executes(
                                                                                                context ->
                                                                                                        adjust(
                                                                                                                context.getSource(),
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                ),
                                                                                                                true,
                                                                                                                true
                                                                                                        )
                                                                                        )
                                                                        )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("end")
                                                        .executes(
                                                                context ->
                                                                        end(
                                                                                context.getSource()
                                                                        )
                                                        )
                                        )
                        )
        );
    }

    private static int pause(
            CommandSourceStack source
    ) {
        if (!DungeonTimer.isActive()) {
            return failure(
                    source,
                    "There is no active dungeon timer."
            );
        }

        if (DungeonTimer.isPaused()) {
            return failure(
                    source,
                    "The dungeon timer is already paused."
            );
        }

        DungeonTimer.pause(
                source.getServer()
        );

        return success(
                source,
                "Dungeon timer paused at "
                        + formatTime(
                        DungeonTimer.getSecondsRemaining()
                )
                        + "."
        );
    }

    private static int unpause(
            CommandSourceStack source
    ) {
        if (!DungeonTimer.isActive()) {
            return failure(
                    source,
                    "There is no active dungeon timer."
            );
        }

        if (!DungeonTimer.isPaused()) {
            return failure(
                    source,
                    "The dungeon timer is not paused."
            );
        }

        DungeonTimer.unpause(
                source.getServer()
        );

        return success(
                source,
                "Dungeon timer resumed with "
                        + formatTime(
                        DungeonTimer.getSecondsRemaining()
                )
                        + " remaining."
        );
    }

    private static int adjust(
            CommandSourceStack source,
            int amount,
            boolean minutes,
            boolean rewind
    ) {
        if (!DungeonTimer.isActive()) {
            return failure(
                    source,
                    "There is no active dungeon timer."
            );
        }

        long requestedSeconds =
                minutes
                        ? (long) amount * 60L
                        : amount;

        if (requestedSeconds > Integer.MAX_VALUE) {
            return failure(
                    source,
                    "That timer adjustment is too large."
            );
        }

        int seconds =
                (int) requestedSeconds;

        boolean changed =
                rewind
                        ? DungeonTimer.rewind(
                        source.getServer(),
                        seconds
                )
                        : DungeonTimer.advance(
                        source.getServer(),
                        seconds
                );

        if (!changed) {
            return failure(
                    source,
                    "The dungeon timer could not be adjusted."
            );
        }

        if (!DungeonTimer.isActive()) {
            return success(
                    source,
                    "Dungeon timer advanced to zero and ended."
            );
        }

        return success(
                source,
                "Dungeon timer "
                        + (
                        rewind
                                ? "rewound"
                                : "advanced"
                )
                        + " by "
                        + formatTime(seconds)
                        + ". Remaining: "
                        + formatTime(
                        DungeonTimer.getSecondsRemaining()
                )
                        + "."
        );
    }

    private static int end(
            CommandSourceStack source
    ) {
        if (!DungeonTimer.isActive()) {
            return failure(
                    source,
                    "There is no active dungeon timer."
            );
        }

        DungeonTimer.end(
                source.getServer()
        );

        return success(
                source,
                "Dungeon timer ended."
        );
    }

    private static int success(
            CommandSourceStack source,
            String message
    ) {
        source.sendSuccess(
                () -> Component.literal(message),
                false
        );

        return 1;
    }

    private static int failure(
            CommandSourceStack source,
            String message
    ) {
        source.sendFailure(
                Component.literal(message)
        );

        return 0;
    }

    private static String formatTime(
            int totalSeconds
    ) {
        int minutes =
                totalSeconds / 60;

        int seconds =
                totalSeconds % 60;

        if (minutes <= 0) {
            return seconds + "s";
        }

        return String.format(
                "%dm %02ds",
                minutes,
                seconds
        );
    }
}