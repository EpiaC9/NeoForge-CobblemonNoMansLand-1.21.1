package net.epiac9.cobblemonnml.battle.action.projectile.wave;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleState;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class ActionBattleWaveServerRuntime {
    private static final List<ActiveWave> WAVES = new ArrayList<>();
    private ActionBattleWaveServerRuntime() {}

    public static void launch(UUID sessionId, Vec3 origin, int healAmount, long tick) {
        if (sessionId == null || origin == null || healAmount <= 0 || tick < 0) return;
        WAVES.add(new ActiveWave(new ActionBattleWaveRuntime.Instance(sessionId,
                new ActionBattleWaveRuntime.Point(origin.x, origin.y, origin.z), tick,
                new ActionBattleWaveParameters(ActionProjectileProfile.WAVE_AREA_SPEED, 10.0D)), healAmount));
    }

    public static void tick(ServerLevel level, UUID sessionId) {
        if (level == null || sessionId == null) return;
        long tick = level.getGameTime();
        Iterator<ActiveWave> iterator = WAVES.iterator();
        while (iterator.hasNext()) {
            ActiveWave active = iterator.next();
            if (!sessionId.equals(active.instance.sessionId())) continue;
            double radius = active.instance.radius(tick);
            if (tick % 2L == 0L) emitRing(level, active.origin(), radius);
            AABB area = new AABB(-radius, -radius, -radius, radius, radius, radius);
            List<PokemonEntity> entities = level.getEntitiesOfClass(PokemonEntity.class,
                    area.move(active.origin()));
            List<ActionBattleWaveRuntime.PokemonSample> samples = entities.stream().map(entity -> {
                var battle = ActionBattleManager.findSessionForBattlePokemonEntity(entity.getUUID());
                boolean valid = battle != null && battle.state() == ActionBattleState.ACTIVE
                        && active.instance.sessionId().equals(battle.dungeonSessionId());
                return new ActionBattleWaveRuntime.PokemonSample(entity.getPokemon().getUuid(), active.instance.sessionId(),
                        new ActionBattleWaveRuntime.Point(entity.getX(), entity.getY(), entity.getZ()), valid);
            }).toList();
            for (UUID pokemonId : active.instance.collectNewHits(tick, samples)) {
                entities.stream().filter(entity -> pokemonId.equals(entity.getPokemon().getUuid())).findFirst()
                        .ifPresent(entity -> ActionBattleGrassController.healPokemon(entity.getPokemon(), active.healAmount));
            }
            if (active.instance.complete(tick)) iterator.remove();
        }
    }

    public static void clearSession(UUID sessionId) { if (sessionId != null) WAVES.removeIf(wave -> sessionId.equals(wave.instance.sessionId())); }
    public static void clearAll() { WAVES.clear(); }

    private static void emitRing(ServerLevel level, Vec3 origin, double radius) {
        if (radius <= 0.0D) return;
        int points = Math.max(8, (int) Math.ceil(radius * 4.0D));
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0D * index / points;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    origin.x + Math.cos(angle) * radius, origin.y + 0.25D,
                    origin.z + Math.sin(angle) * radius, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private record ActiveWave(ActionBattleWaveRuntime.Instance instance, int healAmount) {
        private Vec3 origin() {
            var origin = instance.origin();
            return new Vec3(origin.x(), origin.y(), origin.z());
        }
    }
}
