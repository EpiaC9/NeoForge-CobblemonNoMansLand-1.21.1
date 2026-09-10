package net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field;

import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class PlasmaBallBlockEntity extends BlockEntity {
    private PlasmaBallLifecycle lifecycle;

    public PlasmaBallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_BALL.get(), pos, state);
    }

    public void initialize(PlasmaBallLifecycle lifecycle) { this.lifecycle = lifecycle; setChanged(); }
    public PlasmaBallLifecycle lifecycle() { return lifecycle; }

    public void serverTick() {
        if (level == null || level.isClientSide || lifecycle == null) return;
        if (!DungeonSession.isActive() || !lifecycle.sessionId().equals(DungeonSession.getSessionId())) {
            ActionBattleElectricController.remove(this);
            return;
        }
        ActionBattleElectricController.roam(this);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (lifecycle == null) return;
        tag.putUUID("Session", lifecycle.sessionId());
        tag.putUUID("Battle", lifecycle.battleId());
        tag.putUUID("OwnerPokemon", lifecycle.ownerPokemonId());
        tag.putString("OwnerSide", lifecycle.ownerSide().name());
        tag.putLong("Sequence", lifecycle.sequence());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.hasUUID("Session") || !tag.hasUUID("Battle") || !tag.hasUUID("OwnerPokemon")) return;
        try {
            lifecycle = new PlasmaBallLifecycle(tag.getUUID("Session"), tag.getUUID("Battle"),
                    tag.getUUID("OwnerPokemon"), ActionBattleFieldObject.OwnerSide.valueOf(tag.getString("OwnerSide")),
                    tag.getLong("Sequence"));
        } catch (IllegalArgumentException ignored) { lifecycle = null; }
    }
}
