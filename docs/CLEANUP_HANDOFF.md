# Poison / Toxic Cleanup Handoff

## Implemented

- Added a dungeon-session-scoped Poison tracker alongside Fire, Ice, and Fairy/Drowsy. All four type effects can coexist on one Pokemon.
- Added continuous accumulation with exact ranges: 1-32 Poison, 33-65 Poison Lv1, 66-98 Poison Lv2, and 99 Toxic. There is no Poison Lv3.
- Fresh qualifying Poison interactions establish accumulation 1. Later direct interactions use a shared gain that starts at 9, drops by one after each natural cycle to a minimum of 1, and resets after 360 completely clean ticks.
- Added 20-tick passive progression of +3/+6/+9 for the three pre-Toxic levels. Each pre-Toxic level has a 120-tick timer and exact expiry loss of 5/8/12; removal at zero occurs before same-tick passive progression.
- Added fixed 180-tick Toxic behavior. Toxic has no passive gain or normal decay, cannot gain accumulation or refresh, and disappears completely on expiry.
- Steel typing rejects the custom mechanic before tracker creation. Poison typing remains eligible, receives +1 Special Attack during all pre-Toxic levels and +2 during Toxic, and receives no custom Poison/Toxic damage in this phase.
- Normal receivers receive a Poison-owned -1 Special Attack during every pre-Toxic level and -2 during Toxic. Haze suppresses only that owned contribution while preserving accumulation, timers, and resistance history.
- Toxic targets take 1.20x incoming Poison-move damage through the centralized Fire/Ice/Poison damage chain, including Toxic Poison-type targets.
- Damaging Poison delivery occurs only after final positive HP damage. Enemy-targeting Poison status moves reuse the status-projectile route. Toxic Spikes remains on its existing path and does not deliver this mechanic.
- Poison progresses through session UUID state without requiring a deployed entity. Recall and battle teardown do not clear it; dungeon cleanup uses the existing type-effect cleanup route.
- Added active-Protect-only penetration. Fresh Poison uses the shared chance table, while active pre-Toxic Poison uses the shared Fire-style multiplier table with positive rounded gains clamped to at least 1. Passive progression ignores Deteriorating Shield.
- Audited Fire, Ice, and Fairy/Drowsy against the same boundary. Their controller paths now have regression coverage proving stored Deteriorating Shield history is ignored without an active Protect stance and used while a stance is active.
- Added `TYPE_POISON`, `TYPE_POISON_LV1`, `TYPE_POISON_LV2`, and `TYPE_TOXIC` to the unified status row. Pre-Toxic rings show accumulation out of 99 with marks at 33 and 66; Toxic shows remaining time out of 180.
- Added low-frequency visual-only particles that shift from green toward purple by level. Missing or recalled entities emit no particles and do not affect progression.

## Deferred damage contract

This phase intentionally emits no custom Poison or Toxic HP-damage events. Exact damage amounts and cadence remain deferred. The state preserves the required typing and lifecycle boundaries so future damage can implement non-lethal normal Poison, lethal Toxic, and complete Poison-type damage immunity without inventing values now.

## Automated coverage

- Accumulation boundaries, threshold jumps, timer preservation/reset, passive gain, decay ordering, zero removal, Toxic expiry/no-refresh, integer clamping, stat inversion, Haze, and Toxic's 1.20 multiplier.
- Fresh-versus-repeat application, supplied penetrated gain, resistance progression to 1, 359/360-tick clean reset, reset interruption, and Toxic reapplication rejection.
- Shared Fire/Ice/Drowsy/Poison coexistence, Special Attack ownership, Haze preservation, Poison-type inversion, and centralized damage behavior.
- Steel immunity, strict chance comparisons, exact rounded multiplier examples, and controller-level active-versus-inactive Protect behavior with retained Deteriorating Shield history for Fire, Ice, Drowsy, and Poison.
- Final-positive-damage qualification and entity-independent progression through decay, both normal thresholds, Toxic, and natural completion.
- HUD identity/progress/countdown mapping, backward level changes, and green-to-purple particle mix selection.

## Manual gameplay verification still required

- Confirm representative Cobblemon Poison status moves expose the expected enemy target-category strings and travel through the existing projectile route.
- Verify fresh and repeated melee, ranged, status, and true multi-hit Poison interactions in-game, including misses and fully blocked hits.
- Exercise accumulation thresholds, same-level timer preservation, decay into lower levels, zero removal, Toxic expiry, resistance progression, and the 18-second clean reset with visible HUD feedback.
- Verify Steel and Steel/Poison immunity, Poison-type Special Attack inversion, Haze suppression, and Toxic's 1.20 incoming Poison damage for normal and Poison-type targets.
- Verify recall, swaps, between-battle downtime, dungeon exit, logout, and full reset behavior.
- Verify all nine active Deteriorating Shield levels for fresh and existing Poison, plus the unshielded-history boundary for Fire, Ice, Drowsy, and Poison.
- Review the fallback status icon, segmented ring marks, unified-row ordering, and green-to-purple particle density in the live client.

## Verification

- `\.\gradlew.bat test --console=plain --no-daemon --max-workers=1` - `BUILD SUCCESSFUL` on 2026-09-04; all existing suites and five new Poison executables ran.
- Focused Poison plus Fire/Ice/Drowsy/Protect regression suites - `BUILD SUCCESSFUL` on 2026-09-04.
- `\.\gradlew.bat compileJava --console=plain --no-daemon --max-workers=1` - `BUILD SUCCESSFUL` on 2026-09-04 after the last review fixes.
- `git diff --check` - no whitespace errors after the last review fixes; Git reported only LF-to-CRLF conversion notices.

No manual gameplay checks were performed or claimed during this implementation session.
