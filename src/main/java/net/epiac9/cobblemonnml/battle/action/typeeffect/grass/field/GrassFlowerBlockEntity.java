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

public final class GrassFlowerBlockEntity extends BlockEntity {
    private GrassFlowerLifecycle lifecycle;
    public GrassFlowerBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.GRASS_FLOWER.get(), pos, state); }
    public void initialize(UUID sessionId, UUID owner, ActionBattleFieldObject.OwnerSide side, long bloomTick,
                           long originalCreationTick, long sequence) {
        lifecycle = new GrassFlowerLifecycle(sessionId, owner, side, bloomTick, originalCreationTick, sequence); setChanged();
    }
    public GrassFlowerLifecycle lifecycle() { return lifecycle; }
    public void serverTick() {
        if (level == null || level.isClientSide || lifecycle == null) return;
        if (!lifecycle.activeAt(level.getGameTime())) ActionBattleGrassController.removeFlower(this);
    }
    public void onPokemonTouch(PokemonEntity pokemon) { ActionBattleGrassController.touchFlower(this, pokemon); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); if (lifecycle == null) return;
        tag.putUUID("Session", lifecycle.sessionId()); tag.putUUID("OwnerPokemon", lifecycle.ownerPokemonUUID());
        tag.putString("OwnerSide", lifecycle.ownerSide().name()); tag.putLong("BloomTick", lifecycle.bloomTick());
        tag.putLong("OriginalCreationTick", lifecycle.originalCreationTick()); tag.putLong("CreationSequence", lifecycle.creationSequence());
        tag.putBoolean("Consumed", lifecycle.consumed());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); if (!tag.hasUUID("Session") || !tag.hasUUID("OwnerPokemon")) return;
        try {
            lifecycle = new GrassFlowerLifecycle(tag.getUUID("Session"), tag.getUUID("OwnerPokemon"),
                    ActionBattleFieldObject.OwnerSide.valueOf(tag.getString("OwnerSide")), tag.getLong("BloomTick"),
                    tag.getLong("OriginalCreationTick"), tag.getLong("CreationSequence"));
            if (tag.getBoolean("Consumed")) lifecycle.consumeFirst();
        } catch (IllegalArgumentException ignored) { lifecycle = null; }
    }
}
