package net.epiac9.cobblemonnml.mixin;

import com.cobblemon.mod.common.net.messages.server.SendOutPokemonPacket;
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePokemonControlGuard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SendOutPokemonHandler.class, remap = false)
public abstract class ActionBattleSendOutPokemonHandlerMixin {
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void cobblemonnml$blockNativePartyControl(SendOutPokemonPacket packet, MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        if (!ActionBattlePokemonControlGuard.blockNativePartyControl(player, packet.getSlot())) return;
        ci.cancel();
    }
}
