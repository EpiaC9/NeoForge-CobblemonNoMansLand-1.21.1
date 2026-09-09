package net.epiac9.cobblemonnml.battle.action.projectile.wave;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleState;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleWaveServerRuntime {
    private static final List<ActiveWave> WAVES = new ArrayList<>();
    private ActionBattleWaveServerRuntime() {}

    @FunctionalInterface
    public interface WaveHitBehavior {
        void onHit(ServerLevel level, PokemonEntity pokemon);
    }

    @FunctionalInterface
    public interface WaveHitEligibility {
        boolean canHit(ServerLevel level, Vec3 origin, PokemonEntity pokemon);
    }

    public static void launch(UUID sessionId, UUID sourcePokemonId, Vec3 origin, long tick,
                              ActionBattleWaveParameters parameters, WaveHitBehavior behavior) {
        launch(sessionId, sourcePokemonId, origin, tick, parameters,
                (level, waveOrigin, pokemon) -> true, behavior);
    }

    public static void launch(UUID sessionId, UUID sourcePokemonId, Vec3 origin, long tick,
                              ActionBattleWaveParameters parameters, WaveHitEligibility eligibility,
                              WaveHitBehavior behavior) {
        if (sessionId == null || sourcePokemonId == null || origin == null || tick < 0
                || parameters == null || eligibility == null || behavior == null) return;
        WAVES.add(new ActiveWave(new ActionBattleWaveRuntime.Instance(sessionId, sourcePokemonId,
                new ActionBattleWaveRuntime.Point(origin.x, origin.y, origin.z), tick, parameters),
                eligibility, behavior));
    }

    public static void launchHealing(UUID sessionId, UUID sourcePokemonId, Vec3 origin, int healAmount, long tick) {
        if (healAmount <= 0) return;
        launch(sessionId, sourcePokemonId, origin, tick,
                new ActionBattleWaveParameters(ActionProjectileProfile.WAVE_AREA_SPEED, 10.0D),
                (level, pokemon) -> net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController
                        .healPokemon(pokemon.getPokemon(), healAmount));
    }

    public static void tick(ServerLevel level, UUID sessionId) {
        if (level == null || sessionId == null) return;
        long tick = level.getGameTime();
        List<ActiveWave> processing = List.copyOf(WAVES);
        for (ActiveWave active : processing) {
            if (!WAVES.contains(active)) continue;
            if (!sessionId.equals(active.instance.sessionId())) continue;
            double radius = active.instance.radius(tick);
            if (tick % 2L == 0L) emitRing(level, active.origin(), radius);
            AABB area = new AABB(-radius, -radius, -radius, radius, radius, radius);
            List<PokemonEntity> entities = level.getEntitiesOfClass(PokemonEntity.class,
                    area.move(active.origin()));
            Map<UUID, PokemonEntity> entitiesByPokemonId = new HashMap<>();
            List<ActionBattleWaveRuntime.PokemonSample> samples = entities.stream().map(entity -> {
                var battle = ActionBattleManager.findSessionForBattlePokemonEntity(entity.getUUID());
                boolean valid = battle != null && battle.state() == ActionBattleState.ACTIVE
                        && active.instance.sessionId().equals(battle.dungeonSessionId())
                        && !net.epiac9.cobblemonnml.battle.action.ActionBattleSwapTransitionGuard
                        .rejectsHit(entity.getPokemon().getUuid());
                entitiesByPokemonId.put(entity.getPokemon().getUuid(), entity);
                return new ActionBattleWaveRuntime.PokemonSample(entity.getPokemon().getUuid(), active.instance.sessionId(),
                        new ActionBattleWaveRuntime.Point(entity.getX(), entity.getY(), entity.getZ()), valid);
            }).toList();
            for (UUID pokemonId : active.instance.collectNewHits(tick, samples, sample -> {
                PokemonEntity entity = entitiesByPokemonId.get(sample.pokemonId());
                return entity != null && active.eligibility.canHit(level, active.origin(), entity);
            })) {
                PokemonEntity entity = entitiesByPokemonId.get(pokemonId);
                if (entity != null) active.behavior.onHit(level, entity);
            }
            if (active.instance.complete(tick)) WAVES.remove(active);
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

    private record ActiveWave(ActionBattleWaveRuntime.Instance instance, WaveHitEligibility eligibility,
                              WaveHitBehavior behavior) {
        private Vec3 origin() {
            var origin = instance.origin();
            return new Vec3(origin.x(), origin.y(), origin.z());
        }
    }
}
