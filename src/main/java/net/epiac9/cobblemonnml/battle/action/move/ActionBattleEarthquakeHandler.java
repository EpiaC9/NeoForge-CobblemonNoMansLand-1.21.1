package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.PokemonInterface;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleState;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommittedMove;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveParameters;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveServerRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleEarthquakeHandler {
    private ActionBattleEarthquakeHandler() {}

    public static boolean isEarthquakeName(String moveName) {
        return ActionBattleEarthquakeRules.isEarthquakeName(moveName);
    }

    public static boolean isEarthquake(Move move) {
        return move != null && isEarthquakeName(move.getName());
    }

    public static ActionBattleWaveParameters waveParameters() {
        return ActionBattleEarthquakeRules.waveParameters();
    }

    public static boolean canLaunch(PokemonEntity attacker) {
        if (attacker == null || attacker.isRemoved() || !attacker.isAlive()
                || !(attacker.level() instanceof ServerLevel)) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        return session != null && session.state() == ActionBattleState.ACTIVE;
    }

    public static boolean launch(PokemonEntity attacker, Move move, double committedDamageMultiplier) {
        return launch(attacker, move, committedDamageMultiplier, ActionBattleCommittedMove.none());
    }

    public static boolean launch(PokemonEntity attacker, Move move, double committedDamageMultiplier,
                                 ActionBattleCommittedMove committedMove) {
        if (!isEarthquake(move) || !canLaunch(attacker)
                || !(attacker.level() instanceof ServerLevel level)) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        if (session == null) return false;
        ((PokemonInterface) attacker).setCurrentMove(move);
        attacker.setTarget(null);
        PokemonUtils.sendAnimationPacket(attacker, "special");
        ActionBattleWaveServerRuntime.launch(session.dungeonSessionId(), attacker.getPokemon().getUuid(),
                attacker.position(), level.getGameTime(), waveParameters(),
                (waveLevel, origin, target) -> hasClearGroundPath(waveLevel, attacker, origin, target),
                (waveLevel, target) -> FightOrFlightAdapter.resolveRangedNativePokemonHit(
                        attacker, target, move, committedDamageMultiplier, committedMove));
        return true;
    }

    static boolean hasClearGroundPath(ServerLevel level, PokemonEntity attacker,
                                      Vec3 origin, PokemonEntity target) {
        if (level == null || attacker == null || origin == null || target == null
                || target.isRemoved() || !target.isAlive()) return false;
        AABB contactBox = ActionBattleGroundController.effectiveCombatBox(
                target, level.getGameTime(), true);
        if (contactBox == null) return false;
        Vec3 center = contactBox.getCenter();
        Vec3 start = new Vec3(origin.x, origin.y + 0.25D, origin.z);
        Vec3 end = new Vec3(center.x, target.getBoundingBox().minY + 0.25D, center.z);
        BlockHitResult hit = level.clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        return hit.getType() == HitResult.Type.MISS;
    }

}
