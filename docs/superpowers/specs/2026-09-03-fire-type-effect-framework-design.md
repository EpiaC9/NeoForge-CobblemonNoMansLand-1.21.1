# Fire Type-Effect Framework Design

## Objective

Create a dungeon-session-scoped ACTION type-effect framework with Fire as its first concrete mechanic. Generic ACTION effects remain separate. The framework supports independent coexisting type effects per Pokemon without implementing speculative types.

## Scope and invariants

- Fire state persists across battle end, victory, surrender, recall, swaps, and later encounters in the same dungeon session.
- Fire state clears on relevant player dungeon exit/logout, full dungeon reset/end, and server cleanup.
- A new dungeon session never inherits old type-effect state.
- Cinders and Burn deal no periodic damage in this implementation.
- Burn's 1.20 multiplier affects incoming Fire-move damage only.
- Sleep, Confusion, Evasion, controls, persistent effects, Protect, Deteriorating Shield, Haze, and existing HUD packet structure retain their approved behavior.
- No removed legacy status system, texture, or animation datagen is restored.

## State ownership

`ActionBattleTypeEffectController` owns an in-memory hierarchy keyed by dungeon session UUID and Pokemon UUID. Every operation validates the currently active dungeon session UUID and discards stale session state on mismatch.

Each Pokemon entry is an `ActionBattleTypeEffectState`. It contains identity and optional type-specific state. Initially it contains only `ActionBattleFireState`; later types can be added independently without a single global type-effect slot.

`ActionBattleFireState` exclusively owns:

- floating-point pressure;
- `BUILDUP`, `CINDERS`, or `BURN` phase;
- pressure decay-delay and next-decay ticks;
- Burn end tick;
- Fire-owned Attack stages;
- whether Haze suppressed the Fire contribution.

## Fire rules

`ActionBattleFireRules` defines:

- normal pressure: `20.0`;
- Cinders threshold: `50.0`;
- Burn threshold and pressure cap: `100.0`;
- decay delay: `360` ticks;
- decay interval: `20` ticks;
- decay amount: `5.0`;
- Burn duration: `180` ticks;
- Burn incoming Fire-damage multiplier: `1.20`;
- Cinders Fire Attack contribution: `+1`;
- Burn Fire Attack contribution: `+2` total.

Pressure remains a `double` and is clamped to `0.0` through `100.0`. HUD conversion may round it, but phase decisions use the exact value.

## Progression and decay

A successful qualifying damaging Fire hit applies the caller-supplied base pressure after protection penetration. Any positive pressure resets the decay delay to 18 seconds. Zero pressure is a no-op.

- `0 < pressure < 50`: Build-Up.
- `50 <= pressure < 100`: Cinders.
- `pressure == 100`: Burn for exactly 180 ticks.

Below Burn, pressure stays unchanged for 360 ticks after the latest positive application. It then falls by 5 every 20 ticks. Crossing below 50 changes Cinders to Build-Up. Reaching zero removes Fire state.

During Burn, pressure remains locked at 100. Fire hits do not add pressure or modify any timer. Burn expiry sets pressure to zero, removes Fire's Attack contribution, and removes Fire state. Burn never downgrades to Cinders.

## Typing behavior

Typing is evaluated in this order:

1. If the target contains Fire typing, Fire's positive interaction applies and pressure may progress.
2. Otherwise, if the target contains Water typing, the custom Fire mechanic is immune and no pressure is applied.
3. Otherwise, normal harmful Fire progression applies.

Fire/Water therefore follows Fire behavior. Fire-owned Attack stages apply only to Pokemon containing Fire typing. No harmful DOT exists yet for any target.

## Stat ownership and Haze

`ActionBattleStatResolver` becomes the shared ACTION stat read path. It adds generic ACTION contributions to dungeon-scoped type-effect contributions and then applies the existing stat clamp. Damage, movement, accuracy, and HUD stage reads use the resolver. The generic effect controller does not depend on Fire.

Fire owns its contribution rather than changing and later subtracting a shared stage value. Cinders contributes +1 Attack and Burn contributes +2 total for Fire-type Pokemon.

