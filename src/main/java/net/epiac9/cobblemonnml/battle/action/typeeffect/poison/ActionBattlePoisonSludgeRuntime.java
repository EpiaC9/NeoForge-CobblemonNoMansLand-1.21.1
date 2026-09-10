package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattlePoisonSludgeRuntime {
    private static final ActionBattleStat[] STATS = {
            ActionBattleStat.ATTACK, ActionBattleStat.DEFENSE, ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.SPECIAL_DEFENSE, ActionBattleStat.SPEED, ActionBattleStat.ACCURACY
    };
    private static final Map<UUID, List<Puddle>> PUDDLES = new HashMap<>();
    private static Tuning tuning = new Tuning(0L);

    private ActionBattlePoisonSludgeRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.battleId() == null || context.pokemon() == null) return;
        long tick = context.pokemon().level().getGameTime();
        if (context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY) {
            add(context.battleId(), context.pokemon().position(), 1.5D, tick);
        } else if (context.moveCategory() == ActionBattleTypeMechanicActionContext.MoveCategory.MELEE) {
            Vec3 forward = context.pokemon().getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            if (forward.lengthSqr() < 0.0001D) forward = new Vec3(0.0D, 0.0D, 1.0D);
            forward = forward.normalize();
            add(context.battleId(), context.pokemon().position().add(forward.scale(1.5D)), 1.5D, tick);
        }
    }

    public static void addProjectileTrail(PokemonEntity owner, Vec3 position) {
        if (owner == null || position == null) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(owner.getUUID());
        if (session != null) add(session.battleId(), position, 0.75D, owner.level().getGameTime());
    }

    public static void tick(ServerLevel level, UUID battleId, long tick) {
        if (level == null || battleId == null) return;
        List<Puddle> puddles = PUDDLES.get(battleId);
        if (puddles == null) return;
        if (tuning.lifetimeTicks() > 0L) puddles.removeIf(puddle -> tick >= puddle.createdTick() + tuning.lifetimeTicks());
        if (Math.floorMod(tick, 20L) != 0L) return;
        for (Puddle puddle : puddles) level.sendParticles(ParticleTypes.WITCH,
                puddle.center().x, puddle.center().y + 0.1D, puddle.center().z,
                Math.max(3, (int) Math.ceil(puddle.radius() * 4.0D)), puddle.radius(), 0.05D,
                puddle.radius(), 0.0D);
        for (var entity : level.getAllEntities()) {
            if (!(entity instanceof PokemonEntity pokemon) || !pokemon.isAlive() || pokemon.isRemoved()) continue;
            ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID());
            if (session == null || !battleId.equals(session.battleId()) || !inside(puddles, pokemon.position())) continue;
            UUID pokemonId = pokemon.getPokemon().getUuid();
            ActionBattleStat stat = selectStat(pokemonId, tick);
            EnumMap<ActionBattleStat, Integer> change = new EnumMap<>(ActionBattleStat.class);
            change.put(stat, -1);
            ActionBattleStatApplicationService.global().applyBatch(
                    battleId, pokemonId, change, tick, ActionBattleStatSource.POISON_SLUDGE, true);
            ActionBattleEffectController.global().convertPoisonToToxic(battleId, pokemonId, tick);
        }
        if (puddles.isEmpty()) PUDDLES.remove(battleId);
    }

    public static ActionBattleStat selectStat(UUID pokemonId, long tick) {
        long mixed = (pokemonId != null ? pokemonId.getMostSignificantBits() + pokemonId.getLeastSignificantBits() : 0L) ^ tick;
        return STATS[Math.floorMod(Long.hashCode(mixed), STATS.length)];
    }

    public static boolean inside(List<Puddle> puddles, Vec3 position) {
        if (puddles == null || position == null) return false;
        for (Puddle puddle : puddles) {
            double dx = position.x - puddle.center().x;
            double dz = position.z - puddle.center().z;
            if (dx * dx + dz * dz <= puddle.radius() * puddle.radius()
                    && Math.abs(position.y - puddle.center().y) <= 1.5D) return true;
        }
        return false;
    }

    public static void configure(Tuning value) { if (value != null) tuning = value; }
    public static void clearBattle(UUID battleId) { if (battleId != null) PUDDLES.remove(battleId); }
    public static void clearAll() { PUDDLES.clear(); }

    private static void add(UUID battleId, Vec3 center, double radius, long tick) {
        PUDDLES.computeIfAbsent(battleId, ignored -> new ArrayList<>()).add(new Puddle(center, radius, tick));
    }

    public record Tuning(long lifetimeTicks) {
        public Tuning { lifetimeTicks = Math.max(0L, lifetimeTicks); }
    }
    public record Puddle(Vec3 center, double radius, long createdTick) {}
}
