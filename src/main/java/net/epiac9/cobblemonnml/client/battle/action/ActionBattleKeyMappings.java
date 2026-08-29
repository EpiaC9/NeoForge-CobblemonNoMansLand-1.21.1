package net.epiac9.cobblemonnml.client.battle.action;

import com.mojang.blaze3d.platform.InputConstants;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleKeyMappings {
    public static final KeyMapping MOVE_HERE = new KeyMapping(
            "key.cobblemonnml.action_battle.move_here",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.cobblemonnml.action_battle"
    );


    public static final KeyMapping MOVE_1 = createMoveKey("move_1", GLFW.GLFW_KEY_Z);
    public static final KeyMapping MOVE_2 = createMoveKey("move_2", GLFW.GLFW_KEY_X);
    public static final KeyMapping MOVE_3 = createMoveKey("move_3", GLFW.GLFW_KEY_C);
    public static final KeyMapping MOVE_4 = createMoveKey("move_4", GLFW.GLFW_KEY_B);
    public static final KeyMapping SWAP_OUT = createMoveKey("swap_out", GLFW.GLFW_KEY_G);

    private ActionBattleKeyMappings() {}

    private static KeyMapping createMoveKey(String name, int key) {
        return new KeyMapping(
                "key.cobblemonnml.action_battle." + name,
                KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM,
                key,
                "key.categories.cobblemonnml.action_battle"
        );
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MOVE_HERE);
        event.register(MOVE_1);
        event.register(MOVE_2);
        event.register(MOVE_3);
        event.register(MOVE_4);
        event.register(SWAP_OUT);
    }
}
