package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleConfusionController {
    private static final double RANDOM_MOVE_MIN_DISTANCE = 4.0D;
    private static final double RANDOM_MOVE_DISTANCE_SPAN = 6.0D;
    private static final double CONFUSED_DASH_SPEED = 0.85D;
    private static final int CONFUSED_DASH_TICKS = 12;
    private static final Map<UUID, DashState> DASHES = new HashMap<>();

    private ActionBattleConfusionController() {}

    public static ActionBattleStatusApplication apply(ActionBattleSession session, PokemonEntity target, long currentTick) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L) return null;
        UUID pokemonUUID = target.getPokemon().getUuid();
        var interception = ActionBattleProtectController.global().interceptTimedEffect(session.battleId(), pokemonUUID, currentTick, "confusion", (int) ActionBattleConfusionRules.DURATION_TICKS);
        if (!interception.allowed()) return null;
        ActionBattleStatusApplication result = ActionBattleEffectController.global().applyConfusion(session.battleId(), pokemonUUID, currentTick);
        if (result != null && target.level() instanceof ServerLevel level) ActionBattleStatusParticleController.emitConfusionBurst(level, target);
        return result;
    }

    public static boolean isConfused(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        return session != null && pokemonUUID != null && ActionBattleEffectController.global().hasStatus(session.battleId(), pokemonUUID, ActionBattleStatus.CONFUSION, currentTick);
    }

    public static CommandPlan roll(ActionBattleSession session, PokemonEntity pokemon, ActionBattleConfusionRules.CommandKind kind, long currentTick) {
        if (session == null || pokemon == null || kind == null || !isConfused(session, pokemon.getPokemon().getUuid(), currentTick)) return CommandPlan.NORMAL;
        if (!ActionBattleConfusionRules.shouldCorrupt(kind, pokemon.getRandom().nextFloat())) return CommandPlan.NORMAL;
        long channelBonus = kind == ActionBattleConfusionRules.CommandKind.CHANNEL
                ? ActionBattleConfusionRules.channelBonusTicks(pokemon.getRandom().nextInt(7)) : 0L;
        boolean selfCancel = kind == ActionBattleConfusionRules.CommandKind.CHANNEL
                && ActionBattleConfusionRules.shouldSelfCancelChannel(pokemon.getRandom().nextFloat());
        DebugLog.log("[CobblemonNML] Confusion corrupted command. Battle=" + session.battleId() + ", pokemon=" + pokemon.getPokemon().getUuid()
                + ", kind=" + kind + ", penaltyTicks=" + ActionBattleConfusionRules.COOLDOWN_PENALTY_TICKS
                + (kind == ActionBattleConfusionRules.CommandKind.CHANNEL ? ", channelBonusTicks=" + channelBonus + ", selfCancel=" + selfCancel : ""));
        return new CommandPlan(true, channelBonus, selfCancel);
    }

    public static boolean applyCooldownPenalty(ActionBattleSession session, PokemonEntity pokemon, long currentTick) {
        return session != null && pokemon != null && ActionBattleCommandController.addCooldownPenalty(session, pokemon.getPokemon().getUuid(), currentTick, ActionBattleConfusionRules.COOLDOWN_PENALTY_TICKS);
    }

    public static Vec3 randomMoveTarget(PokemonEntity pokemon) {
        double angle = pokemon.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = RANDOM_MOVE_MIN_DISTANCE + pokemon.getRandom().nextDouble() * RANDOM_MOVE_DISTANCE_SPAN;
        return pokemon.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    public static Vec3 randomShotDirection(PokemonEntity pokemon) {
        double yaw = pokemon.getRandom().nextDouble() * Math.PI * 2.0D;
        double y = (pokemon.getRandom().nextDouble() - 0.5D) * 0.7D;
        Vec3 direction = new Vec3(Math.cos(yaw), y, Math.sin(yaw));
        return direction.lengthSqr() > 0.000001D ? direction.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
    }

    public static boolean startMeleeDash(ActionBattleSession session, ServerLevel level, PokemonEntity attacker, Move move, long currentTick) {
        if (session == null || level == null || attacker == null || move == null) return false;
        UUID pokemonUUID = attacker.getPokemon().getUuid();
        Vec3 direction = randomShotDirection(attacker);
        direction = new Vec3(direction.x, 0.0D, direction.z).normalize();
        attacker.getNavigation().stop();
        attacker.setDeltaMovement(direction.scale(CONFUSED_DASH_SPEED));
        DASHES.put(pokemonUUID, new DashState(session.battleId(), pokemonUUID, move, currentTick + CONFUSED_DASH_TICKS));
        return true;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        DASHES.entrySet().removeIf(entry -> {
            DashState state = entry.getValue();
            if (!session.battleId().equals(state.battleId())) return false;
            PokemonEntity attacker = activeEntity(session, level, state.pokemonUUID());
            if (attacker == null || attacker.isRemoved() || level.getGameTime() >= state.expiresAtTick()) {
                if (attacker != null) attacker.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            if (attacker.horizontalCollision) {
                damageSelf(attacker, state.move());
                attacker.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            AABB hitBox = attacker.getBoundingBox().inflate(0.20D);
            for (Entity raw : level.getEntities(attacker, hitBox, e -> e instanceof LivingEntity && e.isAlive())) {
                if (!(raw instanceof LivingEntity hit) || raw.getUUID().equals(attacker.getUUID())) continue;
                damageCollision(attacker, hit, state.move());
                attacker.setDeltaMovement(Vec3.ZERO);
                return true;
            }
            Vec3 current = attacker.getDeltaMovement();
            if (current.horizontalDistanceSqr() < 0.05D) return true;
            return false;
        });
    }

    public static void clearBattle(UUID battleId) { if (battleId != null) DASHES.entrySet().removeIf(e -> battleId.equals(e.getValue().battleId())); }

    private static void damageCollision(PokemonEntity attacker, LivingEntity target, Move move) {
        float targetDamage = Math.max(1.0F, FightOrFlightAdapter.scaleActionDamage(attacker, target, move, PokemonAttackEffect.calculatePokemonDamage(attacker, target, move)));
        target.hurt(attacker.damageSources().mobAttack(attacker), targetDamage);
        damageSelf(attacker, move);
    }

    private static void damageSelf(PokemonEntity attacker, Move move) {
        float selfDamage = Math.max(1.0F, FightOrFlightAdapter.scaleActionDamage(attacker, attacker, move, PokemonAttackEffect.calculatePokemonDamage(attacker, attacker, move)));
        attacker.hurt(attacker.damageSources().magic(), selfDamage);
    }

    private static PokemonEntity activeEntity(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        UUID entityUUID = pokemonUUID.equals(session.playerActivePokemonUUID()) ? session.playerActiveEntityUUID()
                : pokemonUUID.equals(session.trainerActivePokemonUUID()) ? session.trainerActiveEntityUUID() : null;
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemon ? pokemon : null;
    }

    public record CommandPlan(boolean corrupted, long channelBonusTicks, boolean channelSelfCancel) {
        public static final CommandPlan NORMAL = new CommandPlan(false, 0L, false);
    }
    private record DashState(UUID battleId, UUID pokemonUUID, Move move, long expiresAtTick) {}
}
