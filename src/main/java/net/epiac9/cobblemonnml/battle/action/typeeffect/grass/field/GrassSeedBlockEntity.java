package net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class GrassSeedBlockEntity extends BlockEntity {
    private GrassSeedLifecycle lifecycle;
    public GrassSeedBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.GRASS_SEED.get(), pos, state); }
    public void initialize(UUID sessionId, UUID owner, ActionBattleFieldObject.OwnerSide side, long tick, long sequence) {
        lifecycle = new GrassSeedLifecycle(sessionId, owner, side, tick, sequence); setChanged();
    }
    public GrassSeedLifecycle lifecycle() { return lifecycle; }
    public void serverTick() {
        if (level == null || level.isClientSide || lifecycle == null) return;
        if (lifecycle.readyToBloom(level.getGameTime())) ActionBattleGrassController.bloom(this);
    }
    public void onPokemonTouch(PokemonEntity pokemon) { ActionBattleGrassController.touchSeed(this, pokemon); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); if (lifecycle == null) return;
        tag.putUUID("Session", lifecycle.sessionId()); tag.putUUID("OwnerPokemon", lifecycle.ownerPokemonUUID());
        tag.putString("OwnerSide", lifecycle.ownerSide().name()); tag.putLong("CreationTick", lifecycle.creationTick());
        tag.putLong("CreationSequence", lifecycle.creationSequence()); tag.putBoolean("Consumed", lifecycle.consumed());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); if (!tag.hasUUID("Session") || !tag.hasUUID("OwnerPokemon")) return;
        try {
            lifecycle = new GrassSeedLifecycle(tag.getUUID("Session"), tag.getUUID("OwnerPokemon"),
                    ActionBattleFieldObject.OwnerSide.valueOf(tag.getString("OwnerSide")),
                    tag.getLong("CreationTick"), tag.getLong("CreationSequence"));
            if (tag.getBoolean("Consumed")) lifecycle.consumeFirst();
        } catch (IllegalArgumentException ignored) { lifecycle = null; }
    }
}
