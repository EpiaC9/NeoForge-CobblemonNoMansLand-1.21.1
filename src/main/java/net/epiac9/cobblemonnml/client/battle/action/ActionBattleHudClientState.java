package net.epiac9.cobblemonnml.client.battle.action;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;
public final class ActionBattleHudClientState {
    private static ActionBattleHudPayload latest = ActionBattleHudPayload.hidden();
    private static final ActionBattleDamageHudState damage = new ActionBattleDamageHudState();
    private ActionBattleHudClientState() {}
    public static void apply(ActionBattleHudPayload payload) {
        latest = payload != null ? payload : ActionBattleHudPayload.hidden();
        if (!latest.visible()) { damage.clear(); return; }
        long tick = clientTick();
        damage.applySideSnapshot(true, latest.playerPokemonUuid(), latest.playerCurrentHp(), latest.playerMaxHp(), damageInputs(latest.playerDamageEvents()), tick);
        damage.applySideSnapshot(false, latest.trainerPokemonUuid(), latest.trainerCurrentHp(), latest.trainerMaxHp(), damageInputs(latest.trainerDamageEvents()), tick);
    }
    public static ActionBattleHudPayload get() { return latest; }
    public static ActionBattleDamageHudState.RenderSnapshot enemyDamage() { return damage.enemy(clientTick()); }
    public static ActionBattleDamageHudState.RenderSnapshot allyDamage() { return damage.ally(clientTick()); }
    public static boolean isVisible() { return latest.visible(); }
    public static void clear() { latest = ActionBattleHudPayload.hidden(); damage.clear(); }
    private static List<ActionBattleDamageHudState.DamageInput> damageInputs(List<ActionBattleHudPayload.DamageState> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<ActionBattleDamageHudState.DamageInput> inputs = new ArrayList<>(events.size());
        for (ActionBattleHudPayload.DamageState event : events) if (event != null && event.damage() > 0) inputs.add(new ActionBattleDamageHudState.DamageInput(event.eventId(), event.damage(), event.category()));
        return List.copyOf(inputs);
    }
    private static long clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null ? minecraft.player.tickCount : 0L;
    }
}
