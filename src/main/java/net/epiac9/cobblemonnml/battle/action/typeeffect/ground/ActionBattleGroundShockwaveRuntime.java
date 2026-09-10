package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleGroundShockwaveRuntime {
    private static final Map<UUID, List<Zone>> ZONES = new HashMap<>();
    private ActionBattleGroundShockwaveRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.arenaSessionId() == null || context.pokemon() == null) return;
        boolean self = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY;
        if (!self && FightOrFlightAdapter.movePower(context.move()) <= 0) return;
        Vec3 center = self || context.target() == null ? context.pokemon().position() : context.target().position();
        ZONES.computeIfAbsent(context.arenaSessionId(), ignored -> new ArrayList<>())
                .add(new Zone(context.battleId(), center));
        if (context.pokemon().level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            level.getBlockState(BlockPos.containing(center).below())),
                    center.x, center.y + 0.1D, center.z, 32,
                    ActionBattleGroundRules.SHOCKWAVE_HALF_WIDTH, 0.05D,
                    ActionBattleGroundRules.SHOCKWAVE_HALF_WIDTH, 0.0D);
        }
    }

    public static void tick(ServerLevel level, UUID sessionId, long tick) {
        if (level == null || sessionId == null || Math.floorMod(tick, 20L) != 0L) return;
        for (Zone zone : ZONES.getOrDefault(sessionId, List.of())) {
            AABB area = new AABB(zone.center(), zone.center()).inflate(
                    ActionBattleGroundRules.SHOCKWAVE_HALF_WIDTH, 1.0D,
                    ActionBattleGroundRules.SHOCKWAVE_HALF_WIDTH);
            for (PokemonEntity pokemon : level.getEntitiesOfClass(PokemonEntity.class, area,
                    candidate -> candidate.isAlive() && !candidate.isRemoved())) {
                ActionBattleSession battle = ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID());
                if (battle == null || zone.battleId() == null || !zone.battleId().equals(battle.battleId())) continue;
                var result = ActionBattleTypeEffectController.global().applyGround(sessionId,
                        pokemon.getPokemon().getUuid(), tick, false);
                int depth = ActionBattleTypeEffectController.global().groundView(sessionId,
                        pokemon.getPokemon().getUuid(), tick).map(ActionBattleGroundState.View::depthPercent).orElse(0);
                ActionBattleGroundVisualSync.update(pokemon, sessionId, depth);
                if (result == ActionBattleGroundState.ApplyResult.FULLY_SUNK || depth >= 100) {
                    pokemon.getPokemon().setCurrentHealth(0);
                    pokemon.setHealth(0.0F);
                }
            }
        }
    }

    public static void clearSession(UUID sessionId) { ZONES.remove(sessionId); }
    public static void clearAll() { ZONES.clear(); }
    private record Zone(UUID battleId, Vec3 center) {}
}
