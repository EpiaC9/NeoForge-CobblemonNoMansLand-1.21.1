package net.epiac9.cobblemonnml.mixin;

import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.client.battle.action.ActionBattleNativePartyObscurity;
import net.epiac9.cobblemonnml.client.battle.action.ActionBattleNativePartyText;
import net.epiac9.cobblemonnml.client.battle.action.ActionBattleObscurityHudRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PartyOverlay.class, remap = false)
public abstract class ActionBattlePartyOverlayMixin {
    @Unique
    private static final ResourceLocation COBBLEMONNML$ENCHANTMENT_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "alt");

    @Inject(method = "render", at = @At("HEAD"))
    private void cobblemonnml$beginObscurity(GuiGraphics graphics, DeltaTracker deltaTracker,
                                             CallbackInfo ci) {
        int stage = ActionBattleNativePartyObscurity.stage();
        if (ActionBattleObscurityHudRules.grayscale(stage)) {
            RenderSystem.setShaderColor(0.58F, 0.58F, 0.58F, 1.0F);
        }
    }

    @ModifyArgs(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/client/render/RenderHelperKt;drawScaledText$default(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/chat/MutableComponent;Ljava/lang/Number;Ljava/lang/Number;FLjava/lang/Number;IIZZLjava/lang/Integer;Ljava/lang/Integer;ILjava/lang/Object;)V"
            )
    )
    private void cobblemonnml$obscurePartyText(Args args) {
        int stage = ActionBattleNativePartyObscurity.stage();
        net.minecraft.network.chat.MutableComponent text = args.get(2);
        args.set(2, ActionBattleNativePartyText.present(text, stage,
                value -> value.copy().withStyle(style ->
                        style.withFont(COBBLEMONNML$ENCHANTMENT_FONT)),
                Component::empty));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void cobblemonnml$endObscurity(GuiGraphics graphics, DeltaTracker deltaTracker,
                                           CallbackInfo ci) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
