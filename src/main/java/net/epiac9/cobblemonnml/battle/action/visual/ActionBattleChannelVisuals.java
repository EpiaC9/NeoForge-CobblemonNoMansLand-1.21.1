package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

public final class ActionBattleChannelVisuals {
    private ActionBattleChannelVisuals() {}

    public static void emitAura(ServerLevel level, PokemonEntity caster, String typeName, float progress) {
        if (level == null || caster == null || caster.isRemoved() || level.getGameTime() % 2L != 0L) return;
        Vector3f color = typeColor(typeName);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        int count = 3 + Math.round(clamped * 5.0F);
        double width = Math.max(0.25D, caster.getBbWidth() * (0.45D + clamped * 0.12D));
        double y = caster.getY() + caster.getBbHeight() * 0.5D;
        level.sendParticles(new DustParticleOptions(color, 0.8F + clamped * 0.35F), caster.getX(), y, caster.getZ(), count, width, caster.getBbHeight() * 0.38D, width, 0.01D);
    }

    public static void emitCancellationBurst(ServerLevel level, PokemonEntity caster, String typeName) {
        if (level == null || caster == null || caster.isRemoved()) return;
        Vector3f color = typeColor(typeName);
        double width = Math.max(0.3D, caster.getBbWidth() * 0.6D);
        level.sendParticles(new DustParticleOptions(color, 1.0F), caster.getX(), caster.getY() + caster.getBbHeight() * 0.5D, caster.getZ(), 18, width, caster.getBbHeight() * 0.42D, width, 0.08D);
    }

    private static Vector3f typeColor(String typeName) {
        String type = typeName == null ? "normal" : typeName.toLowerCase(java.util.Locale.ROOT);
        return switch (type) {
            case "fire" -> rgb(238, 96, 48);
            case "water" -> rgb(72, 144, 224);
            case "electric" -> rgb(248, 208, 48);
            case "grass" -> rgb(96, 184, 72);
            case "ice" -> rgb(120, 208, 224);
            case "fighting" -> rgb(192, 64, 48);
            case "poison" -> rgb(160, 64, 176);
            case "ground" -> rgb(216, 184, 104);
            case "flying" -> rgb(152, 136, 232);
            case "psychic" -> rgb(240, 88, 136);
            case "bug" -> rgb(168, 184, 32);
            case "rock" -> rgb(184, 160, 72);
            case "ghost" -> rgb(112, 88, 152);
            case "dragon" -> rgb(112, 56, 248);
            case "dark" -> rgb(104, 88, 80);
            case "steel" -> rgb(168, 168, 192);
            case "fairy" -> rgb(232, 144, 200);
            default -> rgb(168, 168, 152);
        };
    }

    private static Vector3f rgb(int r, int g, int b) {
        return new Vector3f(r / 255.0F, g / 255.0F, b / 255.0F);
    }
}
