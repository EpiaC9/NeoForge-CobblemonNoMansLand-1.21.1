package net.epiac9.cobblemonnml.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonEntity.class)
public abstract class ActionBattlePokemonBrainMixin {
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void cobblemonnml$suppressAutonomousBrainMovement(CallbackInfo ci) {
        PokemonEntity pokemon = (PokemonEntity) (Object) this;
        if (ActionBattleManager.shouldSuppressAutonomousMovement(pokemon)) {
            ci.cancel();
        }
    }
}