Haze clears and suppresses Fire's contribution without clearing pressure, phase, decay, or Burn time. Tick processing never restores a suppressed contribution. A later Cinders-to-Burn threshold transition may establish the new +2 contribution only when Haze is no longer blocking stat changes; otherwise it remains suppressed.

## Combat integration

Melee/contact and projectile hit paths call one shared successful-hit hook after a confirmed hit. The hook receives attacker, target, move, and pressure amount. Fire eligibility is decided centrally; no hit detection is recreated and no once-per-command guard is added. Each successful multi-hit or AoE target impact can therefore apply its own caller-supplied pressure.

Burn damage uses the existing centralized ACTION damage scaling path:

1. Cobblemon/Fight-or-Flight base damage.
2. Resolved ACTION stat stages.
3. Type-effect damage modifiers, including Burn's 1.20 multiplier for incoming Fire moves.
4. Protect or Deteriorating Shield damage reduction.
5. Actual HP damage.

Cinders and Burn add no DOT.

## Deteriorating Shield integration

Protect exposes an explicit effect-penetration multiplier rather than exposing shield level semantics to Fire or reusing timed-effect duration scaling.

| Shield level | Pressure penetration |
| --- | ---: |
| 1 | 0% |
| 2 | 8% |
| 3 | 16% |
| 4 | 24% |
| 5 | 32% |
| 6 | 45% |
| 7 | 60% |
| 8 | 75% |
| 9 | 90% |

For base pressure 20, the resulting values are `0.0`, `1.6`, `3.2`, `4.8`, `6.4`, `9.0`, `12.0`, `15.0`, and `18.0`. Existing generic timed-effect interception remains unchanged.

## Dungeon runtime and cleanup

`ActionBattleTypeEffectRuntime` ticks from the dungeon server-tick flow whenever a valid session exists. It advances every tracked Pokemon by identity and game time, whether deployed, recalled, between battles, or otherwise missing a world entity.

Normal ACTION cleanup never clears type effects. Player exit or dungeon-runtime logout clears that player's party Pokemon entries. Full dungeon reset/end clears all entries, including trainers and abandoned state. Server stopping also clears all in-memory state. Session-ID validation provides a fallback against missed cleanup.

## HUD and particles

The existing status payload is reused.

- `TYPE_FIRE_BUILDUP`: rounded pressure over 100.
- `TYPE_FIRE_CINDERS`: rounded pressure over 100.
- `TYPE_FIRE_BURN`: remaining Burn ticks over 180.

HUD status ordering is deterministic: Deteriorating Shield, generic ACTION effects, then type effects. All remain in the existing single status row.

The visual registry maps Fire states to `textures/gui/action/type_effect/`:

- `fire_build_up.png`: small heat/flame mark;
- `cinders.png`: smoke/ember mark;
- `burn.png`: stronger flame mark.

The placeholders are transparent, static, and visually distinct. No animation datagen is created. Build-Up has no persistent particles; Cinders has sparse smoke and Burn has sparse fire particles.

## Invalid input behavior

Inactive or mismatched dungeon sessions, invalid IDs, missing Pokemon participants, misses, failed projectiles, non-damaging moves, non-Fire moves, Water-only targets, non-positive pressure, and fully blocked pressure are no-ops.

## Verification

Automated state and rule coverage will verify:

- pressure progression and exact phase thresholds;
- the 18-second delay and 5-per-second decay;
- fractional shield penetration;
- Burn activation, fixed duration, no pressure gain, and no refresh;
- Water immunity and Fire/Water precedence;
- Fire-owned stages and Haze suppression;
- Burn's Fire-only damage modifier;
- dungeon-session isolation and cleanup;
- generic/type-effect coexistence where dependencies permit.

The final build gate is:

```powershell
.\gradlew.bat compileJava --console=plain --no-daemon --max-workers=1
```

Interactive Minecraft validation remains a manual handoff checklist covering HUD appearance/order, particles, recall and between-battle ticking, victory/surrender persistence, dungeon exit/reset cleanup, and coexistence with generic effects.
