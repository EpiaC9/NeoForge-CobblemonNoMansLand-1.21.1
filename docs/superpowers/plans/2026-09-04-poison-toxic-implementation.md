# Poison / Toxic Type-Effect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add continuous, persistent Poison/Toxic progression with source-owned Special Attack effects, Toxic Poison-move vulnerability, active-Protect-only penetration, unified HUD state, and off-field ticking.

**Architecture:** A focused Poison state and tracker live beside Fire, Ice, and Drowsy in the dungeon-session type-effect container. The Poison controller owns delivery/type/Protect decisions; shared controller, runtime, stat, damage, and HUD layers expose only narrow integration points.

**Tech Stack:** Java 21, NeoForge 1.21.1, Cobblemon/Fight-or-Flight APIs, standalone Java regression executables, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-04-poison-toxic-design.md`

## Global Constraints

- Accumulation is continuous 0–99 with levels NONE, POISON, POISON_LV1, POISON_LV2, and TOXIC; there is no Poison Lv3.
- Fresh Poison starts at 1; direct gain begins at 9, falls by 1 per natural cycle to a minimum of 1, and resets after 360 clean ticks.
- Pre-Toxic timers are 120 ticks, Toxic is fixed at 180 ticks, and passive accumulation occurs every 20 ticks.
- Steel is immune; Poison typing inverts owned Special Attack and receives no future custom DoT but remains vulnerable to Toxic's 1.20 Poison-move multiplier.
- This phase emits no Poison/Toxic HP-damage events because exact DoT values and cadence are deferred.
- DS history has no effect without an active Protect stance. Toxic Spikes and move-specific overrides remain unchanged.
- Preserve existing Fire, Ice, Fairy/Drowsy, Sleep, Hail, Protect, and generic ACTION mechanics except for fixes required by the explicit DS invariant.

---

### Task 1: Poison rules and active state machine

**Files:**
- Create: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonRules.java`
- Create: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonState.java`
- Create: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonStateTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Produces: constants and `PoisonLevel levelForAccumulation(int)`, `passiveGain(PoisonLevel)`, `decay(PoisonLevel)`, `specialAttackStages(PoisonLevel, boolean)`, `modifyIncomingDamage(double, boolean, boolean)`.
- Produces: state operations `applyDirectGain(int, long)`, `TickResult tick(long)`, `suppressSpecialAttackByHaze()`, and immutable getters for accumulation, level, deadlines, stat stages, and Haze state.

- [ ] **Step 1: Register the focused executable and write failing boundary tests**

  Register `poisonStateTest` as a `JavaExec` dependency of `test`. Assert literal mappings `0/NONE`, `1/POISON`, `32/POISON`, `33/POISON_LV1`, `65/POISON_LV1`, `66/POISON_LV2`, `98/POISON_LV2`, and `99/TOXIC`.

- [ ] **Step 2: Add failing timing and transition cases**

  Exercise fresh state at `1` with deadline `120`; direct `+9` preserving a same-level deadline; threshold crossings resetting to `now + 120`; per-level passive gains at 20 ticks; final-level selection on multi-threshold jumps; and Toxic at 99 with deadline `now + 180`.

- [ ] **Step 3: Add failing expiry-order cases**

  Assert level expiry subtracts literal 5/8/12 values, recalculates downward levels, starts a new 120-tick timer if still active, removes accumulation 4 at expiry, and does not apply a same-tick passive gain after removal. Assert Toxic has no passive/normal decay, ignores direct hits without deadline changes, and expires to zero at 180 ticks.

- [ ] **Step 4: Verify RED**

  Run: `.\gradlew.bat poisonStateTest --console=plain --no-daemon --max-workers=1`

  Expected: compilation fails because Poison rule/state classes are absent.

- [ ] **Step 5: Implement the minimal rules and deterministic state machine**

  Use `ActionBattleTiming.safeAdd`. Process level expiry/removal before Toxic expiry, threshold recalculation, and passive accumulation. Return explicit `TickResult.NONE`, `CHANGED`, or `COMPLETED_NATURALLY`; never retain accumulation zero.

- [ ] **Step 6: Verify GREEN and commit**

  Run the Task 1 command; expected: `BUILD SUCCESSFUL`.

  Commit: `feat: add poison toxic state machine`

### Task 2: Persistent resistance tracker

**Files:**
- Create: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonTracker.java`
- Create: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonTrackerTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: Task 1 `ActionBattlePoisonState`.
- Produces: `applyMove(long, boolean, int)` where the boolean is Poison receiver typing and the integer is already-penetrated direct gain; `tick(long)`; views; `moveAccumulationGain()`; `cleanResetEndTick()`; and `suppressSpecialAttackByHaze()`.

- [ ] **Step 1: Write failing fresh/reapplication tests**

  Assert the first accepted interaction creates accumulation 1 regardless of direct gain. Assert later interactions add exactly the supplied penetrated gain, zero gain does not mutate, and Toxic ignores later interactions.

