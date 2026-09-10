package net.epiac9.cobblemonnml.battle.action.audit;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class ActionBattleMoveAuditCommand {
    private ActionBattleMoveAuditCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cobblemonnml")
                .then(Commands.literal("moveaudit")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> run(context.getSource()))));
    }

    private static int run(CommandSourceStack source) {
        try {
            ActionBattleMoveAuditExporter.AuditResult result = ActionBattleMoveAuditExporter.export(FMLPaths.GAMEDIR.get());
            source.sendSuccess(() -> Component.literal("CobblemonNML move audit exported " + result.moveCount()
                    + " moves via " + result.discoverySource() + " to " + result.output().toAbsolutePath()), false);
            return result.moveCount() > 0 ? 1 : 0;
        } catch (Exception exception) {
            source.sendFailure(Component.literal("CobblemonNML move audit failed: " + exception.getMessage()));
            exception.printStackTrace();
            return 0;
        }
    }
}
