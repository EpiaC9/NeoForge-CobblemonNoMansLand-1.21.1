package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleEvasionController {
    public static final long TARGET_DELAY_TICKS = 20L;
    private static final ActionBattleTargetHistory HISTORY = new ActionBattleTargetHistory(40);
    private static final Map<UUID, Set<UUID>> ENTITIES_BY_BATTLE = new HashMap<>();

    private ActionBattleEvasionController() {}

    public static ActionBattleStatusApplication apply(ActionBattleSession session, PokemonEntity target, long currentTick) {
        if (session == null || target == null || currentTick < 0L) return null;
        ActionBattleStatusApplication result = ActionBattleEffectController.global().applyEvasion(session.battleId(), target.getPokemon().getUuid(), currentTick);
        if (result == ActionBattleStatusApplication.EVASION_APPLIED && target.level() instanceof net.minecraft.server.level.ServerLevel level) ActionBattleStatusParticleController.emitEvasionBurst(level, target);
        return result;
    }

    static void record(ActionBattleSession session, PokemonEntity entity, long currentTick) {
        if (session == null || entity == null || entity.isRemoved() || currentTick < 0L) return;
        HISTORY.record(entity.getUUID(), currentTick, entity.getX(), entity.getY(), entity.getZ());
        ENTITIES_BY_BATTLE.computeIfAbsent(session.battleId(), ignored -> new HashSet<>()).add(entity.getUUID());
    }

    public static boolean isEvading(PokemonEntity target, long currentTick) {
        if (target == null || currentTick < 0L) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        return session != null && ActionBattleEffectController.global().hasStatus(session.battleId(), target.getPokemon().getUuid(), ActionBattleStatus.EVASION, currentTick);
    }

    public static Vec3 trackedPosition(PokemonEntity target, long currentTick) {
        if (target == null) return Vec3.ZERO;
        if (!isEvading(target, currentTick)) return target.position();
        ActionBattleTargetHistory.Position delayed = HISTORY.positionAtOrBefore(target.getUUID(), Math.max(0L, currentTick - TARGET_DELAY_TICKS));
        return delayed != null ? new Vec3(delayed.x(), delayed.y(), delayed.z()) : target.position();
    }

    public static Vec3 trackedEyePosition(PokemonEntity target, long currentTick) {
        Vec3 base = trackedPosition(target, currentTick);
        return base.add(0.0D, target.getEyeHeight(), 0.0D);
    }

    static void clearEntity(UUID entityUUID) { HISTORY.clear(entityUUID); }
    static void clearBattle(UUID battleId) {
        Set<UUID> entities = battleId != null ? ENTITIES_BY_BATTLE.remove(battleId) : null;
        if (entities != null) for (UUID entityUUID : entities) HISTORY.clear(entityUUID);
    }
    static void clearAll() { HISTORY.clearAll(); ENTITIES_BY_BATTLE.clear(); }
}
