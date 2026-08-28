package net.epiac9.cobblemonnml.events.trainer;

import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;
import com.mojang.brigadier.CommandDispatcher;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class DungeonTrainerBattleCommand {
    private static final double MAX_TRAINER_DISTANCE = 8.0D;

    private DungeonTrainerBattleCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cobblemonnml")
                        .then(
                                Commands.literal("dungeonbattle")
                                        .executes(context -> startDungeonBattle(context.getSource()))
                        )
        );
    }

    private static int startDungeonBattle(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        Entity trainerEntity = findNearestDungeonTrainer(player);
        if (trainerEntity == null) {
            source.sendFailure(Component.literal("No dungeon trainer found nearby."));
            return 0;
        }

        if (!(trainerEntity instanceof LivingEntity livingTrainerEntity)) {
            source.sendFailure(Component.literal("Dungeon trainer is not a living entity."));
            return 0;
        }

        String runtimeTrainerId = DungeonTrainerTracker.getRCTTrainerId(trainerEntity.getUUID());
        if (runtimeTrainerId == null || runtimeTrainerId.isBlank()) {
            source.sendFailure(Component.literal("That dungeon trainer has no battle team attached."));
            return 0;
        }

        ResourceLocation preset = DungeonTrainerTracker.getPreset(trainerEntity.getUUID());
        String battleFormat = DungeonTrainerBattleFormats.getBattleFormat(player.serverLevel(), preset);

        DebugLog.log("[CobblemonNML] Dungeon battle requested.");
        DebugLog.log("[CobblemonNML] Player UUID: " + player.getUUID());
        DebugLog.log("[CobblemonNML] NPC UUID: " + trainerEntity.getUUID());
        DebugLog.log("[CobblemonNML] Runtime trainer ID: " + runtimeTrainerId);
        DebugLog.log("[CobblemonNML] Trainer preset: " + preset);
        DebugLog.log("[CobblemonNML] Battle format: " + battleFormat);

        TrainerRegistry trainerRegistry;
        try {
            trainerRegistry = TBCS.getInstance().getTrainerRegistry();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Could not access the TBCS trainer registry."));
            exception.printStackTrace();
            return 0;
        }

        TrainerNPC runtimeTrainer;
        try {
            runtimeTrainer = trainerRegistry.getById(runtimeTrainerId, TrainerNPC.class);
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Could not find runtime trainer: " + runtimeTrainerId));
            exception.printStackTrace();
            return 0;
        }

        if (runtimeTrainer == null) {
            source.sendFailure(Component.literal("Runtime trainer does not exist: " + runtimeTrainerId));
            return 0;
        }

        runtimeTrainer.setEntity(livingTrainerEntity);

        String trainerIdFromEntity;
        try {
            trainerIdFromEntity = trainerRegistry.getId(livingTrainerEntity);
        } catch (Exception exception) {
            trainerIdFromEntity = null;
        }

        DebugLog.log("[CobblemonNML] Trainer ID resolved from entity: " + trainerIdFromEntity);

        String command = "tbcs battle " + battleFormat + " @s vs " + runtimeTrainerId;
        DebugLog.log("[CobblemonNML] Executing TBCS command: " + command);

        try {
            Objects.requireNonNull(player.getServer())
                    .getCommands()
                    .performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command);
            return 1;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Failed to start dungeon trainer battle."));
            exception.printStackTrace();
            return 0;
        }
    }

    private static Entity findNearestDungeonTrainer(ServerPlayer player) {
        Entity nearestTrainer = null;
        double nearestDistanceSquared = MAX_TRAINER_DISTANCE * MAX_TRAINER_DISTANCE;

        for (UUID trainerUUID : DungeonTrainerTracker.getTrackedTrainers()) {
            Entity trainer = player.serverLevel().getEntity(trainerUUID);
            if (trainer == null || !trainer.isAlive()) {
                continue;
            }

            double distanceSquared = player.distanceToSqr(trainer);
            if (distanceSquared > nearestDistanceSquared) {
                continue;
            }

            nearestTrainer = trainer;
            nearestDistanceSquared = distanceSquared;
        }

        return nearestTrainer;
    }
}
