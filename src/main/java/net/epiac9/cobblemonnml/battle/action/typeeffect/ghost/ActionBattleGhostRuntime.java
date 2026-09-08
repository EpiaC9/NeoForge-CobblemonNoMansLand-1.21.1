package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleHealthResolver;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDotExecutor;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDamageSource;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntConsumer;

public final class ActionBattleGhostRuntime {
    private static final ActionBattleGhostRuntime GLOBAL =
            new ActionBattleGhostRuntime(new ActionBattleGhostController());

    private final ActionBattleGhostController curses;
    private final ActionBattleGhostDamageRules damageRules;
    private final ActionBattleHealthResolver health;
    private final ActionBattleDotExecutor dots;
    private final Map<UUID, ActionBattleGhostCast> armedCasts = new HashMap<>();
    private static final ThreadLocal<Integer> HEALTH_WRITE_DEPTH = ThreadLocal.withInitial(() -> 0);

    public ActionBattleGhostRuntime(ActionBattleGhostController curses) {
        if (curses == null) throw new IllegalArgumentException("Ghost controller cannot be null.");
        this.curses = curses;
        this.damageRules = new ActionBattleGhostDamageRules(curses);
        this.health = new ActionBattleHealthResolver(curses);
        this.dots = new ActionBattleDotExecutor(curses, health);
    }

    public static ActionBattleGhostRuntime global() {
        return GLOBAL;
    }

    public ActionBattleGhostController curses() {
        return curses;
    }

    public ActionBattleGhostDamageRules damageRules() {
        return damageRules;
    }

    public ActionBattleHealthResolver health() { return health; }
    public ActionBattleDotExecutor dots() { return dots; }

