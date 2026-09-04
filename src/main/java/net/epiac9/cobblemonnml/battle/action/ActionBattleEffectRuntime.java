package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotDamage;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentEvent;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentTick;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleProtectVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ActionBattleEffectRuntime {
    private ActionBattleEffectRuntime() {}

    static void tickBattle(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs) {
        if (session == null || level == null) return;
        ActionBattleHailHandler.tickBattle(session, level);
        ActionBattleToxicSpikesHandler.tickBattle(session, level);

        Set<UUID> activeProtectPokemon = new HashSet<>();
        if (session.playerActivePokemonUUID() != null) activeProtectPokemon.add(session.playerActivePokemonUUID());
        if (session.trainerActivePokemonUUID() != null) activeProtectPokemon.add(session.trainerActivePokemonUUID());
        ActionBattleProtectController.global().tickBattle(session.battleId(), activeProtectPokemon);

        long currentTick = level.getGameTime();
        ActionBattleControlController.global().tickBattle(session.battleId(), currentTick);
        if (refs != null) {
            trackRuntimeState(session, level, refs.playerPokemon(), currentTick);
            trackRuntimeState(session, level, refs.trainerPokemon(), currentTick);
        }
        syncHazeBattleZone(session, level, currentTick);
        observeDamageFeedback(session, refs);
        List<ActionBattleDotEvent> effectTicks = ActionBattleEffectController.global().tickBattle(session.battleId(), currentTick);
        applyEffectTicks(session, level, effectTicks);
        List<ActionBattlePersistentTick> persistentTicks = ActionBattlePersistentController.global().tickBattle(session.battleId(), currentTick);
        applyPersistentTicks(session, level, persistentTicks, currentTick);
        if (refs != null) {
            ActionBattleStatusParticleController.tickBattle(session, level, refs.playerPokemon(), refs.trainerPokemon());
            ActionBattleProtectVisuals.tickBattle(session, level, refs.playerPokemon(), refs.trainerPokemon());
        }
        observeDamageFeedback(session, refs);
    }

    static void seedDamageFeedback(ActionBattleSession session, Pokemon pokemon) {
        if (session == null || pokemon == null) return;
        ActionBattleDamageFeedbackController.global().seedPokemon(session.battleId(), pokemon.getUuid(), pokemon.getCurrentHealth());
    }

    static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        ActionBattleHailHandler.clearBattle(battleId);
        ActionBattleToxicSpikesHandler.clearBattle(battleId);
        ActionBattleProtectController.global().clearBattle(battleId);
        ActionBattleEffectController.global().clearBattle(battleId);
        ActionBattlePersistentController.global().clearBattle(battleId);
        ActionBattleControlController.global().clearBattle(battleId);
        ActionBattleDamageFeedbackController.global().clearBattle(battleId);
        ActionBattleEvasionController.clearBattle(battleId);
    }

    private static void applyEffectTicks(ActionBattleSession session, ServerLevel level, List<ActionBattleDotEvent> events) {
        if (session == null || level == null || events == null || events.isEmpty()) return;
        for (ActionBattleDotEvent event : events) applyEffectTick(session, level, event);
    }

    private static void applyEffectTick(ActionBattleSession session, ServerLevel level, ActionBattleDotEvent event) {
        Pokemon pokemon = findBattlePokemon(session, level, event.pokemonUUID());
        if (pokemon == null || pokemon.isFainted()) return;
        int maxHealth = Math.max(1, pokemon.getMaxHealth());
        int beforeHealth = pokemon.getCurrentHealth();
        int damage = ActionBattleDotDamage.calculate(maxHealth, pokemon.getCurrentHealth(), event.maxHealthFraction());
        int newHealth = Math.max(event.canKo() ? 0 : 1, beforeHealth - damage);
        pokemon.setCurrentHealth(newHealth);
        ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), pokemon.getUuid(), beforeHealth, newHealth,
                ActionBattleDamageFeedbackCategory.DOT);
        DebugLog.log("[CobblemonNML] Action battle DOT tick. Battle=" + session.battleId() + ", status=" + event.status()
                + ", pokemon=" + event.pokemonUUID() + ", damage=" + damage + ", hp=" + newHealth + "/" + maxHealth);
    }

    private static void trackRuntimeState(ActionBattleSession session, ServerLevel level, Pokemon pokemon, long currentTick) {
        if (session == null || level == null || pokemon == null) return;
        PokemonEntity entity = pokemon.getEntity();
        if (entity == null || entity.isRemoved() || entity.level() != level) return;
        ActionBattleEvasionController.record(session, entity, currentTick);
        ActionBattleSleepController.tickPokemon(session, entity, currentTick);
        syncNightmareWithSleep(session, entity, currentTick);
    }

    private static void syncNightmareWithSleep(ActionBattleSession session, PokemonEntity entity, long currentTick) {
        if (session == null || entity == null) return;
        UUID pokemonUUID = entity.getPokemon().getUuid();
        ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
        if (persistent.has(session.battleId(), pokemonUUID, ActionBattlePersistentType.NIGHTMARE, currentTick)
                && !ActionBattleSleepController.isSleeping(session, pokemonUUID, currentTick)) {
            persistent.onSleepEnded(session.battleId(), pokemonUUID);
        }
    }

    private static void applyPersistentTicks(ActionBattleSession session, ServerLevel level, List<ActionBattlePersistentTick> ticks, long currentTick) {
        if (session == null || level == null || ticks == null || ticks.isEmpty()) return;
        for (ActionBattlePersistentTick tick : ticks) applyPersistentTick(session, level, tick, currentTick);
    }

    private static void applyPersistentTick(ActionBattleSession session, ServerLevel level, ActionBattlePersistentTick tick, long currentTick) {
        if (tick == null || tick.event() == null) return;
        ActionBattlePersistentEvent event = tick.event();
        if (event.kind() == ActionBattlePersistentEvent.Kind.ENDED) return;
        Pokemon target = findBattlePokemon(session, level, tick.targetPokemonUUID());
        if (target == null || target.isFainted()) return;
        if (event.kind() == ActionBattlePersistentEvent.Kind.FAINT) {
            int before = target.getCurrentHealth();
            target.setCurrentHealth(0);
            ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), target.getUuid(), before, 0, ActionBattleDamageFeedbackCategory.DOT);
            DebugLog.log("[CobblemonNML] Action battle Perish countdown reached zero. Battle=" + session.battleId() + ", pokemon=" + target.getUuid());
            return;
        }
        int maxHealth = Math.max(1, target.getMaxHealth());
        int before = target.getCurrentHealth();
        int damage = Math.max(1, (int) Math.floor(maxHealth * event.maxHealthFraction()));
        int after = Math.max(0, before - damage);
        target.setCurrentHealth(after);
        int actualDamage = Math.max(0, before - after);
        ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), target.getUuid(), before, after, ActionBattleDamageFeedbackCategory.DOT);
        DebugLog.log("[CobblemonNML] Action battle persistent tick. Battle=" + session.battleId() + ", effect=" + event.type()
                + ", pokemon=" + target.getUuid() + ", damage=" + actualDamage + ", hp=" + after + "/" + maxHealth);
    }

    private static Pokemon findBattlePokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        Pokemon playerPokemon = findPlayerPokemon(session, level, pokemonUUID);
        return playerPokemon != null ? playerPokemon : findTrainerPokemon(session, level, pokemonUUID);
    }

    private static Pokemon findPlayerPokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (player == null) return null;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
        }
        return null;
    }

    private static Pokemon findTrainerPokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity)) return null;
        TrainerNPC trainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainerEntity);
        if (trainer == null) return null;
        for (Pokemon pokemon : trainer.getTeam()) {
            if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
        }
        return null;
    }

    private static void syncHazeBattleZone(ActionBattleSession session, ServerLevel level, long currentTick) {
        long remaining = session.hazeRemainingTicks(currentTick);
        boolean active = session.isHazeActive(currentTick) && remaining > 0L;
        syncPokemonHaze(session, level, session.playerActivePokemonUUID(), session.playerActiveEntityUUID(), active, currentTick);
        syncPokemonHaze(session, level, session.trainerActivePokemonUUID(), session.trainerActiveEntityUUID(), active, currentTick);
    }

    private static void syncPokemonHaze(ActionBattleSession session, ServerLevel level, UUID pokemonUUID, UUID entityUUID, boolean hazeActive, long currentTick) {
        if (pokemonUUID == null) return;
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        boolean inside = isInsideHazeZone(session, raw, hazeActive);
        ActionBattleEffectController.global().setHazeProtected(session.battleId(), pokemonUUID, inside, currentTick);
        if (inside && DungeonSession.isActive()) {
            ActionBattleTypeEffectController.global().suppressFireBonusByHaze(DungeonSession.getSessionId(), pokemonUUID);
            ActionBattleTypeEffectController.global().suppressIceDefenseByHaze(DungeonSession.getSessionId(), pokemonUUID);
            ActionBattleTypeEffectController.global().suppressFairySpecialDefenseByHaze(DungeonSession.getSessionId(), pokemonUUID);
        }
    }

    private static boolean isInsideHazeZone(ActionBattleSession session, Entity entity, boolean hazeActive) {
        return hazeActive && entity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()
                && session.battleZone().contains(pokemonEntity.getX(), pokemonEntity.getZ());
    }

    private static void observeDamageFeedback(ActionBattleSession session, ActionBattlePokemonRefs refs) {
        if (session == null || refs == null) return;
        ActionBattleDamageFeedbackController feedback = ActionBattleDamageFeedbackController.global();
        if (refs.playerPokemon() != null) feedback.observePokemon(session.battleId(), refs.playerPokemon().getUuid(), refs.playerPokemon().getCurrentHealth());
        if (refs.trainerPokemon() != null) feedback.observePokemon(session.battleId(), refs.trainerPokemon().getUuid(), refs.trainerPokemon().getCurrentHealth());
    }

}
