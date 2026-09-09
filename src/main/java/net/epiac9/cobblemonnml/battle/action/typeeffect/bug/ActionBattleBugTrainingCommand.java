package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ActionBattleBugTrainingCommand {
    private ActionBattleBugTrainingCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("cobblemonnml")
                .then(Commands.literal("bugtraining")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("def").executes(context -> apply(
                                context.getSource(), ActionBattleBugTrainingPreset.DEF_ONLY)))
                        .then(Commands.literal("spdef").executes(context -> apply(
                                context.getSource(), ActionBattleBugTrainingPreset.SPDEF_ONLY)))
                        .then(Commands.literal("combined").executes(context -> apply(
                                context.getSource(), ActionBattleBugTrainingPreset.DEF_SPDEF)))));
    }

    private static int apply(CommandSourceStack source, ActionBattleBugTrainingPreset preset) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be used by a player."));
            return 0;
        }
        Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(0);
        if (pokemon == null) {
            source.sendFailure(Component.literal("Party slot 1 is empty."));
            return 0;
        }
        for (Stat stat : Stats.Companion.getPERMANENT()) {
            pokemon.setIV(stat, 0);
            pokemon.setEV(stat, 0);
        }
        pokemon.setEV(Stats.DEFENCE, preset.ev(ActionBattleBugTrainingStat.DEFENSE));
        pokemon.setEV(Stats.SPECIAL_DEFENCE, preset.ev(ActionBattleBugTrainingStat.SPECIAL_DEFENSE));
        source.sendSuccess(() -> Component.literal("Bug training preset " + preset
                + " applied to party slot 1 (all IVs reset to 0)."), false);
        return 1;
    }
}
