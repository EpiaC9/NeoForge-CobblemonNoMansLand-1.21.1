package net.epiac9.cobblemonnml.events.damage;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    private static final ResourceKey<DamageType> LIFE_TRANSFER =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "life_transfer" )
            );
    // LIFE TRANSFER
    public static DamageSource lifeTransfer(ServerLevel level) {
        Holder<DamageType> holder =
                level.registryAccess().registryOrThrow( Registries.DAMAGE_TYPE ).getHolderOrThrow( LIFE_TRANSFER );
        return new DamageSource( holder );
    }
}
