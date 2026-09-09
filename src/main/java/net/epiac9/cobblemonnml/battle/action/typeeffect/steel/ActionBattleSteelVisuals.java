package net.epiac9.cobblemonnml.battle.action.typeeffect.steel;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

public final class ActionBattleSteelVisuals {
    public static final String MAGNET_RISE_STATUS_ID = "TYPE_STEEL_MAGNET_RISE";
    public static final String WEIGHTED_STATUS_ID = "TYPE_STEEL_WEIGHTED";
    private static final DustParticleOptions IRON = new DustParticleOptions(new Vector3f(0.67F, 0.72F, 0.76F), 0.8F);

    private ActionBattleSteelVisuals() {}

    public static void tick(ActionBattleSession session, PokemonEntity pokemon, long tick) {
        if (session == null || pokemon == null || !(pokemon.level() instanceof ServerLevel level) || tick % 2L != 0L) return;
        var view = ActionBattleSteelRuntime.view(session.battleId(), pokemon.getPokemon().getUuid(), tick).orElse(null);
        if (view == null) return;
        if (view.branch() == ActionBattleSteelRules.Branch.MAGNET_RISE) emitCrossedRings(level, pokemon, tick);
        else emitPlates(level, pokemon, tick);
    }

    private static void emitCrossedRings(ServerLevel level, PokemonEntity pokemon, long tick) {
        AABB box = pokemon.getBoundingBox();
        double radius = Math.max(0.35D, Math.max(box.getXsize(), box.getZsize()) * 0.65D);
        double centerY = box.minY + box.getYsize() * 0.52D;
        double phase = tick * 0.22D;
        for (int i = 0; i < 4; i++) {
            double angle = phase + i * Math.PI / 2.0D;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = Math.sin(angle) * radius * 0.55D;
            level.sendParticles(IRON, box.getCenter().x + x, centerY + y, box.getCenter().z + z, 1, 0, 0, 0, 0);
            level.sendParticles(IRON, box.getCenter().x + x, centerY - y, box.getCenter().z - z, 1, 0, 0, 0, 0);
        }
    }

    private static void emitPlates(ServerLevel level, PokemonEntity pokemon, long tick) {
        AABB box = pokemon.getBoundingBox();
        BlockParticleOption iron = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState());
        double radius = Math.max(0.24D, Math.max(box.getXsize(), box.getZsize()) * 0.52D);
        double phase = tick * 0.08D;
        for (int i = 0; i < 4; i++) {
            double angle = phase + i * Math.PI / 2.0D;
            level.sendParticles(iron, box.getCenter().x + Math.cos(angle) * radius,
                    box.minY + box.getYsize() * (0.30D + 0.13D * (i & 1)),
                    box.getCenter().z + Math.sin(angle) * radius, 3, 0.06D, 0.04D, 0.06D, 0.01D);
        }
    }
}
