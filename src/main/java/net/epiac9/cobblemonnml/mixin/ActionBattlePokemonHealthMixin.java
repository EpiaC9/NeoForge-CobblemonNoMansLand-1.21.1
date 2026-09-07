package net.epiac9.cobblemonnml.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Pokemon.class, remap = false)
public abstract class ActionBattlePokemonHealthMixin {
    @ModifyVariable(method = "setCurrentHealth", at = @At("HEAD"), argsOnly = true)
    private int cobblemonnml$applyWitheringToHealing(int requestedHealth) {
        return ActionBattleGhostRuntime.adjustExternalHealthSet((Pokemon) (Object) this, requestedHealth);
    }
}