    public int onPpConsumed(PokemonEntity pokemonEntity, int ppConsumed) {
        if (pokemonEntity == null || ppConsumed <= 0) return 0;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonEntity.getUUID());
        if (battleId == null) return 0;
        int before = pokemonEntity.getPokemon().getCurrentHealth();
        int actual = health.onPpConsumed(healthAccess(pokemonEntity), battleId,
                pokemonEntity.getPokemon().getUuid(), ppConsumed,
                pokemonEntity.level().getGameTime()).actualChange();
        if (actual > 0) onDamageResolved(pokemonEntity, before);
        if (actual > 0) ActionBattleGhostVisuals.emitEvent(
                pokemonEntity, ActionBattleGhostCurseType.HUNGER);
        return actual;
    }

    public void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        long currentTick = level.getGameTime();
        for (UUID targetId : curses.trackedTargets(session.battleId())) {
            PokemonEntity target = activeEntity(session, level, targetId);
            if (target != null) {
                for (ActionBattleGhostCurseState.View view
                        : curses.views(session.battleId(), targetId, currentTick)) {
                    if (view.type().timed()) ActionBattleGhostVisuals.emitAmbient(
                            target, view.type(), currentTick);
                }
            }
            for (ActionBattleGhostCurseState.TickEvent event
                    : curses.tick(session.battleId(), targetId, currentTick)) {
                if (event.type() == ActionBattleGhostCurseType.DECAY && target != null && target.isAlive()) {
                    applyDot(target, ActionBattleGhostRules.decayDamage(
                            target.getPokemon().getMaxHealth()), "ghost_decay", currentTick);
                    ActionBattleGhostVisuals.emitEvent(target, ActionBattleGhostCurseType.DECAY);
                }
            }
        }
    }

    public int applyDot(PokemonEntity target, int requestedDamage, String sourceId, long currentTick) {
        if (target == null || requestedDamage <= 0) return 0;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null) return 0;
        int before = target.getPokemon().getCurrentHealth();
        var result = dots.execute(healthAccess(target), battleId, target.getPokemon().getUuid(),
                requestedDamage, ActionBattleDamageSource.dot(sourceId), currentTick,
                target.getRandom().nextDouble());
        if (result.extraActualDamage() > 0) {
            ActionBattleGhostVisuals.emitEvent(target, ActionBattleGhostCurseType.MISFORTUNE);
        }
        if (result.baseActualDamage() > 0) transferHaunting(target, result.baseActualDamage());
        if (result.extraActualDamage() > 0) transferHaunting(target, result.extraActualDamage());
        ActionBattleDamageFeedbackController.global().recordDamage(battleId,
                target.getPokemon().getUuid(), before, target.getPokemon().getCurrentHealth(),
                ActionBattleDamageFeedbackCategory.DOT);
        return result.totalActualDamage();
    }

    public void onDamageResolved(PokemonEntity damaged, int beforeHealth) {
        if (damaged == null) return;
        transferHaunting(damaged, Math.max(0, beforeHealth - damaged.getPokemon().getCurrentHealth()));
    }

    private void transferHaunting(PokemonEntity damaged, int actualDamage) {
        if (damaged == null || actualDamage <= 0 || !(damaged.level() instanceof ServerLevel level)) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(damaged.getUUID());
        if (session == null) return;
        int transfer = ActionBattleGhostRules.hauntingDamage(actualDamage);
        for (UUID targetId : curses.hauntedTargets(
                session.battleId(), damaged.getPokemon().getUuid(), level.getGameTime())) {
            PokemonEntity target = activeEntity(session, level, targetId);
            if (target == null || !target.isAlive()) continue;
            int before = target.getPokemon().getCurrentHealth();
            health.damage(healthAccess(target), session.battleId(), targetId, transfer,
                    ActionBattleDamageSource.nonReactiveCurse("haunting"), level.getGameTime());
            ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), targetId,
                    before, target.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.DOT);
            ActionBattleGhostVisuals.emitEvent(target, ActionBattleGhostCurseType.HAUNTING);
        }
    }

    public double prepareDamagingAbility(PokemonEntity caster, Move move) {
        if (caster == null || move == null || !isDamaging(move)) return 1.0D;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(caster.getUUID());
        if (battleId == null) return 1.0D;
        double multiplier = damageRules.prepareDamagingAbility(
                battleId, caster.getPokemon().getUuid(), caster.level().getGameTime(), true);
        if (multiplier < 1.0D) ActionBattleGhostVisuals.emitEvent(
                caster, ActionBattleGhostCurseType.WEAKNESS);
        return multiplier;
    }

    public ActionBattleGhostDamageRules.CooldownPlan abilityCooldownPlan(
            UUID battleId, UUID casterPokemonUUID, int moveSlot, long currentTick) {
        return damageRules.abilityCooldownPlan(battleId, casterPokemonUUID, moveSlot, currentTick);
    }

    public ActionBattleGhostDamageRules.CooldownPlan applyAbilityCooldown(
            ActionBattleSession session, UUID casterPokemonUUID, int moveSlot, long currentTick) {
        if (session == null || casterPokemonUUID == null) {
            return new ActionBattleGhostDamageRules.CooldownPlan(0L, 0L, moveSlot);
        }
        var plan = abilityCooldownPlan(session.battleId(), casterPokemonUUID, moveSlot, currentTick);
        session.startPokemonSharedAbilityCooldown(casterPokemonUUID, currentTick, plan.sharedTicks());
        if (plan.personalTicks() > 0L && moveSlot >= 0) {
            session.startPokemonPersonalMoveCooldown(casterPokemonUUID, moveSlot,
                    currentTick, plan.personalTicks());
        }
        return plan;
    }

    public ActionBattleGhostDamageRules.CooldownPlan applyAbilityCooldown(
            ActionBattleSession session, PokemonEntity caster, int moveSlot, long currentTick) {
        if (caster == null) {
            return new ActionBattleGhostDamageRules.CooldownPlan(0L, 0L, moveSlot);
        }
        ActionBattleDragonRuntime.CooldownPlan uproar = ActionBattleDragonRuntime.cooldownPlan(
                session, caster, moveSlot, currentTick);
        if (uproar.active()) {
            session.clearPokemonSharedAbilityCooldown(caster.getPokemon().getUuid());
            session.startPokemonPersonalMoveCooldown(caster.getPokemon().getUuid(), moveSlot,
                    currentTick, uproar.personalTicks());
            return new ActionBattleGhostDamageRules.CooldownPlan(0L, uproar.personalTicks(), moveSlot);
        }
        long baseSharedTicks = ActionBattleFightingRuntime.sharedCooldownTicks(
                session, caster, moveSlot, currentTick);
        var plan = damageRules.abilityCooldownPlan(session.battleId(), caster.getPokemon().getUuid(),
                moveSlot, currentTick, baseSharedTicks);
        if (ActionBattleFightingRuntime.consumeActivationCooldownSuppression(
                session, caster, moveSlot, currentTick)) {
            emitAbilityCooldownConsumption(caster, plan);
            return new ActionBattleGhostDamageRules.CooldownPlan(0L, 0L, moveSlot);
        }
        session.startPokemonSharedAbilityCooldown(caster.getPokemon().getUuid(), currentTick, plan.sharedTicks());
        if (plan.personalTicks() > 0L && moveSlot >= 0) {
            session.startPokemonPersonalMoveCooldown(caster.getPokemon().getUuid(), moveSlot,
                    currentTick, plan.personalTicks());
        }
        emitAbilityCooldownConsumption(caster, plan);
        return plan;
    }

    public void emitAbilityCooldownConsumption(
            PokemonEntity caster, ActionBattleGhostDamageRules.CooldownPlan plan) {
        if (caster == null || plan == null) return;
        if (plan.personalTicks() > ActionBattleTiming.PERSONAL_MOVE_BASE_COOLDOWN_TICKS) {
            ActionBattleGhostVisuals.emitEvent(caster, ActionBattleGhostCurseType.BURDEN);
        }
        if (plan.sharedTicks() > ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS) {
            ActionBattleGhostVisuals.emitEvent(caster, ActionBattleGhostCurseType.TORMENT);
        }
    }

    public int findMoveSlot(PokemonEntity caster, Move move) {
        if (caster == null || move == null) return -1;
        int slot = 0;
        for (Move candidate : caster.getPokemon().getMoveSet()) {
            if (candidate == move) return slot;
            slot++;
        }
        return -1;
    }

    public double modifyIncomingDirectDamage(PokemonEntity target, double damage) {
        if (target == null || !(damage > 0.0D)) return damage;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null) return damage;
        double modified = damageRules.modifyIncomingDirectDamage(
                battleId, target.getPokemon().getUuid(), target.level().getGameTime(), damage, true);
        if (modified > damage) ActionBattleGhostVisuals.emitEvent(
                target, ActionBattleGhostCurseType.FRAILTY);
        return modified;
    }

    public boolean blocksVoluntarySwap(UUID battleId, UUID pokemonUUID, long currentTick) {
        return curses.view(battleId, pokemonUUID,
                ActionBattleGhostCurseType.BINDING, currentTick).isPresent();
    }

    public static int adjustExternalHealthSet(Pokemon pokemon, int requestedHealth) {
        if (pokemon == null || HEALTH_WRITE_DEPTH.get() > 0) return requestedHealth;
        int current = pokemon.getCurrentHealth();
        if (requestedHealth <= current) return requestedHealth;
        ActionBattleSession session = ActionBattleManager.findSessionForPokemon(pokemon.getUuid());
        PokemonEntity entity = pokemon.getEntity();
        if (session == null || entity == null || entity.isRemoved()) return requestedHealth;
        long tick = entity.level().getGameTime();
        if (global().curses.view(session.battleId(), pokemon.getUuid(),
                ActionBattleGhostCurseType.WITHERING, tick).isEmpty()) return requestedHealth;
        int requestedHealing = requestedHealth - current;
        return Math.min(pokemon.getMaxHealth(), current + (int) Math.ceil(requestedHealing * 0.50D));
    }

    private static void writeHealth(Runnable write) {
        HEALTH_WRITE_DEPTH.set(HEALTH_WRITE_DEPTH.get() + 1);
        try {
            write.run();
        } finally {
            int depth = HEALTH_WRITE_DEPTH.get() - 1;
            if (depth <= 0) HEALTH_WRITE_DEPTH.remove();
            else HEALTH_WRITE_DEPTH.set(depth);
        }
    }

    public Optional<ActionBattleGhostCast> onMoveCompleted(UUID battleId, UUID casterPokemonUUID,
                                                            int maximumHealth, int currentHealth,
                                                            boolean ghostCaster, IntConsumer healthSetter) {
        if (battleId == null || casterPokemonUUID == null || healthSetter == null) return Optional.empty();
        int cost = ActionBattleGhostRules.safeSacrifice(maximumHealth, currentHealth, ghostCaster);
        if (cost <= 0) return Optional.empty();
        healthSetter.accept(currentHealth - cost);
        ActionBattleGhostCast cast = new ActionBattleGhostCast(
                UUID.randomUUID(), battleId, casterPokemonUUID, ghostCaster, true);
        armedCasts.put(cast.castId(), cast);
        return Optional.of(cast);
    }

    public ApplicationResult onConnected(ActionBattleGhostCast cast, UUID targetPokemonUUID,
                                          String primaryType, String secondaryType,
                                          Map<ActionBattleStat, Integer> stages,
                                          int randomIndex, long currentTick) {
        if (cast == null || targetPokemonUUID == null || stages == null || currentTick < 0L
                || !cast.armed() || !cast.equals(armedCasts.remove(cast.castId()))) {
            return ApplicationResult.NOT_ARMED;
        }
        if (ActionBattleGhostRules.isCurseImmune(primaryType, secondaryType)) {
            return new ApplicationResult(ApplicationKind.IMMUNE, null, null, 0);
        }
        if (cast.ghostCaster()) {
            ActionBattleGhostCurseType type = ActionBattleGhostRules.selectSpecialCurse(randomIndex);
            curses.apply(cast.battleId(), cast.casterPokemonUUID(), targetPokemonUUID, type, currentTick);
            return new ApplicationResult(ApplicationKind.SPECIAL_CURSE, type, null, 0);
        }
        Optional<ActionBattleStat> selected = ActionBattleGhostRules.selectEligibleStat(stages, randomIndex);
        if (selected.isEmpty()) {
            return new ApplicationResult(ApplicationKind.INSTANT_KO, null, null, 0);
        }
        return new ApplicationResult(ApplicationKind.STAT_CURSE, null, selected.orElseThrow(), -1);
    }

    public void discard(ActionBattleGhostCast cast) {
        if (cast != null) armedCasts.remove(cast.castId(), cast);
    }

    public void restore(ActionBattleGhostCast cast) {
        if (cast != null && cast.armed()) armedCasts.put(cast.castId(), cast);
    }

    public Optional<ActionBattleGhostCast> completeMove(PokemonEntity caster, Move move) {
        if (caster == null || move == null || move.getType() == null
                || !"ghost".equalsIgnoreCase(move.getType().getName())) return Optional.empty();
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(caster.getUUID());
        if (battleId == null) return Optional.empty();
        Pokemon pokemon = caster.getPokemon();
        return onMoveCompleted(battleId, pokemon.getUuid(), pokemon.getMaxHealth(), pokemon.getCurrentHealth(),
                hasType(pokemon, "ghost"), health -> synchronizeHealth(caster, health));
    }

    public ApplicationResult connect(ActionBattleGhostCast cast, PokemonEntity target) {
        if (cast == null || target == null) return ApplicationResult.NOT_ARMED;
        Pokemon pokemon = target.getPokemon();
        long tick = target.level().getGameTime();
        Map<ActionBattleStat, Integer> stages = new java.util.EnumMap<>(ActionBattleStat.class);
        for (ActionBattleStat stat : ActionBattleStat.values()) {
            stages.put(stat, ActionBattleEffectController.global().effectiveStage(
                    cast.battleId(), pokemon.getUuid(), stat, tick));
        }
        ApplicationResult result = onConnected(cast, pokemon.getUuid(),
                pokemon.getPrimaryType() != null ? pokemon.getPrimaryType().getName() : null,
                pokemon.getSecondaryType() != null ? pokemon.getSecondaryType().getName() : null,
                stages, target.getRandom().nextInt(ActionBattleGhostCurseType.values().length), tick);
        if (result.kind() == ApplicationKind.STAT_CURSE) {
            ActionBattleStatApplicationService.global().applyBatch(cast.battleId(), pokemon.getUuid(),
                    Map.of(result.stat(), result.stages()), tick, ActionBattleStatSource.GHOST_CURSE, true);
            ActionBattleGhostVisuals.emitStatApplication(target);
        } else if (result.kind() == ApplicationKind.INSTANT_KO) {
            synchronizeHealth(target, 0);
            ActionBattleGhostVisuals.emitStatApplication(target);
        } else if (result.kind() == ApplicationKind.SPECIAL_CURSE) {
            ActionBattleGhostVisuals.emitApplication(target, result.curseType());
        }
        return result;
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonUUID) {
        curses.onPokemonUnavailable(battleId, pokemonUUID);
        armedCasts.entrySet().removeIf(entry -> entry.getValue().battleId().equals(battleId)
                && entry.getValue().casterPokemonUUID().equals(pokemonUUID));
    }

    public void clearBattle(UUID battleId) {
        curses.clearBattle(battleId);
        if (battleId != null) armedCasts.entrySet().removeIf(entry -> entry.getValue().battleId().equals(battleId));
    }

    public void clearAll() {
        curses.clearAll();
        armedCasts.clear();
    }

    private static boolean hasType(Pokemon pokemon, String expected) {
        return pokemon != null && (pokemon.getPrimaryType() != null
                && expected.equalsIgnoreCase(pokemon.getPrimaryType().getName())
                || pokemon != null && pokemon.getSecondaryType() != null
                && expected.equalsIgnoreCase(pokemon.getSecondaryType().getName()));
    }

    private static boolean isDamaging(Move move) {
        return net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter.movePower(move) > 0;
    }

    private static void synchronizeHealth(PokemonEntity entity, int health) {
        Pokemon pokemon = entity.getPokemon();
        ActionBattlePokemonHealth.damage(healthAccess(entity),
                Math.max(0, pokemon.getCurrentHealth() - health));
    }

    public static ActionBattlePokemonHealth.Access healthAccess(PokemonEntity entity) {
        Pokemon pokemon = entity.getPokemon();
        return new ActionBattlePokemonHealth.Access() {
            @Override public int currentHealth() { return pokemon.getCurrentHealth(); }
            @Override public int maxHealth() { return pokemon.getMaxHealth(); }
            @Override public boolean deployed() { return !entity.isRemoved(); }
            @Override public float liveMaxHealth() { return entity.getMaxHealth(); }
            @Override public void setCurrentHealth(int value) { writeHealth(() -> pokemon.setCurrentHealth(value)); }
            @Override public void setLiveHealth(float value) { entity.setHealth(value); }
        };
    }

    private static PokemonEntity activeEntity(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        UUID entityUUID = session.isPlayerPokemon(pokemonUUID)
                ? session.playerEntityForPokemon(pokemonUUID)
                : pokemonUUID.equals(session.trainerActivePokemonUUID())
                ? session.trainerActiveEntityUUID() : null;
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemon ? pokemon : null;
    }

    public enum ApplicationKind { NOT_ARMED, IMMUNE, STAT_CURSE, INSTANT_KO, SPECIAL_CURSE }

    public record ApplicationResult(ApplicationKind kind, ActionBattleGhostCurseType curseType,
                                    ActionBattleStat stat, int stages) {
        public static final ApplicationResult NOT_ARMED =
                new ApplicationResult(ApplicationKind.NOT_ARMED, null, null, 0);
    }
}
