package net.epiac9.cobblemonnml.battle.action.move;

import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;

import java.util.List;
import java.util.Map;
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
        double critRatio,
        ActionBattleCanonicalMoveMetadata canonicalMetadata
) {
    public ActionBattleMoveDescriptor {
        id = id != null ? id : "";
        exposedType = exposedType != null ? exposedType : "normal";
        effectiveType = effectiveType != null ? effectiveType : exposedType;
        damageCategory = damageCategory != null ? damageCategory : DamageCategory.STATUS;
        targetCategory = targetCategory != null ? targetCategory : "";
        targetingMode = targetingMode != null ? targetingMode : TargetingMode.TARGET;
        deliveryType = deliveryType != null ? deliveryType : ActionMoveDeliveryType.NORMAL_PROJECTILE;
        canonicalMetadata = canonicalMetadata != null ? canonicalMetadata : ActionBattleCanonicalMoveMetadata.empty();
        flags = flags != null && !flags.isEmpty() ? Set.copyOf(flags) : canonicalMetadata.flags();
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

    public String status() { return canonicalMetadata.status(); }
    public String volatileStatus() { return canonicalMetadata.volatileStatus(); }
    public Map<String, Integer> boosts() { return canonicalMetadata.boosts(); }
    public Map<String, Integer> selfBoosts() { return canonicalMetadata.selfBoosts(); }
    public List<ActionBattleCanonicalMoveMetadata.SecondaryEffect> secondaries() { return canonicalMetadata.secondaries(); }
    public ActionBattleCanonicalMoveMetadata.Fraction recoil() { return canonicalMetadata.recoil(); }
    public ActionBattleCanonicalMoveMetadata.Fraction drain() { return canonicalMetadata.drain(); }
    public ActionBattleCanonicalMoveMetadata.Fraction heal() { return canonicalMetadata.heal(); }
    public ActionBattleCanonicalMoveMetadata.MultiHit multiHit() { return canonicalMetadata.multiHit(); }
    public boolean multiAccuracy() { return canonicalMetadata.multiAccuracy(); }
    public boolean forceSwitch() { return canonicalMetadata.forceSwitch(); }
    public boolean selfSwitch() { return canonicalMetadata.selfSwitch(); }
    public String selfSwitchMode() { return canonicalMetadata.selfSwitchMode(); }
    public boolean ohko() { return canonicalMetadata.ohko(); }
    public boolean guaranteedCrit() { return canonicalMetadata.guaranteedCrit(); }
    public boolean breaksProtect() { return canonicalMetadata.breaksProtect(); }
    public boolean stallingMove() { return canonicalMetadata.stallingMove(); }
    public boolean chargeMove() { return canonicalMetadata.chargeMove(); }
    public boolean rechargeMove() { return canonicalMetadata.rechargeMove(); }
    public String weather() { return canonicalMetadata.weather(); }
    public String terrain() { return canonicalMetadata.terrain(); }
    public String sideCondition() { return canonicalMetadata.sideCondition(); }
    public String slotCondition() { return canonicalMetadata.slotCondition(); }
    public String pseudoWeather() { return canonicalMetadata.pseudoWeather(); }

    public ProtectInteraction protectInteraction() {
        if (breaksProtect()) return ProtectInteraction.BYPASS;
        return ActionBattleMoveMetadataRules.protectInteraction(flags);
    }

    public enum ProtectInteraction { NORMAL, BLOCKED, BYPASS }

    public enum DamageCategory { PHYSICAL, SPECIAL, STATUS }
    public enum TargetingMode { TARGET, SELF_OR_ALLY }
}
