package net.epiac9.cobblemonnml.client.battle.action;

import com.bedrockk.molang.runtime.MoLangRuntime;
import com.cobblemon.mod.common.api.snowstorm.BedrockParticleOptions;
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository;
import com.cobblemon.mod.common.client.particle.ParticleStorm;
import com.cobblemon.mod.common.client.render.MatrixWrapper;
import kotlin.Unit;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ActionBattleProjectileNativeVisuals {
    private static final Map<ActionBattleProjectileEntity, Boolean> STARTED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ActionBattleProjectileNativeVisuals() {}

    public static void tryAttach(ActionBattleProjectileEntity projectile) {
        if (!(projectile.level() instanceof ClientLevel level) || !projectile.isAlive() || STARTED.containsKey(projectile)) return;
        var effectIds = ActionProjectileProfile.nativeCobblemonEffects(projectile.committedMoveName());
        if (effectIds.isEmpty()) return;
        boolean spawnedAny = false;
        for (String effectId : effectIds) spawnedAny |= spawnEffect(level, projectile, effectId);
        if (spawnedAny) STARTED.put(projectile, Boolean.TRUE);
    }

    private static boolean spawnEffect(ClientLevel level, ActionBattleProjectileEntity projectile, String effectId) {
        int separator = effectId.indexOf(':');
        if (separator <= 0 || separator >= effectId.length() - 1) return false;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(effectId.substring(0, separator), effectId.substring(separator + 1));
        BedrockParticleOptions effect = BedrockParticleOptionsRepository.INSTANCE.getEffect(id);
        if (effect == null) return false;

        MatrixWrapper matrix = new MatrixWrapper();
        matrix.setUpdateFunction(wrapper -> {
            wrapper.setPosition(projectile.position());
            return Unit.INSTANCE;
        });
        matrix.updatePosition(projectile.position());

        ParticleStorm storm = new ParticleStorm(
                effect,
                matrix,
                matrix,
                level,
                projectile::getDeltaMovement,
                projectile::isAlive,
                () -> !projectile.isInvisible(),
                null,
                () -> Unit.INSTANCE,
                () -> null,
                new MoLangRuntime(),
                projectile
        );
        storm.spawn();
        return true;
    }
}
