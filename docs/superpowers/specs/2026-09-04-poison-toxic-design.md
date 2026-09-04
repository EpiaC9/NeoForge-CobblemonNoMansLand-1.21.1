# Poison / Toxic Type-Effect Design

## Goal

Implement a fresh dungeon-session-scoped Poison/Toxic mechanic with continuous accumulation, deterministic off-field progression, source-owned Special Attack effects, and active-Protect-only DS interactions. Do not restore the removed legacy implementation or invent deferred DoT values.

## Architecture

Poison is an independent type effect stored beside Fire, Ice, and Drowsy in `ActionBattleTypeEffectState`. A Poison package contains focused rules, active state, persistent tracker, delivery controller, and visual helper. The shared type-effect controller exposes narrow Poison operations and views while leaving Poison decisions inside the Poison package.

`ActionBattlePoisonState` owns one active 1–99 accumulation value, its derived level, the current level or Toxic deadline, passive-accumulation scheduling, receiver typing, and whether Haze has suppressed its owned stat contribution. `ActionBattlePoisonTracker` owns the optional active state, direct-move accumulation gain, and clean-reset deadline so resistance history survives after active Poison disappears.

All delivery paths converge on the Poison controller. Phase 1 automatically submits every successful Poison-type interaction that affects an enemy Pokemon. Damaging hits submit only after final positive HP damage; supported non-damaging moves submit after their enemy-targeting status projectile succeeds. Toxic Spikes remains unchanged and future move metadata or overrides will call the same application API.

## Rules and lifecycle

The central level helper maps 0 to no state, 1–32 to `POISON`, 33–65 to `POISON_LV1`, 66–98 to `POISON_LV2`, and 99+ to `TOXIC`, clamping accumulation to 0–99. There is no Poison Lv3.

A fresh successful application creates Poison at accumulation 1 and starts a 120-tick level timer. It does not add the current direct gain. Later qualifying hits add the tracker's direct gain, initially 9, except that Toxic ignores applications without refreshing its timer. Same-level increases preserve the existing level timer; upward threshold transitions reset it to 120 ticks. Multi-hit delivery submits each successful hit independently.

Every 20 ticks, active pre-Toxic Poison gains 3, 6, or 9 accumulation according to its current level. A threshold crossed by passive or direct gain takes effect immediately and resets the level timer. Toxic has no passive accumulation.

When a 120-tick level timer expires, processing occurs before passive accumulation. The state reads its pre-expiry level, subtracts 5, 8, or 12, clamps to zero, and removes itself immediately at zero. A removed state cannot receive a passive tick on that same game tick. If accumulation remains, its level is recalculated and a fresh 120-tick timer begins even when the resulting level is unchanged or lower.

Reaching 99 immediately enters Toxic, removes normal level/passive scheduling, and starts one fixed 180-tick Toxic deadline. Further Poison hits do not accumulate or refresh. Toxic expiry removes the state.

Natural removal through zero decay or Toxic expiry completes one cycle and reduces later direct gain by one, to a minimum of 1. Cleanup does not count. Removal starts a 360-tick clean window; uninterrupted completion restores direct gain to 9. Reapplication before the deadline uses the penalized gain and cancels the clean reset.

Recall, swapping, battle completion, surrender, and between-battle downtime do not remove or pause Poison. The existing session runtime advances timers and passive progression by Pokemon UUID without requiring an entity. Dungeon exit, logout in the dungeon, full reset, and session replacement clear all Poison state/history through existing type-effect cleanup.

## Typing, stats, and Haze

Steel typing rejects initial and subsequent custom Poison interactions before tracker mutation. Steel/Poison is therefore immune.

Poison typing is not immune to accumulation. Pre-Toxic levels contribute Poison-owned +1 Special Attack and Toxic contributes +2, with no custom Poison/Toxic DoT. Other targets receive -1 Special Attack before Toxic and -2 during Toxic. Level changes replace the Poison-owned contribution conceptually; they never stack -1 and -2 or mutate unrelated stages.

Haze suppresses the current Poison-owned Special Attack contribution without removing accumulation, changing timers, resetting resistance, or stopping progression. Suppression remains for that active Poison/Toxic cycle and is not re-established by later ticks or level transitions.

DoT numbers and cadence remain deferred. State/rules expose policy for later work: pre-Toxic Poison is non-lethal, Toxic is lethal, and Poison-typed receivers take zero custom DoT. This phase emits no custom Poison HP-damage events, avoiding an invented amount or schedule.

## Protect and Deteriorating Shield

Protect activity, not stored DS history, gates every penetration rule. Existing `effectPenetrationChance` and `effectPenetrationMultiplier` already return full penetration when no Protect stance is active; Fire, Ice, and Drowsy use these APIs. Tests will lock that invariant for all four type systems.

Fresh Poison under active Protect uses the shared chance table: levels 1–9 yield 0%, 5%, 10%, 20%, 30%, 60%, 70%, 80%, and 90%. Failure creates no state or history mutation. Without active Protect, fresh Poison is guaranteed regardless of DS history.

Later move accumulation under active Protect uses the shared Fire-style multiplier table: 0%, 8%, 16%, 24%, 32%, 45%, 60%, 75%, and 90%. Integer accumulation uses the existing Fire convention: round to the nearest integer and guarantee at least 1 only when penetration and base gain are both positive. At base gain 9 this produces 0/1/1/2/3/4/5/7/8. Passive gain and all timer decay ignore DS. Without active Protect, the full current move gain applies.

## Damage integration

While a target is Toxic, an incoming Poison-type damaging move receives a 1.20 multiplier even when the target itself is Poison typed. The shared type-effect damage modifier accepts Fire, Ice, and Poison move identity, applying each target-specific modifier once in the existing centralized damage path. Poison delivery observes final positive HP damage after Protect; misses and invalid or zero-damage hits do not submit an interaction.

## HUD and visuals

The unified effect row receives `TYPE_POISON`, `TYPE_POISON_LV1`, `TYPE_POISON_LV2`, and `TYPE_TOXIC`. Pre-Toxic views expose accumulation as remaining/progress against 99; the renderer adds visual segment marks at 33 and 66 without quantizing the underlying value. Toxic switches to remaining ticks over 180. Backward decay changes the icon immediately.

`ActionBattlePoisonVisuals` emits subtle level-dependent particles when an entity is deployed: green at Poison, mixed green/purple at Lv1, mostly purple at Lv2, and purple at Toxic. Visuals consume authoritative state and never drive it.

## Failure handling and invariants

Invalid session IDs, Pokemon IDs, ticks, move contexts, targets, and non-finite penetration inputs are rejected without mutation. State deadlines use overflow-safe tick addition. An active state never remains at accumulation zero, Toxic never refreshes, same-level gain never resets the level timer, and decay-to-zero always prevents same-tick passive resurrection.

## Verification

Deterministic standalone regression tasks cover all accumulation boundaries, fresh application, Steel precedence, passive gain, threshold/timer behavior, decay and same-tick zero removal, Toxic lifetime/no-refresh, stat inversion and ownership, Haze, Toxic damage vulnerability, resistance floor, clean reset, recall/off-field ticking, HUD views, and both DS tables. Fire, Ice, and Drowsy tests explicitly compare stored DS history with inactive Protect against the active-Protect behavior.

Final verification runs:

```powershell
.\gradlew.bat test --console=plain --no-daemon --max-workers=1
.\gradlew.bat compileJava --console=plain --no-daemon --max-workers=1
git diff --check
```
