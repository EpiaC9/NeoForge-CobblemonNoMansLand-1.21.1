package net.epiac9.cobblemonnml.events.trainer;

import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;

import com.mojang.brigadier.CommandDispatcher;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
    // REGISTER COMMAND
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("cobblemonnml")
                        .then(
                                Commands.literal("dungeonbattle")
                                        .executes( context -> startDungeonBattle( context.getSource() ) )
                        )
        );
    }
    // START DUNGEON TRAINER BATTLE
    private static int startDungeonBattle(CommandSourceStack source) {
        // GET EXECUTING PLAYER
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure( Component.literal( "This command must be executed by a player." ) );
            return 0;
        }
        // FIND NEAREST TRACKED DUNGEON TRAINER
        Entity trainerEntity = findNearestDungeonTrainer( player );
        if (trainerEntity == null) {
            source.sendFailure( Component.literal( "No dungeon trainer found nearby." ) );
            return 0;
        }
        // EASY NPC MUST BE A LIVING ENTITY
        if (!(trainerEntity instanceof LivingEntity livingTrainerEntity)) {
            source.sendFailure( Component.literal( "Dungeon trainer is not a living entity." ) );
            return 0;
        }
        // GET RUNTIME TRAINER ID
        String runtimeTrainerId =
                DungeonTrainerTracker
                        .getRCTTrainerId( trainerEntity.getUUID() );
        if (runtimeTrainerId == null || runtimeTrainerId.isBlank()) {
            source.sendFailure( Component.literal( "That dungeon trainer has no battle team attached." ) );
            return 0;
        }
        DebugLog.log( "[CobblemonNML] Dungeon battle requested." );
        DebugLog.log( "[CobblemonNML] Player UUID: " + player.getUUID());
        DebugLog.log( "[CobblemonNML] NPC UUID: " + trainerEntity.getUUID());
        DebugLog.log( "[CobblemonNML] Runtime trainer ID: " + runtimeTrainerId );
        // GET TBCS TRAINER REGISTRY
        TrainerRegistry trainerRegistry;
        try {
            trainerRegistry =
                    TBCS.getInstance()
                            .getTrainerRegistry();
        } catch (Exception exception) {
            source.sendFailure( Component.literal( "Could not access the TBCS trainer registry." ) );
            exception.printStackTrace();
            return 0;
        }
        // GET EXACT RUNTIME TRAINER
        TrainerNPC runtimeTrainer;
        try {
            runtimeTrainer = trainerRegistry.getById( runtimeTrainerId, TrainerNPC.class );
        } catch (Exception exception) {
            source.sendFailure( Component.literal( "Could not find runtime trainer: " + runtimeTrainerId ) );
            exception.printStackTrace();
            return 0;
        }
        if (runtimeTrainer == null) {
            source.sendFailure( Component.literal( "Runtime trainer does not exist: " + runtimeTrainerId ) );
            return 0;
        }
        // REATTACH TRAINER TO EASY NPC ENTITY
        /*
         * This is important.
         * TBCS/RCT requires TrainerNPC to have an entity attached before it can participate in a battle.
         * We explicitly restore that attachment immediately before* starting the battle.
         */
        runtimeTrainer.setEntity( livingTrainerEntity );
        // VERIFY ENTITY LOOKUP
        String trainerIdFromEntity;
        try {
            trainerIdFromEntity = trainerRegistry.getId( livingTrainerEntity );
        } catch (Exception exception) {
            trainerIdFromEntity = null;
        }
        DebugLog.log( "[CobblemonNML] Trainer ID resolved from entity: " + trainerIdFromEntity );
        // BUILD TBCS COMMAND
        /*
         * IMPORTANT:
         * Use the runtime trainer ID here rather than the NPC UUID.
         * This guarantees that TBCS uses the randomly selected dungeon trainer/team registered when the NPC spawned.
         */
        String command =
                "tbcs battle GEN_9_SINGLES @s vs "
                        + runtimeTrainerId;
        DebugLog.log( "[CobblemonNML] Executing TBCS command: " + command );
        // EXECUTE TBCS COMMAND
        try {
            Objects.requireNonNull(player.getServer())
                    .getCommands()
                    .performPrefixedCommand( player.createCommandSourceStack() .withPermission(4), command );
            return 1;
        } catch (Exception exception) {
            source.sendFailure( Component.literal( "Failed to start dungeon trainer battle." ) );
            exception.printStackTrace();
            return 0;
        }
    }
    // FIND NEAREST TRACKED TRAINER
    private static Entity findNearestDungeonTrainer(ServerPlayer player) {
        Entity nearestTrainer = null;
        double nearestDistanceSquared =
                MAX_TRAINER_DISTANCE
                        * MAX_TRAINER_DISTANCE;
        for (UUID trainerUUID : DungeonTrainerTracker .getTrackedTrainers()) {
            Entity trainer =
                    player.serverLevel()
                            .getEntity( trainerUUID );
            if (trainer == null || !trainer.isAlive()) {
                continue;
            }
            double distanceSquared = player.distanceToSqr( trainer );
            if (distanceSquared > nearestDistanceSquared) {
                continue;
            }
            nearestTrainer = trainer;
            nearestDistanceSquared = distanceSquared;
        }
        return nearestTrainer;
    }
}
