package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.Callable;

public final class ActionBattlePokemonControlGuard {
    private static final Component RECALL_WARNING = Component.literal("You can't recall at the moment.");
    private static final Component SEND_OUT_WARNING = Component.literal("Use Swap Out during an action battle.");
    private static final ThreadLocal<Integer> INTERNAL_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static boolean registered;

    private ActionBattlePokemonControlGuard() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGH, event -> {
            if (isInternal()) return Unit.INSTANCE;
            Pokemon pokemon = event.getPokemon();
            ServerPlayer owner = pokemon != null ? pokemon.getOwnerPlayer() : null;
            if (owner == null || !ActionBattleManager.hasBattleForPlayer(owner.getUUID())) return Unit.INSTANCE;
            ActionBattleSession session = ActionBattleManager.getByPlayer(owner.getUUID());
            if (session == null || session.state() != ActionBattleState.ACTIVE) return Unit.INSTANCE;
            event.cancel();
            owner.displayClientMessage(SEND_OUT_WARNING, true);
            DebugLog.log("[CobblemonNML] Blocked manual Pokemon send-out during action battle. Battle=" + session.battleId() + ", pokemon=" + pokemon.getUuid());
            return Unit.INSTANCE;
        });
        DebugLog.log("[CobblemonNML] Action battle Pokemon control guard registered.");
    }


    public static boolean blockNativePartyControl(ServerPlayer player, int slot) {
        if (player == null) return false;
        ActionBattleSession session = ActionBattleManager.getByPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        Pokemon pokemon = slot >= 0 ? Cobblemon.INSTANCE.getStorage().getParty(player).get(slot) : null;
        player.displayClientMessage(RECALL_WARNING, true);
        DebugLog.log("[CobblemonNML] Blocked native Cobblemon party control during action battle. Battle=" + session.battleId()
                + ", slot=" + slot + ", pokemon=" + (pokemon != null ? pokemon.getUuid() : "unknown"));
        return true;
    }

    public static void runInternal(Runnable action) {
        if (action == null) return;
        enterInternal();
        try {
            action.run();
        } finally {
            exitInternal();
        }
    }

    public static <T> T callInternal(Callable<T> action) {
        if (action == null) return null;
        enterInternal();
        try {
            return action.call();
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            exitInternal();
        }
    }

    private static boolean isInternal() {
        return INTERNAL_DEPTH.get() > 0;
    }

    private static void enterInternal() {
        INTERNAL_DEPTH.set(INTERNAL_DEPTH.get() + 1);
    }

    private static void exitInternal() {
        int depth = INTERNAL_DEPTH.get() - 1;
        if (depth <= 0) INTERNAL_DEPTH.remove();
        else INTERNAL_DEPTH.set(depth);
    }
}
