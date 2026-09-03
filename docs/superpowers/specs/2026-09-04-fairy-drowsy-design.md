# Fairy / Drowsy Type-Effect Design

## Goal

Add the dungeon-session-scoped Fairy Drowsy mechanic without duplicating ACTION Sleep, while revising sleeping-hit damage and wake behavior through the existing damage pipeline.

## Architecture

Fairy owns only Drowsy application, timing, escalating history, completion routing, its same-type Special Defense contribution, and its presentation. `ActionBattleDrowsyTracker` remains attached to the existing per-Pokemon `ActionBattleTypeEffectState`, allowing active Drowsy and its history to continue between battles until dungeon cleanup. Existing ACTION Sleep remains the sole source of truth for Sleep state, ticking, HUD, command restrictions, animation, and removal.

All application routes converge on `ActionBattleFairyController.applyDrowsy(...)`. The initial automatic route is a successfully executed Fairy-type, zero-power move aimed at an enemy Pokemon. The controller rejects Steel targets, active reapplications, and deteriorating-shield failures before mutating tracker state. A later metadata route can call the same method.

## Drowsy lifecycle

The tracker begins with a 180-tick next duration. Applying Drowsy creates one immutable countdown using that duration and cancels any clean-reset countdown. Reapplication while active is a complete no-op.

Natural completion removes Drowsy, rolls one existing Sleep duration of 60–180 ticks, and increments the next duration by 180 ticks without a cap. The single roll is routed in this order:

1. Dragon targets invoke a clearly named Uproar completion hook with the rolled duration.
2. Fairy targets receive a Fairy-owned +2 Special Defense contribution for the rolled duration.
3. Other targets enter the existing ACTION Sleep state for the rolled duration.

No Uproar implementation currently exists. Fairy will therefore expose the completion event/hook and retain the completion lifetime needed for history/reset timing, but will not invent Dragon combat behavior or silently substitute Sleep. This is the precise integration blocker for later Dragon work.

Once the routed completion lifetime has ended, the tracker starts a 360-tick clean window. Reapplication interrupts the window and uses the increased duration. Completing the full clean window resets the next duration to 180 ticks. Haze suppresses only the current Fairy-owned Special Defense contribution while allowing its completion lifetime and Drowsy history to continue.

Actual player and trainer swap paths notify Fairy using the outgoing Pokemon UUID. Recall cancels active Drowsy without rolling, routing, or increasing history, then starts the clean window if no relevant completion remains. Battle-end entity cleanup does not notify Fairy, preserving dungeon-session state. Existing ACTION recall cleanup is narrowed so Sleep remains active and continues ticking while its other recall-cleared effects retain their current behavior.

## Sleep integration

The existing Sleep rules gain a reusable 3–9 second duration roller and one pure sleeping-hit multiplier helper. Against a sleeping target, the multiplier table is:

| Damaging hit | Multiplier |
|---|---:|
| Ordinary | 1.20 |
| Fairy | 1.25 |
| Explicit wake | 1.25 |
| Fairy + explicit wake | 1.50 |

Explicit wake detection uses the existing move-effect metadata pipeline through a minimal reusable `wake`/`on_hit`/`target` predicate; no move names are assigned in this task.

The multiplier is applied exactly once after ACTION stat and Fire/Ice modifiers but before Protect/DS final reduction. Sleep is removed only after final positive HP damage is observed. Misses, non-damaging moves, and fully blocked hits do not wake. This keeps melee and projectile semantics identical without spawning duplicate damage or bypassing Protect.

## Shared integration

`ActionBattleTypeEffectState` stores an optional Fairy tracker beside Fire and Ice. The shared controller exposes narrow Drowsy application, view, recall, Haze, completion, and Fairy Special Defense queries while delegating Fairy rules to the Fairy package. The stat resolver adds the Fairy-owned contribution only to `SPECIAL_DEFENSE`.

The type-effect runtime ticks Fairy even when no Pokemon entity is deployed, so countdowns, completion lifetimes, and clean-reset history continue during dungeon downtime. Existing dungeon exit/logout/reset cleanup clears the tracker and all Fairy-owned state through the current type-effect cleanup path.

The unified status row receives `TYPE_FAIRY_DROWSY` with remaining and total ticks. Drowsy disappears before the routed Sleep/buff/Uproar result is presented. Lightweight sleepy/star particles are permitted; head animation is deferred because it risks interfering with existing Pokemon animation control.

## Verification

Focused deterministic tests cover escalating uncapped durations, active no-op reapplication, all completion routes and Dragon/Fairy precedence, recall cancellation, clean-reset interruption/completion, Steel immunity, exact DS probabilities, Haze suppression, stat ownership, HUD timing, one-roll routing, wake multipliers, guaranteed post-damage waking, fully blocked hits, and lack of duplicate Sleep. The full Gradle test suite and `compileJava` must pass without Fire or Ice regressions.
