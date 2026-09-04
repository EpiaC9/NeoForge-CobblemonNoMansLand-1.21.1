package net.epiac9.cobblemonnml.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface ActionBattleLivingEntityAccessor {
    @Accessor("dead")
    void cobblemonNml$setDead(boolean dead);
}
