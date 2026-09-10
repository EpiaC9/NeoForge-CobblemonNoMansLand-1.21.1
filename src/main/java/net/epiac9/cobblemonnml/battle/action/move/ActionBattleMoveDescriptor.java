package net.epiac9.cobblemonnml.battle.action.move;

import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;

import java.util.Set;

public record ActionBattleMoveDescriptor(
        String id,
        String exposedType,
        String effectiveType,
        DamageCategory damageCategory,
        int power,
        double accuracy,
        int priority,
        int currentPp,
        int maxPp,
        String targetCategory,
        TargetingMode targetingMode,
        ActionMoveDeliveryType deliveryType,
        Set<String> flags,
        double critRatio
) {
    public ActionBattleMoveDescriptor {
        id = id != null ? id : "";
        exposedType = exposedType != null ? exposedType : "normal";
        effectiveType = effectiveType != null ? effectiveType : exposedType;
        damageCategory = damageCategory != null ? damageCategory : DamageCategory.STATUS;
        targetCategory = targetCategory != null ? targetCategory : "";
        targetingMode = targetingMode != null ? targetingMode : TargetingMode.TARGET;
        deliveryType = deliveryType != null ? deliveryType : ActionMoveDeliveryType.NORMAL_PROJECTILE;
        flags = flags != null ? Set.copyOf(flags) : Set.of();
    }

    public boolean damaging() { return power > 0 && damageCategory != DamageCategory.STATUS; }
    public boolean selfOrAllyTargeted() { return targetingMode == TargetingMode.SELF_OR_ALLY; }
    public boolean targeted() { return targetingMode == TargetingMode.TARGET; }
    public boolean hasFlag(String flag) { return flags.contains(ActionBattleMoveMetadataRules.normalizeToken(flag)); }
    public boolean hasFlag(ActionBattleMoveFlag flag) { return ActionBattleMoveMetadataRules.hasCanonicalFlag(flags, flag); }
    public boolean contact() { return hasFlag(ActionBattleMoveFlag.CONTACT); }
    public boolean sound() { return hasFlag(ActionBattleMoveFlag.SOUND); }
    public boolean powder() { return hasFlag(ActionBattleMoveFlag.POWDER); }
    public boolean punch() { return hasFlag(ActionBattleMoveFlag.PUNCH); }
    public boolean bite() { return hasFlag(ActionBattleMoveFlag.BITE); }
    public boolean slicing() { return hasFlag(ActionBattleMoveFlag.SLICING); }
    public boolean wind() { return hasFlag(ActionBattleMoveFlag.WIND); }
    public boolean ballBomb() { return hasFlag(ActionBattleMoveFlag.BALL_BOMB); }
    public ProtectInteraction protectInteraction() { return ActionBattleMoveMetadataRules.protectInteraction(flags); }

    public enum ProtectInteraction { NORMAL, BLOCKED, BYPASS }

    public enum DamageCategory { PHYSICAL, SPECIAL, STATUS }
    public enum TargetingMode { TARGET, SELF_OR_ALLY }
}
