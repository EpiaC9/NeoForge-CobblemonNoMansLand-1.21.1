package net.epiac9.cobblemonnml.client.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleFlinchVisualPayload;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleFlinchVisualType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class ActionBattleFlinchVisualClientState {
    private static final int NORMAL_DURATION_TICKS = 8;
    private static final int PARALYSIS_DURATION_TICKS = 10;
    private static final Map<Integer, ActiveVisual> ACTIVE = new HashMap<>();

    private ActionBattleFlinchVisualClientState() {}

    public static void trigger(ActionBattleFlinchVisualPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload == null || client.level == null) return;
        Entity raw = client.level.getEntity(payload.entityId());
        if (!(raw instanceof PokemonEntity entity) || entity.isRemoved()) return;
        ActionBattleFlinchVisualType type;
        try { type = ActionBattleFlinchVisualType.valueOf(payload.visualType()); }
        catch (IllegalArgumentException exception) { type = ActionBattleFlinchVisualType.NORMAL; }
        ActiveVisual previous = ACTIVE.remove(payload.entityId());
        if (previous != null) previous.restore(entity);
        ActiveVisual visual = new ActiveVisual(type, client.level.getGameTime());
        ACTIVE.put(payload.entityId(), visual);
        if (type == ActionBattleFlinchVisualType.NORMAL) entity.animateHurt(0.0F);
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            ACTIVE.clear();
            return;
        }
        long currentTick = client.level.getGameTime();
        Iterator<Map.Entry<Integer, ActiveVisual>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveVisual> entry = iterator.next();
            Entity raw = client.level.getEntity(entry.getKey());
            if (!(raw instanceof PokemonEntity entity) || entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            ActiveVisual visual = entry.getValue();
            if (!visual.apply(entity, currentTick)) {
                visual.restore(entity);
                iterator.remove();
            }
        }
    }

    private static final class ActiveVisual {
        private final ActionBattleFlinchVisualType type;
        private final long startTick;
        private float lastYawOffset;
        private float lastPitchOffset;

        private ActiveVisual(ActionBattleFlinchVisualType type, long startTick) {
            this.type = type;
            this.startTick = startTick;
        }

        private boolean apply(PokemonEntity entity, long currentTick) {
            int duration = type == ActionBattleFlinchVisualType.PARALYSIS ? PARALYSIS_DURATION_TICKS : NORMAL_DURATION_TICKS;
            long ageTicks = Math.max(0L, currentTick - startTick);
            if (ageTicks >= duration) return false;
            float progress = ageTicks / (float) duration;
            float envelope = (float) Math.sin(Math.PI * progress);
            float yawOffset;
            float pitchOffset;
            if (type == ActionBattleFlinchVisualType.PARALYSIS) {
                float jitter = (float) Math.sin(progress * Math.PI * 8.0D);
                yawOffset = 8.0F * jitter * envelope;
                pitchOffset = 5.0F * (float) Math.cos(progress * Math.PI * 10.0D) * envelope;
            } else {
                yawOffset = 3.0F * (float) Math.sin(progress * Math.PI * 2.0D) * envelope;
                pitchOffset = -10.0F * envelope;
            }
            float baseYaw = entity.getYRot() - lastYawOffset;
            float basePitch = entity.getXRot() - lastPitchOffset;
            entity.setYRot(baseYaw + yawOffset);
            entity.setYHeadRot(baseYaw + yawOffset);
            entity.setXRot(basePitch + pitchOffset);
            lastYawOffset = yawOffset;
            lastPitchOffset = pitchOffset;
            return true;
        }

        private void restore(PokemonEntity entity) {
            entity.setYRot(entity.getYRot() - lastYawOffset);
            entity.setYHeadRot(entity.getYRot());
            entity.setXRot(entity.getXRot() - lastPitchOffset);
            lastYawOffset = 0.0F;
            lastPitchOffset = 0.0F;
        }
    }
}
