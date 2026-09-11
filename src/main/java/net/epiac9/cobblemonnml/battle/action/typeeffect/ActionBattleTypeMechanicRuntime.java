package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectDataManager;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundShockwaveRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingGuardRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleChillingAuraRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonSludgeRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockConstructRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyIllusionRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;

public final class ActionBattleTypeMechanicRuntime {
    private ActionBattleTypeMechanicRuntime() {}

    public static int onActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.pokemon() == null || context.move() == null || !context.committed()) return 0;
        java.util.List<String> identities = ActionBattleTypeMechanicIdentity.getTypeMechanicIdentities(context.pokemon());
        java.util.Set<String> routed = ActionBattleMoveEffectDataManager.typeEffectRoutes(context.move().getName());
        if (!routed.isEmpty()) identities = identities.stream().filter(routed::contains).toList();
        return ActionBattleTypeMechanicDispatcher.dispatch(
                identities, context.mechanicSecondary(), identity -> apply(identity, context));
    }

    private static void apply(String identity, ActionBattleTypeMechanicActionContext context) {
        switch (identity) {
            case "fire" -> ActionBattleFireRuntime.onOwnedActionStarted(context);
            case "fighting" -> ActionBattleFightingGuardRuntime.onOwnedActionStarted(context);
            case "water" -> ActionBattleWaterController.onOwnedActionStarted(context);
            case "grass" -> ActionBattleGrassController.onOwnedActionStarted(context);
            case "electric" -> ActionBattleElectricController.onOwnedActionStarted(context);
            case "ground" -> ActionBattleGroundShockwaveRuntime.onOwnedActionStarted(context);
            case "ice" -> ActionBattleChillingAuraRuntime.onOwnedActionStarted(context);
            case "poison" -> ActionBattlePoisonSludgeRuntime.onOwnedActionStarted(context);
            case "rock" -> ActionBattleRockConstructRuntime.onOwnedActionStarted(context);
            case "fairy" -> ActionBattleFairyIllusionRuntime.onOwnedActionStarted(context);
            case "bug" -> ActionBattleBugRuntime.onOwnedActionStarted(context);
            default -> { }
        }
    }
}
