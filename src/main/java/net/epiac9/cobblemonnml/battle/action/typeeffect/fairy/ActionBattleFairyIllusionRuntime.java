package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleFairyIllusionRuntime {
    private static final Map<Key, BlockPos> ACTIVE = new HashMap<>();
    private ActionBattleFairyIllusionRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.battleId() == null || context.pokemon() == null
                || !(context.pokemon().level() instanceof ServerLevel level)) return;
        PokemonEntity copied = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.TARGET
                && context.target() instanceof PokemonEntity target ? target : context.pokemon();
        Key key = new Key(context.battleId(), context.pokemon().getPokemon().getUuid());
        BlockPos old = ACTIVE.remove(key);
        if (old != null) level.removeBlock(old, false);
        BlockPos pos = BlockPos.containing(copied.position().add(1.5D, 0.0D, 0.0D));
        if (!level.getBlockState(pos).isAir()
                || !level.setBlock(pos, ModBlocks.ACTION_BATTLE_FAIRY_ILLUSION.get().defaultBlockState(), 3)
                || !(level.getBlockEntity(pos) instanceof ActionBattleFairyIllusionBlockEntity illusion)) return;
        illusion.initialize(context.battleId(), key.sourcePokemonId(), copied);
        ACTIVE.put(key, pos);
    }

    static void move(ActionBattleFairyIllusionBlockEntity illusion, BlockPos desired) {
        if (!(illusion.getLevel() instanceof ServerLevel level) || !level.getBlockState(desired).isAir()) return;
        UUID battleId = illusion.battleId(); UUID source = illusion.sourcePokemonId(); UUID copied = illusion.copiedEntityId();
        var raw = copied != null ? level.getEntity(copied) : null;
        if (!(raw instanceof PokemonEntity copiedPokemon)) return;
        BlockPos old = illusion.getBlockPos();
        level.removeBlock(old, false);
        if (level.setBlock(desired, ModBlocks.ACTION_BATTLE_FAIRY_ILLUSION.get().defaultBlockState(), 3)
                && level.getBlockEntity(desired) instanceof ActionBattleFairyIllusionBlockEntity replacement) {
            replacement.initialize(battleId, source, copiedPokemon);
            ACTIVE.put(new Key(battleId, source), desired);
        }
    }

    static void remove(ActionBattleFairyIllusionBlockEntity illusion) {
        if (illusion.getLevel() == null) return;
        ACTIVE.remove(new Key(illusion.battleId(), illusion.sourcePokemonId()));
        illusion.getLevel().removeBlock(illusion.getBlockPos(), false);
    }

    public static void clearBattle(ServerLevel level, UUID battleId) {
        ACTIVE.entrySet().removeIf(entry -> {
            if (!entry.getKey().battleId().equals(battleId)) return false;
            if (level != null) level.removeBlock(entry.getValue(), false);
            return true;
        });
    }

    public static void clearWorld(ServerLevel level) {
        if (level != null) {
            for (BlockPos pos : ACTIVE.values()) level.removeBlock(pos, false);
        }
        ACTIVE.clear();
    }

    public static void clearAll() { ACTIVE.clear(); }
    private record Key(UUID battleId, UUID sourcePokemonId) {}
}
