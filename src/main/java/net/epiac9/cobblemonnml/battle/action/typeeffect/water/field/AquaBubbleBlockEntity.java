package net.epiac9.cobblemonnml.battle.action.typeeffect.water.field;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class AquaBubbleBlockEntity extends BlockEntity {
    private AquaBubbleLifecycle lifecycle;

    public AquaBubbleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AQUA_BUBBLE.get(), pos, state);
    }

    public void initialize(UUID sessionId, UUID ownerPokemonUUID, ActionBattleFieldObject.OwnerSide ownerSide,
                           long creationTick, long creationSequence) {
        lifecycle = new AquaBubbleLifecycle(sessionId, ownerPokemonUUID, ownerSide, creationTick, creationSequence,
                creationTick + 360L);
        setChanged();
    }

    public AquaBubbleLifecycle lifecycle() { return lifecycle; }

    public void serverTick() {
        if (level == null || level.isClientSide || lifecycle == null) return;
        if (!DungeonSession.isActive() || !lifecycle.sessionId().equals(DungeonSession.getSessionId())
                || !lifecycle.activeAt(level.getGameTime())) ActionBattleWaterController.removeBubble(this);
    }

    public void onPokemonTouch(PokemonEntity pokemon) {
        if (lifecycle == null || !lifecycle.activeAt(level != null ? level.getGameTime() : -1L)) return;
        ActionBattleWaterController.activateBubble(this, pokemon);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (lifecycle == null) return;
        tag.putUUID("Session", lifecycle.sessionId());
        tag.putUUID("OwnerPokemon", lifecycle.ownerPokemonUUID());
        tag.putString("OwnerSide", lifecycle.ownerSide().name());
        tag.putLong("CreationTick", lifecycle.creationTick());
        tag.putLong("CreationSequence", lifecycle.creationSequence());
        tag.putLong("ExpiryTick", lifecycle.expiryTick());
        tag.putBoolean("Consumed", lifecycle.consumed());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.hasUUID("Session") || !tag.hasUUID("OwnerPokemon")) return;
        try {
            lifecycle = new AquaBubbleLifecycle(tag.getUUID("Session"), tag.getUUID("OwnerPokemon"),
                    ActionBattleFieldObject.OwnerSide.valueOf(tag.getString("OwnerSide")),
                    tag.getLong("CreationTick"), tag.getLong("CreationSequence"), tag.getLong("ExpiryTick"));
            if (tag.getBoolean("Consumed")) lifecycle.consumeFirst();
        } catch (IllegalArgumentException ignored) {
            lifecycle = null;
        }
    }
}
