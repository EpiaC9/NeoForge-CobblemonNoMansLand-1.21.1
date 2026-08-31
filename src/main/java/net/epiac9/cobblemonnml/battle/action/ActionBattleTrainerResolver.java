package net.epiac9.cobblemonnml.battle.action;

import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.world.entity.LivingEntity;

final class ActionBattleTrainerResolver {
    private ActionBattleTrainerResolver() {}

    static TrainerNPC resolve(String runtimeTrainerId, LivingEntity trainerEntity) {
        if (runtimeTrainerId == null || runtimeTrainerId.isBlank() || trainerEntity == null) return null;
        try {
            TrainerRegistry registry = TBCS.getInstance().getTrainerRegistry();
            TrainerNPC runtimeTrainer = registry.getById(runtimeTrainerId, TrainerNPC.class);
            if (runtimeTrainer == null) {
                DebugLog.log("[CobblemonNML] Runtime action trainer does not exist: " + runtimeTrainerId);
                return null;
            }
            runtimeTrainer.setEntity(trainerEntity);
            return runtimeTrainer;
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Could not resolve runtime trainer for action battle: " + runtimeTrainerId, exception);
            return null;
        }
    }
}