- [ ] **Step 2: Write failing resistance/reset tests**

  Complete cycles and assert gains `9→8→7→…→1→1`. Assert cleanup-style discard does not decrement. After completion assert gain remains penalized at 359 clean ticks, resets at 360, and early fresh reapplication cancels the reset while preserving the penalty.

- [ ] **Step 3: Verify RED**

  Run: `.\gradlew.bat poisonTrackerTest --console=plain --no-daemon --max-workers=1`

- [ ] **Step 4: Implement the minimal tracker**

  Keep the tracker after natural removal, decrement only on `COMPLETED_NATURALLY`, and remove it only when state is absent, gain is 9, and no reset deadline remains.

- [ ] **Step 5: Verify GREEN and commit**

  Run the Task 2 command; expected: `BUILD SUCCESSFUL`.

  Commit: `feat: track poison reapplication resistance`

### Task 3: Shared container, stats, Haze, and damage

**Files:**
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ActionBattleTypeEffectState.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ActionBattleTypeEffectController.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/ActionBattleStatResolver.java`
- Modify: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ActionBattleTypeEffectControllerTest.java`

**Interfaces:**
- Consumes: Task 2 tracker.
- Produces: `applyPoisonMove(sessionId, pokemonId, tick, poisonTyped, penetratedGain)`, `poisonView(...)`, `poisonSpecialAttackStages(...)`, `suppressPoisonSpecialAttackByHaze(...)`, and three-type `modifyDamage(..., fireMove, iceMove, poisonMove, damage, tick)`.

- [ ] **Step 1: Write failing coexistence/stat tests**

  Assert Fire, Ice, Drowsy, and Poison coexist for one Pokemon. Assert normal Poison/Toxic contributes -1/-2 Sp. Atk and Poison-typed Poison/Toxic contributes +1/+2; combine with a generic contribution using `ActionBattleStatResolver.combineStages` and verify expiry removes only Poison ownership.

- [ ] **Step 2: Write failing Haze and Toxic damage tests**

  Assert Haze changes Poison-owned stages to zero while accumulation, level, deadline, and resistance remain. Assert Toxic plus Poison move returns 120 from 100, while non-Toxic or non-Poison moves return 100, including a Poison-typed Toxic receiver.

- [ ] **Step 3: Verify RED**

  Run: `.\gradlew.bat typeEffectControllerTest --console=plain --no-daemon --max-workers=1`

- [ ] **Step 4: Extend the shared state/controller and stat resolver**

  Store one optional tracker, tick it beside other effects, expose immutable `PoisonView`, add its stages only for `SPECIAL_ATTACK`, and preserve independent cleanup.

- [ ] **Step 5: Extend the central modifier signature**

  Chain Fire, then Ice, then Poison using each rules helper once. Retain compatibility overloads for existing call sites until Task 5 migrates the damage adapter.

- [ ] **Step 6: Verify GREEN and commit**

  Run the Task 3 command; expected: `BUILD SUCCESSFUL`.

  Commit: `feat: integrate poison type effect state`

### Task 4: Protect/DS invariant and Poison delivery rules

**Files:**
- Create: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonController.java`
- Create: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonDeliveryTest.java`
- Modify: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/fire/ActionBattleFireStateTest.java`
- Modify: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ice/ActionBattleIceDeliveryTest.java`
- Modify: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/fairy/ActionBattleFairyControllerTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: shared Protect APIs `activeStance`, `effectPenetrationChance`, and `effectPenetrationMultiplier`.
- Produces: `onSuccessfulEnemyInteraction(attacker, target, move)`, reusable `applyPoisonFromMove(...)`, `penetratedDirectGain(int, double)`, and `isQualifyingPoisonMove(Move)` using Fairy's target-category parser.

- [ ] **Step 1: Write failing Poison immunity/penetration tests**

  Assert Steel and Steel/Poison reject before tracker mutation; Poison receivers accept. Assert fresh chance thresholds are strict and direct base gain 9 maps through multipliers to `0/1/1/2/3/4/5/7/8`, with positive penetrations clamped to at least 1.

- [ ] **Step 2: Add mandatory inactive-versus-active Protect tests**

  Build real `ActionBattleProtectController` state with DS history, let the stance expire while history remains, and assert chance/multiplier return 1.0. With an active stance assert the correct table value. Cover this shared boundary from Fire, Ice, Drowsy, and Poison regression executables rather than mocking controller output.

- [ ] **Step 3: Verify RED**

  Run: `.\gradlew.bat poisonDeliveryTest fireStateTest iceTypeEffectDeliveryTest fairyControllerTest --console=plain --no-daemon --max-workers=1`

- [ ] **Step 4: Implement controller decisions without changing Toxic Spikes**

  Require active dungeon/battle identity, Poison move type, enemy target, and successful interaction. For fresh Poison use chance penetration; for active pre-Toxic Poison use multiplier penetration; for Toxic return a no-op result. Query Protect APIs so inactive stance automatically yields full behavior.

- [ ] **Step 5: Verify GREEN and commit**

  Run the Task 4 command; expected: `BUILD SUCCESSFUL`.

  Commit: `feat: add poison move delivery`

