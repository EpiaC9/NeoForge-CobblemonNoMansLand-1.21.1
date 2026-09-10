package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class ActionBattleRockConstructBlockEntity extends BlockEntity {
    private UUID battleId;
    private UUID ownerEntityId;
    private UUID ownerPokemonId;
    private long endTick;
    private boolean selfAction;

    public ActionBattleRockConstructBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACTION_BATTLE_ROCK_CONSTRUCT.get(), pos, state);
    }

    public void initialize(UUID battleId, UUID ownerEntityId, UUID ownerPokemonId, long endTick, boolean selfAction) {
        this.battleId = battleId;
        this.ownerEntityId = ownerEntityId;
        this.ownerPokemonId = ownerPokemonId;
        this.endTick = endTick;
        this.selfAction = selfAction;
        setChanged();
    }

    public void serverTick() {
        if (level == null) return;
        boolean channeling = selfAction && ownerPokemonId != null
                && net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController.isChanneling(ownerPokemonId);
        if (level.getGameTime() >= endTick && !channeling) level.removeBlock(worldPosition, false);
    }

    public UUID battleId() { return battleId; }
    public UUID ownerEntityId() { return ownerEntityId; }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (battleId != null) tag.putUUID("Battle", battleId);
        if (ownerEntityId != null) tag.putUUID("OwnerEntity", ownerEntityId);
        if (ownerPokemonId != null) tag.putUUID("OwnerPokemon", ownerPokemonId);
        tag.putLong("EndTick", endTick);
        tag.putBoolean("SelfAction", selfAction);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        battleId = tag.hasUUID("Battle") ? tag.getUUID("Battle") : null;
        ownerEntityId = tag.hasUUID("OwnerEntity") ? tag.getUUID("OwnerEntity") : null;
        ownerPokemonId = tag.hasUUID("OwnerPokemon") ? tag.getUUID("OwnerPokemon") : null;
        endTick = tag.getLong("EndTick");
        selfAction = tag.getBoolean("SelfAction");
    }
}
