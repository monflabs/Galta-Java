# Design brief: transpiled-mode module import/export live bindings + hoisting

**Status: IMPLEMENTED (2026-08-25/26).** P1-P4 below, plus an additional,
unanticipated piece (a static `resolveExport()` override on
`JSTranspiledUnit`, needed for cyclic indirect-export resolution and
star-export ambiguity identity - discovered only once P1-P4 were in place
and self-registration started reaching those cases for real), are all
implemented and verified via a full unfiltered transpiled-mode sweep. See
`KnownGaps.md`'s `language/module-code` section ("Fixed: `language/
module-code` transpiled-mode live-bindings architecture") for the final
verified result and the small number of narrower, separately-diagnosed
gaps that remain. This document is kept as-is below (unchanged from when
it was written) for the design rationale and prerequisite-ordering
reasoning behind each piece, still accurate to what was actually built.

## The problem, precisely

GaltaJS's transpiler correctly handles the common case: a plain
`import {x} from './dep.js'` where `dep.js` is a genuine, acyclic dependency
that has already fully evaluated by the time the importing statement's own
generated code runs, in its own natural source position. Three related but
distinct spec requirements are NOT implemented for anything beyond that
common case:

1. **Import hoisting.** A module's `import` bindings must be visible/usable
   before ANY of its own top-level code runs, regardless of the `import`
   statement's own source position (test262
   `language/module-code/instn-iee-bndng-*.js`: code on line 1 reads an
   imported binding whose `import` statement is on line 20). Interpreted
   mode implements this via `ASTProgram.hoistDeclarations()` calling
   `ASTImport.hoistBindings()` for every import child before `evaluate()`
   ever runs the body. Transpiled codegen has no equivalent at all —
   `ASTImport.transpileJavaStatement()` only emits its value-read at the
   import statement's own position in the generated Java method.

2. **Export liveness.** If an exporting module reassigns its own exported
   `var`/`let` variable AFTER the `export` statement ran, every importer
   (including this same module, via a self-import) must observe the NEW
   value on next read — exports are live bindings, not one-time snapshots
   (test262 `language/module-code/namespace/internals/get-str-update.js`:
   `export var local1 = 111; ... local1 = 333;` then `ns.local1` must read
   `333`). Interpreted mode gets this via `JSInterpretedUnit.
   getExportAccessor()` returning a live `VarAccessor` aliased straight to
   the exporting module's own top-level variable cell (`resolveExport()`,
   spec 16.2.1.6.3) — every read indirects through it, so it doesn't matter
   when the alias was created relative to when the value was last written.
   `JSTranspiledUnit` does NOT override `getExportAccessor()`, so it falls
   through to `AbstractModule`'s default: a ONE-TIME snapshot
   (`VarAccessor.ofStatic`) of whatever `getNamedExports()` held at the
   moment `ASTExport`'s own codegen called `addNamedExports(name, value)` —
   which happens exactly once, at the `export` statement's own generated-code
   position, and is never touched again by a later plain reassignment
   (which only writes the module's local `Object[]` array slot).

3. **Self/circular identity resolution.** An `import` whose target resolves
   to the SAME module currently being evaluated (directly, or transitively
   through another file) must find that SAME module instance, not load a
   fresh duplicate. `JSInterpretedUnit.executeWithContext()` calls
   `gc.registerRootModule(this)` before hoisting/body execution (for a root)
   and `JSSourceModuleResolver.loadInterpretedModule()` calls an
   `earlyRegister` callback before `initModule()` (for a dependency).
   `JSTranspiledUnit.runValue()` calls neither — a transpiled root is NEVER
   entered into the global context's module cache, so any self/circular
   reference reaching back to it falls through to loading a completely
   separate, freshly-parsed-and-interpreted duplicate instead (which,
   because it's interpreted, happens to correctly implement (1) and (2)
   internally — this is why the gap has been invisible until now: the
   duplicate-instance fallback accidentally "works" by accident, via a
   completely different, correctly-behaving code path).

**The critical finding from attempt 7**: these three are NOT independent.
Fixing (3) alone (self-registration) — with ZERO change to (1) or hoisting
order — already regresses 65→76 test262 files in `language/module-code`,
because self-imports that used to accidentally work (via the duplicate-
interpreted-copy fallback) now find the REAL transpiled module instance,
which has neither live exports (2) nor any deferred-resolution capability
for "found myself, but I'm not done running yet." So (3) cannot ship without
(2), and a full fix for the ORIGINAL motivating bug (1) needs all three.

## Prerequisite work, in dependency order

### P1. Self-identity registration (small, mechanically simple, already spot-verified)

- `JSTranspiledUnit.runValue()` (`parent-js/js/src/main/java/org/monflabs/galtajs/rt/transpiler/JSTranspiledUnit.java`,
  around line 195-198): add `gc.registerRootModule(this);` right after
  `gc.setScriptUnit(this);`. This line alone was tested standalone this
  session and compiles/runs fine — the *infrastructure* is correct, it's
  just unsafe to deploy before P2/P3 exist.
- Check whether transpiled DEPENDENCY modules (not roots) need the same
  treatment: `JSSourceModuleResolver.loadTranspiledModule()` has no
  `earlyRegister` hook at all (unlike `loadInterpretedModule()`, which
  threads one through `JSModuleResolver.loadModule()`). Per this session's
  investigation, dependency modules currently always load via the
  INTERPRETED path regardless of the root's own mode ("modules aren't
  ahead-of-time transpiled per file") — confirm this is still true before
  investing effort in `loadTranspiledModule()`'s own registration; if
  confirmed, P1 is just the one `runValue()` line above.

### P2. Live export bindings for transpiled modules (the big prerequisite, not previously scoped)

This is the piece no prior attempt anticipated. Needs a `getExportAccessor()`
(and likely `getLiveDefaultExportAccessor()`) override on `JSTranspiledUnit`
mirroring `JSInterpretedUnit`'s shape — i.e., transpiled modules need a way
to hand out a live accessor into a specific named export's own storage,
not just a `JSObject` (`getNamedExports()`) populated by one-shot
`addNamedExports()` calls from `ASTExport`'s codegen.

**Design challenge**: interpreted mode's live accessor works because every
module-level variable already lives in a `VariableMap` cell reachable by
name (`executionContext.getLocalVariableEntry(name)`). Transpiled module-level
variables are plain `Object[]` array slot positions (`p_0[N]`), resolved by
INDEX at codegen time, not by name at runtime — there is no existing
"resolve a variable cell by name string, at runtime" mechanism for a
transpiled unit to build a `VarAccessor` out of.

Two directions worth evaluating (this session did not get far enough to
recommend one over the other with confidence):

- **(a) Runtime name→index map + live accessor wrapper.** Have
  `JSTranspiledUnit` (or its generated subclass) expose a
  `Map<String,Integer>` (or similar) from exported-variable name to its own
  `p_0` array index, built once at construction (the transpiler already
  knows every exported name's array index at CODEGEN time, per
  `ASTExport.transpileJavaStatement()`'s existing `addNamedExports(name,
  JSTranspiler.asVar(jsContext,v))` calls — `v.getJavaVariableIndex()` or
  equivalent should already be known there). Override `getExportAccessor(name)`
  to return a `VarAccessor` whose `getValue()`/`setValue()` read/write
  `this.p_0[index]` directly (needs `p_0` to be an instance field, not a
  local variable in `_runValue()` — check whether it already is one, or
  would need to become one; this could have broader implications for how
  the transpiled unit's own generated method is structured).
- **(b) Keep `addNamedExports()` value-based, but re-push updates.** Have
  every REASSIGNMENT of an exported local variable (not just its initial
  `export` statement) also call `addNamedExports(name, newValue)` again, so
  the stored value stays in sync. Simpler in principle (no new accessor
  machinery, no array-field restructuring) but requires the transpiler to
  KNOW, at every assignment codegen site (`ASTIdentifier`'s assignment
  path, `ASTVariableDecl`'s init path, `+=`/`++`/destructuring assignment
  targets, etc. — anywhere a module-level exported name can be written),
  that "this specific variable is exported, also push its new value" — a
  broader, more invasive change touching assignment codegen generally
  (higher blast radius, touches code far outside the module/import system),
  and still wouldn't be truly "live" for a namespace object holding a
  reference captured before some assignment (needs to always re-read
  fresh — actually this note doesn't apply since `getExportAccessor()`
  would still be called per-read by `ModuleNamespaceObject`, so (b) might
  work fine IF every write path is covered — but "every write path" is the
  real risk here).

(a) is probably lower-risk (localized to the module/export system, no
changes to general assignment codegen) but needs the array-field
restructuring question resolved first. Recommend spiking both directions
briefly (a throwaway JS snippet + generated-Java inspection) before
committing to one.

### P3. Default-export deferred-callback mechanism (bounded, ports ~1:1)

Port `JSInterpretedUnit.addDefaultExportCallback()`/`setDefaultExport()`
override (queues `Runnable`s, drains them the instant the real
`setDefaultExport()` call fires — which for transpiled code is already
emitted at `ASTExport.java` in the `defaultExport!=null` branch,
`b.println("setDefaultExport({0});", ...)`) onto `JSTranspiledUnit`. A
transpiled self-import reaching a not-yet-executed NON-hoistable default
export (`export default class{}`/`export default <expr>` — a hoistable one,
`export default function fn(){}`, might be resolvable more simply, mirroring
how `hoistBindings()` already special-cases it via
`getLiveDefaultExportAccessor()`) needs to register a callback that writes
into its own `p_0[N]` slot when the real export fires, instead of reading
`getDefaultExport()` synchronously and getting nothing.

### P4. Import-hoisting itself (the ORIGINAL motivating bug — only safe once P1-P3 exist)

Once self-imports can be reliably, correctly resolved even when reached
early (P1+P2+P3), hoisting cross-file AND self imports uniformly becomes
safe. This session's attempt 3 already has a verified-correct MECHANICAL
approach for the hoisting itself (early-emission without disturbing slot
allocation or source-map tracking) — reusable as-is once the self-import
correctness prerequisite exists:

- `ASTImport.java`: add a `transpiledEarly` guard field; check it at the
  top of `transpileJavaStatement()`, set it before the existing body runs,
  so a later normal-position call becomes a no-op.
- `ASTProgram.java` (`transpileJavaStatement()`, around line 1395-1413):
  right after `transpilerDeclareStatement(jsContext,b,0);`, loop over
  `getChildCount()`/`getChild(i)`/`skipTransparent(...)` (mirroring the
  EXISTING interpreted-mode `hoistDeclarations()` loop just above this
  method), and for each `ASTImport` child: `b.debugLocation(child);
  map.getCurrentBlock().add(b, child); imp.transpileJavaStatement(jsContext, b);`
  — the `mapBlock.add()` call is REQUIRED (mirrors `ASTBlock.
  transpileBlockStatements()`'s own pattern exactly) to avoid regressing
  `JSTranspilerMap`'s source-map line tracking (attempt 2's regression).
- With P1-P3 in place, the self-import EXCLUSION this session added
  (skip hoisting when `ModuleUtil.resolvePath(jsContext.getModuleName(),
  imp.getFrom()).equals(jsContext.getModuleName())`) should no longer be
  necessary — self-imports should be safe to hoist too once they resolve
  correctly regardless of timing. Try WITHOUT the exclusion first (simpler);
  fall back to keeping it if something still doesn't check out.
- The `moduleName` plumbing fix is a prerequisite for ANY transpile-time
  self-import detection: `JSTranspiler.compile(String,String,JSInterpretedUnit)`
  (`parent-js/js/src/main/java/org/monflabs/galtajs/transpiler/JSTranspiler.java`,
  ~line 104-106) currently hardcodes `moduleName=null` in its call to
  `compileResult()`, discarding `script`'s own already-populated real
  descriptor name. Fix: pass `script.getDescriptor().getName()` instead.
  Confirmed low-risk this session (exactly 3 callers repo-wide, all test
  infrastructure — `BaseProjectTestCase.java`, `BaseTestSuiteTest.java`,
  `Test262TestLibrary.java` — zero production runtime path; the only other
  consumer of `jsContext.getModuleName()` besides self-import detection is
  a cosmetic error-message string in `ASTExport.java`, currently always
  showing `null`). Safe to apply early/independently of the rest of this
  work if useful for other diagnostics in the meantime.

## Recommended approach

1. Spike P2's two design directions (a vs b above) with small, throwaway
   JS snippets + direct inspection of generated Java, BEFORE writing real
   code — this is the piece with the most design uncertainty and the
   highest blast-radius risk if done wrong.
2. Implement P1 + chosen P2 direction + P3 together (they're mutually
   dependent — P1 alone is already proven unsafe without P2/P3). Verify in
   isolation via `Test262OneTest`/`AdHocProbeTranspilerTests` against
   `language/module-code` specifically before touching anything broader.
3. Only once P1-P3 are verified clean (full unfiltered transpiled-mode
   sweep, diffed against the pre-change failure list for zero regressions —
   see Verification below) should P4 (hoisting) be attempted, reusing
   attempt 3's already-verified-safe mechanical approach.
4. Each of P1+P2+P3 (as one combined change) and P4 should get their own
   full-sweep verification pass and their own commit, per this project's
   established one-item-per-commit rhythm — don't combine P4 into the same
   commit as P1-P3.

## Verification methodology (established this session, reuse exactly)

1. Isolated fast-iteration checks: set `js-test-test262/src/test/java/test/test262/Test262OneTest.java`'s
   `TEST_FILE` to `"language/module-code"`, run via
   `mvn test -pl parent-js/js-test-test262 -Dtest=AdHocProbeTranspilerTests -q`
   (forces transpiler mode). Clear stale generated sources first:
   `rm -f parent-js/js-test-test262/src/test/java/compiled/*.java`. Current
   baseline: 63 failures (post the already-committed `export let` fix).
   Reset `TEST_FILE` back to `"built-ins/String"` when done — required
   convention, don't skip.
2. Before declaring victory on ANY change in this area, diff the FULL
   failure-file list (not just the count) against the pre-change baseline:
   ```
   grep -E ", Errors: [0-9]+ total, [0-9]+ expected" <log> | sed -E 's#^\s*##; s#, Errors:.*##' | sort > new.txt
   comm -23 new.txt baseline.txt   # NEW failures not in baseline = regressions, must be zero
   comm -13 new.txt baseline.txt   # files fixed
   ```
   A lower TOTAL count is not sufficient evidence of safety — this exact
   area produced multiple attempts with a better count but real
   regressions hiding underneath. Zero new failures, always.
3. Once isolated `language/module-code` checks are clean, run BOTH full
   unfiltered sweeps before committing:
   - `mvn test -pl parent-js/js-test-test262 -Dtest=All262TranspilerTests`
     (transpiled, ~2+ hours — run via `nohup ... > log 2>&1 & disown`, poll
     via Monitor/background task rather than blocking)
   - `mvn test -pl parent-js/js-test-test262 -Dtest=Test262AllTest`
     (interpreted, ~13 min) — must stay at 0 failures; neither P1-P4 should
     touch interpreted-mode code paths at all, but verify per this
     project's established convention anyway.
   Diff the transpiled sweep's full failure list the same way as step 2.
4. Update `docs/GaltaJS/KnownGaps.md`: remove/rewrite the `language/module-code`
   cluster's "Import-hoisting order" section once genuinely fixed, updating
   the overall transpiled-mode failure count.
5. If ANY unexpected new failure appears at any point, stop and investigate
   rather than proceeding — do not rationalize a "probably fine" regression
   in this area. It has produced 5 genuinely distinct regression mechanisms
   across 7 attempts so far (slot allocation, source-map line tracking,
   self-import snapshot staleness, self-registration-without-liveness, and
   presumably more latent ones not yet triggered) — assume there are more
   until proven otherwise by a clean full sweep.

## Why this matters beyond `language/module-code`

Export liveness (P2) is very likely NOT limited to self-imports — any
transpiled module whose exported variable is reassigned after its initial
`export` statement, imported by ANYTHING (not just itself), would show the
same staleness. This session did not confirm whether any CURRENTLY-PASSING
test exercises "import a transpiled module's mutated export from a
different file" (plausibly rare in practice, since dependency modules
currently always load interpreted regardless of root mode — meaning the
only way to import FROM a transpiled unit at all is via a self/circular
reference back to the root, which is exactly the currently-broken case).
Worth an explicit check once P2 is designed, since fixing it might have
value/risk beyond just this one gap.
