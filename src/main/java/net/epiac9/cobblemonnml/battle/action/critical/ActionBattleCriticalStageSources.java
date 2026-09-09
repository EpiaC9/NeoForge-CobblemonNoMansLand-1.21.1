package net.epiac9.cobblemonnml.battle.action.critical;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ActionBattleCriticalStageSources {
    private ActionBattleCriticalStageSources() {}

    public static int stage(PokemonEntity attacker, Move move) {
        double moveRatio = move != null && move.getTemplate() != null
                ? move.getTemplate().getCritRatio() : 0.0D;
        return combine(moveRatio, holdsCriticalStageItem(attacker));
    }

    public static int combine(double moveCritRatio, boolean heldItemBonus) {
        int moveStage = Double.isFinite(moveCritRatio) && moveCritRatio > 0.0D
                ? (int) Math.floor(moveCritRatio) : 0;
        return Math.max(0, moveStage) + (heldItemBonus ? 1 : 0);
    }

    private static boolean holdsCriticalStageItem(PokemonEntity attacker) {
        if (attacker == null || attacker.getPokemon() == null) return false;
        ItemStack held = attacker.getPokemon().heldItem();
        if (held == null || held.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        return id != null && "cobblemon".equals(id.getNamespace())
                && ("scope_lens".equals(id.getPath()) || "razor_claw".equals(id.getPath()));
    }
}
