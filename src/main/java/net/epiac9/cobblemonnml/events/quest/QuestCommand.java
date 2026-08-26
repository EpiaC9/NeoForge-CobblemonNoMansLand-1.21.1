package net.epiac9.cobblemonnml.events.quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "cobblemonnml")
public final class QuestCommand {
    private QuestCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cobblemonnml")
                        .then(
                                Commands.literal("quest")
                                        .then(
                                                Commands.literal("start")
                                                        .then(
                                                                Commands.argument("quest_id", ResourceLocationArgument.id())
                                                                        .executes(context -> start(
                                                                                context.getSource(),
                                                                                ResourceLocationArgument.getId(context, "quest_id")
                                                                        ))
                                                        )
                                        )
                                        .then(
                                                Commands.literal("progress")
                                                        .then(
                                                                Commands.argument("quest_id", ResourceLocationArgument.id())
                                                                        .then(
                                                                                Commands.argument("objective_id", StringArgumentType.word())
                                                                                        .then(
                                                                                                Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                                                        .executes(context -> progress(
                                                                                                                context.getSource(),
                                                                                                                ResourceLocationArgument.getId(context, "quest_id"),
                                                                                                                StringArgumentType.getString(context, "objective_id"),
                                                                                                                IntegerArgumentType.getInteger(context, "amount")
                                                                                                        ))
                                                                                        )
                                                                        )
                                                        )
                                        )
                                        .then(
                                                Commands.literal("end")
                                                        .then(
                                                                Commands.argument("quest_id", ResourceLocationArgument.id())
                                                                        .executes(context -> end(
                                                                                context.getSource(),
                                                                                ResourceLocationArgument.getId(context, "quest_id")
                                                                        ))
                                                        )
                                        )
                                        .then(
                                                Commands.literal("cancel")
                                                        .then(
                                                                Commands.argument("quest_id", ResourceLocationArgument.id())
                                                                        .executes(context -> cancel(
                                                                                context.getSource(),
                                                                                ResourceLocationArgument.getId(context, "quest_id")
                                                                        ))
                                                        )
                                        )
                        )
        );
    }

    private static int start(CommandSourceStack source, ResourceLocation questId) {
        return respond(source, run(source, questId, Action.START, null, 0));
    }

    private static int progress(
            CommandSourceStack source,
            ResourceLocation questId,
            String objectiveId,
            int amount
    ) {
        return respond(source, run(source, questId, Action.PROGRESS, objectiveId, amount));
    }

    private static int end(CommandSourceStack source, ResourceLocation questId) {
        return respond(source, run(source, questId, Action.END, null, 0));
    }

    private static int cancel(CommandSourceStack source, ResourceLocation questId) {
        return respond(source, run(source, questId, Action.CANCEL, null, 0));
    }

    private static QuestRuntimeManager.Result run(
            CommandSourceStack source,
            ResourceLocation questId,
            Action action,
            String objectiveId,
            int amount
    ) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            return new QuestRuntimeManager.Result(false, "This command must be executed by a player.");
        }

        if (questId == null) {
            return new QuestRuntimeManager.Result(false, "Invalid quest id.");
        }

        return switch (action) {
            case START -> QuestRuntimeManager.start(player, questId);
            case PROGRESS -> QuestRuntimeManager.progress(player, questId, objectiveId, amount);
            case END -> QuestRuntimeManager.end(player, questId);
            case CANCEL -> QuestRuntimeManager.fail(player, questId);
        };
    }

    private static int respond(CommandSourceStack source, QuestRuntimeManager.Result result) {
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        try {
            source.getPlayerOrException().sendSystemMessage(Component.literal(result.message()));
        } catch (Exception ignored) {
        }
        return 1;
    }

    private enum Action {
        START,
        PROGRESS,
        END,
        CANCEL
    }
}
