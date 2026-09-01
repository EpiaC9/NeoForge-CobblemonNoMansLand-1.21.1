package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotDamage;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleProtectVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
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
        if (refs != null) {
            PokemonEntity playerEntity = refs.playerPokemon() != null ? refs.playerPokemon().getEntity() : null;
            PokemonEntity trainerEntity = refs.trainerPokemon() != null ? refs.trainerPokemon().getEntity() : null;
            if (playerEntity != null && !playerEntity.isRemoved() && playerEntity.level() == level) ActionBattleSleepController.tickPokemon(session, playerEntity, currentTick);
            if (trainerEntity != null && !trainerEntity.isRemoved() && trainerEntity.level() == level) ActionBattleSleepController.tickPokemon(session, trainerEntity, currentTick);
        }
        syncHazeBattleZone(session, level, currentTick);
        observeDamageFeedback(session, refs);
        List<ActionBattleDotEvent> effectTicks = ActionBattleEffectController.global().tickBattle(session.battleId(), currentTick);
        applyEffectTicks(session, level, effectTicks);
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
        ActionBattleDamageFeedbackController.global().clearBattle(battleId);
        ActionBattleParalysisController.clearBattle(battleId);
    }

    private static void applyEffectTicks(ActionBattleSession session, ServerLevel level, List<ActionBattleDotEvent> events) {
        if (session == null || level == null || events == null || events.isEmpty()) return;
        for (ActionBattleDotEvent event : events) {
            Pokemon pokemon = findBattlePokemon(session, level, event.pokemonUUID());
            if (pokemon == null || pokemon.isFainted()) continue;
            int maxHealth = Math.max(1, pokemon.getMaxHealth());
            int beforeHealth = pokemon.getCurrentHealth();
            int damage = ActionBattleDotDamage.calculate(maxHealth, pokemon.getCurrentHealth(), event.maxHealthFraction());
            int newHealth = Math.max(event.canKo() ? 0 : 1, beforeHealth - damage);
            pokemon.setCurrentHealth(newHealth);
            boolean poisonDot = isPoison(event.status());
            ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), pokemon.getUuid(), beforeHealth, newHealth,
                    poisonDot ? ActionBattleDamageFeedbackCategory.POISON_DOT : ActionBattleDamageFeedbackCategory.DOT);
            PokemonEntity entity = pokemon.getEntity();
            if (entity != null && !entity.isRemoved() && entity.level() == level) {
                if (event.status() == ActionBattleStatus.BURN) ActionBattleStatusParticleController.emitBurnDotBurst(level, entity);
                else if (event.status() == ActionBattleStatus.FROSTBITE) ActionBattleStatusParticleController.emitFrostbiteDotBurst(level, entity);
                else if (poisonDot) ActionBattleStatusParticleController.emitPoisonDotBurst(level, entity, toxicLevel(event.status()));
            }
            DebugLog.log("[CobblemonNML] Action battle DOT tick. Battle=" + session.battleId() + ", status=" + event.status()
                    + ", pokemon=" + event.pokemonUUID() + ", damage=" + damage + ", hp=" + newHealth + "/" + maxHealth);
        }
    }

    private static Pokemon findBattlePokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (player != null) {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (int slot = 0; slot < party.size(); slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
            }
        }
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (rawTrainer instanceof LivingEntity trainerEntity) {
            TrainerNPC trainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainerEntity);
            if (trainer != null) {
                for (Pokemon pokemon : trainer.getTeam()) {
                    if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
                }
            }
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
        boolean inside = hazeActive && raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()
                && session.battleZone().contains(pokemonEntity.getX(), pokemonEntity.getZ());
        ActionBattleEffectController.global().setHazeProtected(session.battleId(), pokemonUUID, inside, currentTick);
    }

    private static void observeDamageFeedback(ActionBattleSession session, ActionBattlePokemonRefs refs) {
        if (session == null || refs == null) return;
        ActionBattleDamageFeedbackController feedback = ActionBattleDamageFeedbackController.global();
        if (refs.playerPokemon() != null) feedback.observePokemon(session.battleId(), refs.playerPokemon().getUuid(), refs.playerPokemon().getCurrentHealth());
        if (refs.trainerPokemon() != null) feedback.observePokemon(session.battleId(), refs.trainerPokemon().getUuid(), refs.trainerPokemon().getCurrentHealth());
    }

    private static boolean isPoison(ActionBattleStatus status) {
        return status == ActionBattleStatus.POISON || status == ActionBattleStatus.TOXIC_1
                || status == ActionBattleStatus.TOXIC_2 || status == ActionBattleStatus.TOXIC_3;
    }

    private static int toxicLevel(ActionBattleStatus status) {
        return switch (status) {
            case TOXIC_1 -> 1;
            case TOXIC_2 -> 2;
            case TOXIC_3 -> 3;
            default -> 0;
        };
    }
}
