package net.epiac9.cobblemonnml.mixin;

import me.rufia.fightorflight.data.movedata.movedatas.StatChangeMoveData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = StatChangeMoveData.class, remap = false)
public interface ActionBattleStatChangeMoveDataAccessor {
    @Accessor("stage")
    int cobblemonNml$getStage();
}