### Task 5: Combat and off-field runtime integration

**Files:**
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/compat/FightOrFlightAdapter.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/projectile/ActionBattleProjectileEntity.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/ActionBattleEffectRuntime.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ActionBattleTypeEffectRuntime.java`
- Create: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonRuntimeTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: Task 4 delivery and Task 3 shared damage/state APIs.
- Guarantees: damaging delivery occurs only after final positive HP damage; enemy status delivery occurs only after successful projectile interaction; session ticking needs no entity; recall does nothing to Poison.

- [ ] **Step 1: Write failing hit qualification/runtime tests**

  Assert success with HP `100→80` submits one interaction, while miss and `100→100` do not. Assert repeated calls model multi-hit accumulation. Tick a tracker by UUID with no entity across thresholds, decay, Toxic, and natural completion; assert no recall API clears or pauses it.

- [ ] **Step 2: Verify RED**

  Run: `.\gradlew.bat poisonRuntimeTest --console=plain --no-daemon --max-workers=1`

- [ ] **Step 3: Integrate melee/projectile/status delivery**

  Call Poison after Protect has finalized HP damage. Qualifying non-damaging Poison status moves share the existing Fairy enemy-target projectile route. Do not route Toxic Spikes through this path.

- [ ] **Step 4: Add Toxic damage once and off-field ticking**

  Extend `scaleActionDamage` after Fire/Ice with the Poison flag, before Sleep/Protect. Tick Poison from `ActionBattleTypeEffectRuntime` and route Haze suppression from `ActionBattleEffectRuntime`; do not add recall clearing.

- [ ] **Step 5: Verify GREEN and commit**

  Run the Task 5 command plus `.\gradlew.bat compileJava --console=plain --no-daemon --max-workers=1`; expected: both `BUILD SUCCESSFUL`.

  Commit: `feat: progress poison through action runtime`

### Task 6: Unified HUD and visuals

**Files:**
- Create: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonVisuals.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/ActionBattleHudSync.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/client/battle/action/ActionBattleStatusVisualRegistry.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/client/battle/action/ActionBattleStatusHudRenderer.java`
- Modify: `src/main/java/net/epiac9/cobblemonnml/battle/action/typeeffect/ActionBattleTypeEffectRuntime.java`
- Create: `src/test/java/net/epiac9/cobblemonnml/battle/action/typeeffect/poison/ActionBattlePoisonHudTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: `PoisonView(level, accumulation, toxicRemainingTicks, ownedSpecialAttackStages, statSuppressedByHaze)`.
- Produces: four unified-row status IDs, pre-Toxic progress `accumulation/99`, Toxic countdown `remaining/180`, two segment markers, and subtle level-dependent particles.

- [ ] **Step 1: Write failing HUD mapping tests**

  Assert accumulation 1/33/66 maps to the three Poison IDs with duration 99, accumulation 98 remains Lv2 at 98/99, and Toxic begins at 180/180 then drains. Assert backward decay immediately changes the status ID.

- [ ] **Step 2: Verify RED**

  Run: `.\gradlew.bat poisonHudTest --console=plain --no-daemon --max-workers=1`

- [ ] **Step 3: Add unified status and segmented rendering**

  Append one Poison status to the existing row. Register four visuals using existing icon fallback if dedicated textures are absent. Draw only two subtle radial boundary marks at 33/99 and 66/99 for pre-Toxic IDs; leave Toxic and all existing statuses unchanged.

- [ ] **Step 4: Add visual-only particles**

  Resolve deployed entities by Pokemon UUID and emit low-frequency green/purple particle mixes selected by level. Missing/recalled entities are a no-op and never affect progression.

- [ ] **Step 5: Verify GREEN and commit**

  Run the Task 6 command; expected: `BUILD SUCCESSFUL`.

  Commit: `feat: display poison toxic progression`

### Task 7: Full regression verification and handoff

**Files:**
- Modify: `docs/CLEANUP_HANDOFF.md`

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: exact change summary, verification evidence, deferred DoT statement, and manual gameplay checklist.

- [ ] **Step 1: Run the complete test suite**

  Run: `.\gradlew.bat test --console=plain --no-daemon --max-workers=1`

  Expected: `BUILD SUCCESSFUL`, including all existing Fire/Ice/Fairy/Sleep tasks and all new Poison tasks.

- [ ] **Step 2: Compile production Java independently**

  Run: `.\gradlew.bat compileJava --console=plain --no-daemon --max-workers=1`

  Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Audit final scope and whitespace**

  Run: `git diff --check`

  Confirm no legacy Poison restoration, Poison Lv3, invented DoT amount/cadence, Toxic refresh, recall clearing, Toxic Spikes delivery, move-specific values, or unrelated generated output.

- [ ] **Step 4: Update the cleanup handoff**

  Record changed components, exact test commands/results, active-Protect DS audit result, deferred DoT behavior, HUD/particle behavior, and every unperformed manual check without claiming in-game verification.

- [ ] **Step 5: Commit the verified feature**

  Commit: `feat: implement poison toxic type effect`
