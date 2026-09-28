# test262 known-gaps history (archived)

Archived fix narratives that formerly lived in `docs/GaltaJS/KnownGaps.md` - the
record of how the transpiled-mode module/import gaps, the async-scheduling
cluster, and several narrower bugs were diagnosed and fixed between 2026-08-22
and 2026-08-28. Kept verbatim for reference; **not maintained**. For what is
still open, see [Known ECMAScript Gaps](/GaltaJS/KnownGaps) - that document
only lists current, verified deviations.

---

### ~~`Object.preventExtensions(ClassName)` self-reference in its own static field initializer~~ - FIXED 2026-08-22

Was mis-diagnosed as a self-reference/binding-timing issue; root cause was
actually `BuiltinClassConstructor.addClassStaticField()`/`addClassField()`
defining fields via a non-throwing `setOwnProperty` (`DESC_CHECK.CHECK`) -
when the initializer itself made the class non-extensible, the define
silently failed and the field was just never added, instead of throwing
per spec's `CreateDataPropertyOrThrow`. Fixed by switching both to
`DESC_CHECK.STRICT`. No test262 file exercised this (confirmed via a
repo-wide grep for `preventExtensions` combined with a class `static`
field) - the fix's correctness is verified via source reading and a full
unfiltered sweep showing zero regressions, not a flipped test262 count.

Reproduce (now correctly throws, matching spec's `CreateDataPropertyOrThrow`):
```js
class C { static x = (Object.preventExtensions(C), C.name); }
C.x; // spec+GaltaJS (as of the fix): throws TypeError
```

---

### ~~`built-ins/RegExp/nullable-quantifier.js`~~ - FIXED 2026-08-23

Was: ECMA-262 RepeatMatcher step 2.a (an iteration that matched
ZERO-LENGTH must be treated as outright FAILURE, forcing backtrack,
rather than silently exiting the loop) wasn't implemented for the general
DYNAMIC case where the quantified body CAN also consume characters in
some branches. An EARLIER fix attempt (unconditionally changing Joni's
null-check opcodes in `ByteCodeMachine.java` to backtrack via `opFail()`)
fixed this file but broke Annex B's `QuantifiableAssertion` lazy
exact-count case (`/.(?=Z){2}?/` returning no match instead of matching) -
reverted at the time.

Root cause of why a blanket change breaks the exact-count case: RepeatMatcher
step 2.a's failure only applies once `min` has reached 0 for the CURRENT
iteration - a still-mandatory iteration below an exact/minimum count (e.g.
iteration 1 of `{2}`) must NOT fail on an empty match, it must just
proceed normally and keep counting toward the total. Every quantifier
COMPILE path except Joni's counting `OP_REPEAT`/`OP_REPEAT_INC` loop
already compiles any mandatory prefix iterations via a separate,
null-check-free `compileTreeNTimes()` call - so a null-check-wrapped body
reached from one of those paths is ALWAYS an optional (min-already-0)
iteration and unconditional `opFail()` is correct there. Only the counting
loop REUSES the same null-check-wrapped body across mandatory AND optional
iterations alike, needing a runtime distinction.

Fixed by adding `Regex.nullCheckRepeatMem` (a compile-time-populated
null-check-id -> REPEAT-id mapping, filled in only by
`ArrayCompiler.compileRangeRepeatNode()` - the one path needing it) and
`ByteCodeMachine.isMandatoryNullCheckIteration()` (consults the mapped
REPEAT's current iteration count vs. its lower bound at runtime): a
mandatory iteration's empty match is now a no-op (falls through normally,
letting `OP_REPEAT_INC` count it and continue the loop); an optional
iteration's empty match calls `opFail()` (backtrack, per spec); a
null-check with no mapping (every non-counting compile path) always calls
`opFail()` too, matching the "always optional" analysis above.

Verified via both the target file and a full `annexB/language/literals/
regexp` sweep (the directory that regressed under the earlier blanket
attempt) - both clean, zero regressions on a full interpreted-mode sweep.

---

## Transpiled mode: history and remaining gaps

Every gap count in this document before 2026-08-23 (the "11" ground truth,
and the "43" history before it) was established via `Test262AllTest` sweeps
run through `js-test-test262`'s `All262Tests` harness - which, until that
date, had a JUnit composition bug (`suite()` factories setting global mode
flags at suite-BUILD time instead of per-test via `runTest()`) that made its
3 nominal execution-mode passes (interpreted / interpreted+optimized /
transpiled) silently run IDENTICALLY. **Transpiled mode was therefore never
actually exercised for real by this harness before that date** - every prior
"clean"/"no known gaps" verification in this document (including the
"Modules" section above, and the full-suite ground-truth counts) only ever
really tested interpreted mode, regardless of what it claimed.

### Fixed: module-linking `StackOverflowError` (2026-08-23, commit `178a7ddb5`)

Once the harness bug was fixed and a real transpiled-mode sweep ran for the
first time, it surfaced 606 failures (vs. 43 for the same, contemporaneous
interpreted-mode sweep). The dominant pattern was a `StackOverflowError`:
`TranspiledGlobalRuntimeContext.importModule()` called the module resolver's
2-arg `loadModule()` overload (no early-registration callback), so a
circular/self-import reached from within a module's own body never found its
own already-registered-but-still-loading instance and recursed into loading
a second one from scratch, indefinitely. Fixed by bringing it to parity with
`InterpretedGlobalRuntimeContext.importModule()` (3-arg `loadModule()` with
an early-registration lambda + the same ERRORED/cycleRoot/pending-deferred
cache-hit checks) - module bodies always run via `JSInterpretedUnit`
regardless of whether the importing host program is transpiled, so this
needed the exact same handling, not an independently-drifted copy. Reduced
the full transpiled-mode sweep from 606 to 400 failures.

### Fixed: `import defer` / import-attributes unimplemented under transpiled mode (2026-08-25)

`JSGlobalContext` declares 5 default (no-op/throwing) methods -
`importAttributedModule`, `importDeferredNamespace`,
`gatherAsynchronousTransitiveDependencies`, `ensureModuleEvaluated`,
`startAsyncDependencyEvaluation`, plus `peekModule`/`registerRootModule` (2
more, needed for self-/circular-import detection) - that
`InterpretedGlobalRuntimeContext` overrides with real logic and
`TranspiledGlobalRuntimeContext` overrode with none of them. Ported all 7
verbatim (module bodies always run via the shared `JSInterpretedUnit`/
`JSModuleResolver` infrastructure regardless of root-program mode, so this
needed no transpiled-specific logic of its own) - see
`TranspiledGlobalRuntimeContext.java`. Also fixed 3 related bugs found while
verifying the port:
- `JSInterpretedUnit.requestedModuleReady()` hard-coded
  `instanceof InterpretedGlobalRuntimeContext` before its `peekModule()`
  readiness check, always optimistically returning "ready" under a
  transpiled global context - changed to a polymorphic `JSGlobalContext.
  peekModule()` call (now on the interface, default `null`).
- `JSInterpretedUnit.executeWithContext()`'s root-module self-registration
  (`registerRootModule()`/`setThis(UNDEFINED)`) was similarly gated to
  `InterpretedGlobalRuntimeContext` only - but a MODULE's body always
  executes via `JSInterpretedUnit` even when the *overall* program is
  running in transpiled mode (only non-module transpiled ROOT programs skip
  it), so a transpiled-mode-configured environment running a module
  dependency still needs this. Made polymorphic; added matching
  `registerRootModule()`/`setThis()` overrides to
  `TranspiledGlobalRuntimeContext` (the latter previously had none at all,
  meaning it silently fell through to `JSRuntimeContext`'s parent-delegating
  default - likely an NPE for any root context, not exercised before this
  fix touched this code path).
- `ASTImport.transpileJavaStatement()` (transpiled-root codegen) had zero
  handling of `isDeferred()`/import-attributes at all - a static `import
  defer * as ns from '...'`/`with {type:...}` at a transpiled root was
  silently evaluated eagerly/incorrectly. Added codegen mirroring
  `hoistBindings()`'s interpreted-mode branches, via a new shared
  `RuntimeUtil.importDeferredNamespaceSync()` helper (the synchronous
  gather-then-link sequence, factored out so interpreted and transpiled
  codegen share one implementation). Also found and fixed a second,
  unrelated pre-existing bug in the same method while verifying: named
  imports used `getExport()` instead of `getExportAccessor()`, which is what
  special-cases the literal `"default"` name - `import {default as x} from
  '...'` at a transpiled root threw "does not export an entry default" for
  any module whose default export isn't a namedExports entry (e.g. a JSON
  synthetic module) - test262 `json-idempotency.js`.

Reduced the full transpiled-mode sweep from 389 to 259 failures (348 to 229
distinct files, 119 fixed, zero regressions - every post-fix failure was
already present in the pre-fix baseline, confirmed by diffing the two full
sweeps' failure lists). `language/import` itself went from ~95 failures to
the 2 documented below.

### ~~Remaining, narrower gaps: transpiled ROOT that is itself a module~~ - BOTH FIXED

Originally two files, both specific to a transpiled *root* program that is
itself an ES module (not a dependency) - a different subsystem from the
above (`JSTranspiledUnit`'s per-statement codegen, not `JSInterpretedUnit`'s
shared module-loading machinery):

- `language/import/import-defer/errors/get-self-while-evaluating.js`:
  originally described here as failing because `JSTranspiledUnit` never
  registered a transpiled root module in the global context's `modules`
  cache. **Already fixed as a side effect of later session work** (the
  `language/module-code` live-bindings architecture entry below gives
  `JSTranspiledUnit.runValue()` real self-identity registration) - this
  entry was simply never updated. Re-verified passing via a full
  `language/import` sweep (2026-08-27/28).
- ~~`language/import/import-defer/evaluation-top-level-await/flattening-order/main.js`~~
  - **FIXED (2026-08-28)**, see the dedicated write-up right after this
    entry.

Not attempted note removed - both are done; see the write-up below for
the flattening-order fix specifically (the self-registration fix is
covered entirely by the earlier `language/module-code` entry).

**Fixed (2026-08-28)**: `flattening-order/main.js` - the last remaining
transpiled-mode gap for the whole session. `ASTProgram.
getModuleEvaluationOrder()` (a purely AST-level, compile-time
computation - no runtime dependency needed) already gives the exact
spec-correct interleaved order (ordinary imports in source position,
each `import defer`'s own transitively-gathered async/TLA dependencies
substituted in place) that `JSInterpretedUnit.linkModule()` already
walks correctly for an interpreted root or any module dependency
(dependencies are ALWAYS `JSInterpretedUnit` regardless of whether the
importing root is transpiled - only entry points are ahead-of-time
transpiled). The transpiled root's own codegen, however, already
processes its imports in that SAME source order (via the existing
`ASTImport.transpileEarlyImport()` hoisting loop `ASTProgram` runs for
every import) - `getModuleEvaluationOrder()` didn't need to be consulted
at all; the two remaining pieces were:
1. An `import defer` statement's own gathered async dependencies (via
   `JSGlobalContext.gatherAsynchronousTransitiveDependencies()`) were
   started (`startAsyncDependencyEvaluation()`) but never tracked into
   the `_pendingModuleDeps` list the async-scheduling 4th attempt fix
   (the day before, see its own entry above) added for ORDINARY imports
   only - so the root's own later top-level code (the test's final
   `assert.compareArray`) could run before these gathered pieces
   settled. Fixed by having
   `ASTImport`'s deferred-import codegen check each gathered dependency's
   `getModuleStatus()` and add any still-`EVALUATING` one to
   `_pendingModuleDeps`, exactly like the ordinary-import branch already
   does - reusing the SAME defer-and-restart-`_runValue()`-from-scratch
   mechanism, no new machinery needed.
2. Starting each gathered dependency (`startAsyncDependencyEvaluation()`)
   was reached synchronously with `draining==false` (no outer
   `runWithDrainSuppressed()` wrapping it, unlike interpreted mode's
   `linkModule()` which is wrapped by `executeWithContext()`) - hitting
   the exact same eager-full-drain issue `RuntimeUtil.importModule()`'s
   own wrap fixed the day before, but at a DIFFERENT call site this fix
   doesn't cover. Fixed by extracting `RuntimeUtil.
   importDeferredNamespaceSync()`'s gather+start sequence into a new
   `RuntimeUtil.gatherAndStartAsyncDependencies()` helper (returning the
   gathered list, for the caller to track pending ones) whose start loop
   is wrapped in `runWithDrainSuppressed()` - a no-op when already
   draining (interpreted mode, unaffected), a real fix when not
   (transpiled root, previously unwrapped). `importDeferredNamespaceSync()`
   itself now just calls this new helper, so interpreted mode's own
   `ASTImport.hoistBindings()` call site picks up the same protection for
   free, needing no separate change.

Verified via isolated `AdHocProbeTranspilerTests` sweeps of the
`flattening-order` directory alone (0 errors), the full `language/import`
directory (0 errors - `get-self-while-evaluating.js` included), and the
full `language/module-code` directory (0 errors - re-checked since this
touches the same shared `_pendingModuleDeps` mechanism), plus a full
interpreted-mode `Test262AllTest` sweep (0 failures - both touched
`RuntimeUtil` methods are shared code).

**Whole-session transpiled-mode total: 74 -> 0 failing files.** No known
gaps remain in `language/` or `built-ins/` transpiled-mode module/import
handling as of 2026-08-28 (a full unfiltered `All262TranspilerTests` run
across the ENTIRE suite was not completed this session due to an
unrelated, unusually heavy concurrent Nashorn test262 process on the same
machine making full sweeps unreliably slow - see the directory-scoped
sweeps above, which cover this fix's entire realistic blast radius).

### Fixed: `language/module-code` transpiled-mode live-bindings architecture (2026-08-25/26)

Seven prior investigation/fix attempts (all reverted, see git history) had
established that this needed genuine multi-part architecture work, not a
targeted bug fix - self-identity registration, live export bindings, a
default-export deferred-callback mechanism, and import hoisting were all
mutually dependent (fixing self-registration alone regressed 65->76 files,
since self-imports that used to accidentally work via a duplicate, freshly-
interpreted copy of the same file now found the REAL transpiled instance,
which had none of these capabilities).

**Implemented, in dependency order:**
- **Self-identity registration** - `JSTranspiledUnit.runValue()` now calls
  `gc.registerRootModule(this)` for a module-flagged root, mirroring
  `JSInterpretedUnit.executeWithContext()`'s identical call - a self-/
  circular-import reaching back to a transpiled root now finds that SAME
  instance instead of loading a fresh duplicate.
- **Live export bindings** - `AbstractModule.registerLiveExport()`/
  `getLiveExportedNames()` (a `Map<String,VarAccessor>`, checked first by
  `getExportAccessor()`) let `ASTProgram`'s hoist pass register every local
  declared export's `JSVarRef`-backed live accessor BEFORE the module body
  runs, so a self-import reaching a name before its own `export` statement's
  source position still resolves correctly - and a later plain reassignment
  of the exported variable stays visible (namespace `get`/property reads
  always indirect through the same accessor, not a one-time value snapshot).
- **Default-export deferred callback** - `JSTranspiledUnit.
  addDefaultExportCallback()`/`setDefaultExport()` override, ported
  verbatim from `JSInterpretedUnit`, for a self-import reaching a
  not-yet-executed non-hoistable default export.
- **Import hoisting** - `ASTImport` split into `transpileEarlyImport()`
  (namespace/default/deferred bindings, hoisted to the top of the module
  body by `ASTProgram`'s hoist pass, mirroring interpreted mode's
  `hoistBindings()`) and `transpileItemBindings()` (named items, which stay
  at their own normal source position - a self-import's target export may
  genuinely not exist yet at hoist time, unlike default/namespace which
  interpreted mode's own `unresolvedItems` retry mechanism doesn't need).
- **Static `resolveExport()` for cyclic/ambiguous resolution** - the above
  alone still failed 3 categories of test262 files once self-registration
  made a cyclic reference reach the real instance: (a) a genuine cycle
  where module A's indirect export resolves through B back into A for a
  name A hasn't source-reached yet (`instn-*-iee-cycle.js`) - the runtime
  bookkeeping above is only populated as each `export ... from` statement's
  own normal-position code runs, too late for a reentrant lookup; (b) star-
  export ambiguity identity (`ambiguous-export-bindings/*.js`) - two
  different immediate `export *` sources that both resolve to the SAME
  underlying binding must NOT be flagged ambiguous, but comparing immediate
  source-module identity (what the runtime bookkeeping does) can't tell the
  difference from two genuinely different sources; (c) bare `export * from`
  and anonymous default-export `.name` (NamedEvaluation) were entirely
  unimplemented in transpiled codegen, previously invisible because nothing
  had ever reached a transpiled module's own copy of them. Fixed by giving
  `JSTranspiledUnit` its own `resolveExport(name, resolveSet)` override -
  a line-for-line mirror of `JSInterpretedUnit.resolveExport()`'s spec
  15.2.1.16.3 algorithm (same resolveSet-based cycle guard, same local/
  default/indirect/star precedence) - driven by new static metadata
  (`registerIndirectExport()`/`registerStarExport()`) that `ASTProgram`'s
  hoist pass registers for every `export ... from` statement (self or
  cross-module alike - a self-reference simply resolves back to `this` via
  the module cache, no special-casing needed) as pure data, with zero
  `importModule()` side effect until an actual lookup needs to walk through
  it. `resolveModuleForRequest()` needed a `JSRuntimeContext` reachable
  regardless of whether this unit is executing as a root (`runValue()`
  called directly) or a dependency (`initModule()`, which also routes
  through `runValue()`) - the pre-existing `moduleContext` field is only
  ever set for the latter, so a new `activeContext` field, set by
  `runValue()` on every entry, is used instead.

**Verified via a full unfiltered transpiled-mode sweep** (`All262TranspilerTests`):
74 failing files (85 errors) -> 53 failing files (63 errors), 25 fixed (23 in
`language/module-code` + 2 bonus `dynamic-import` self-import fixes), diffed
file-by-file against the pre-change baseline - zero unexplained regressions.
A full unfiltered interpreted-mode sweep (`Test262AllTest`) stayed clean
(neither change touches interpreted-mode code paths). 4 files remain
regressed relative to the pre-change baseline, each precisely diagnosed as
a separate, narrower, pre-existing limitation that self-registration simply
stopped masking (same pattern as every item above and the two items in
"Remaining, narrower gaps" just above this section):

**Fixed (2026-08-27)**: `instn-named-iee-cycle.js` and
`eval-gtbndng-indirect-update{,-as}.js` - transpiled named-import bindings
were a one-time value snapshot, not a live `VarAccessor` the way
`CreateImportBinding` requires. Turned out NOT to need the module-level
`Object[]` array slot itself to become accessor-backed (a materially
larger change touching import codegen generally, as originally assessed)
- named imports are never reassignment TARGETS (only their SOURCE
module's own local variable is ever written), so only the READ side
needed to change. Fixed by having `ASTImport.markLiveImportBindings()`
mark each named item's own `VariableDef` (module specifier + export name
+ optional attributes type) as a live import binding, as pure compile-
time metadata registered in `ASTProgram`'s very first pre-pass (before
even variable declaration, so it applies even to a hoisted function
declared textually before its own import statement); `ASTIdentifier`'s
read-accessor codegen then substitutes a self-contained live
re-resolution expression (`importModule(...).getExportAccessor(name).
getValue()`, TDZ-checked) for the plain slot read, safe from any nested
closure since it only references `_ctx` and `RuntimeUtil`'s file-wide
static-imported helpers, no cached local state - `importModule()` is
cache-hit-cheap after the first real load, so re-resolving on every read
is correct, not just tolerable. The array slot itself is left as an
unused, harmless fallback for other array-slot-based machinery (e.g.
JSVarRef-based live-export registration, not applicable to a plain
import binding). Verified via directory-scoped sweeps covering the
complete `language/` and `built-ins/` subtrees (every top-level directory
individually - see the full-suite-reliability note below): zero
regressions.

**Fixed (2026-08-27)**: `eval-gtbndng-indirect-update-dflt.js` - the same
non-live-binding gap, for a DEFAULT import. A first attempt (extending
`markLiveImportBindings()` to mark a default import's `VariableDef` with
export name `"default"`, calling `getExportAccessor("default")`
unconditionally on every read, mirroring the named-item fix exactly)
fixed this file but REGRESSED 5 others: `instn-named-bndng-dflt-
{cls,expr,named,star}.js` and `top-level-await/module-self-import-async-
resolution-ticks.js`. Root cause: unlike a named local export (always
slot/accessor-backed via `registerLiveExport()`'s `JSVarRef`, so
`getExportAccessor()` correctly reflects TDZ even before the module is
"ready"), a default export is tracked as a plain boolean+value pair
(`hasDefaultExport()`/`getDefaultExport()`) with no such backing until
`hasDefaultExport()` is genuinely true - `transpileNonItemBindings()`'s
own `addDefaultExportCallback()` deferred-callback mechanism exists
SPECIFICALLY to handle a circular self-import reading its own module's
default before that module's `export default` statement has run, keeping
the array SLOT correctly updated once ready; the naive live-read
expression bypassed that slot (and therefore the callback's effect)
entirely, calling `getExportAccessor("default")` directly, which throws
for a module that hasn't set its default yet instead of falling back.
Reverted that attempt, then fixed properly: the live-read expression for
a default import now checks `hasDefaultExport()` first - reading live via
`getExportAccessor("default")` when true, falling back to the plain array
slot (which `addDefaultExportCallback()` keeps correctly updated for
exactly the not-yet-ready case) when false. Verified via the same
directory-scoped sweep coverage as the named-item fix, PLUS individually
re-confirming all 5 previously-regressed files now pass: zero
regressions.
**Fixed (2026-08-27)**: `ambiguous-export-bindings/namespace-unambiguous-
if-export-star-as-from-and-import-star-as-and-export.js` and
`verify-dfs.js` (see also its own earlier entry above) - a self-/sibling-
import reached a DIFFERENT test262 file loaded as a module dependency,
whose own top-level `assert.sameValue(...)`/`.sameValue` call then failed
("Left part of member sameValue is undefined" / "Method sameValue is not
a callable"). Root cause: `Test262BaseTest.combineShellAndScript()`
compiled the shell (`sta.js`/`assert.js`) and the test's own text as ONE
combined Java class; for a `flags:[module]` test, the whole combined text
compiled as ONE MODULE, so harness PREAMBLE code (never meant to be part
of any module) became subject to "runs after this module's own import
statements" semantics - import-hoisting correctly runs before any body
statement regardless of source position, so a module's own imports could
run (and call back into a sibling/self reference) before the harness's
own `assert.sameValue = function(){...}` assignment had executed at all.
Interpreted mode never hit this since its shell runs as a genuinely
SEPARATE, already-fully-executed script unit before the test's own script
(`BaseTestSuiteTest.loadShell()`).

Fixed by extracting `execFile()`'s inline {parse, transpile, compile,
load, construct, runValue} sequence into a reusable
`compileAndRunTranspiledUnit()` helper, then calling it TWICE for
`flags:[module]` tests specifically - once for the shell as a plain
non-module script, once for the test's own text - against a shared
`TranspiledGlobalRuntimeContext`, mirroring `loadShell()`. The non-module
path is unchanged, just calling the same helper once with the combined
text as before.

A first attempt at this exact change appeared to trade the timing bug for
a NEW "assert itself is undefined" symptom and was reverted as unsafe,
suspecting a deeper cross-class-global-visibility problem. Root-caused
precisely on a second attempt: the suspected deeper problem didn't exist
- the reverted attempt had a plain ordering bug (`getScriptFlags()` was
called BEFORE `loadScript()`, which is what populates the metadata
`getScriptFlags()` reads via `preprocessFile()` - so `SCRIPT_MODULE` was
never detected and the split branch silently never ran at all, silently
falling through to the ordinary combined path; the "new" symptom was
therefore unrelated to the split and had already shifted naturally over
the course of this session's other fixes). Calling `loadScript()` first
resolved this, and the split works correctly - global visibility across
the two separately-compiled classes was never actually broken. Verified
via directory-scoped sweeps covering every top-level `language/`
directory and a complete `built-ins/` sweep: zero regressions.
**Fixed (2026-08-27)**: `import-defer/errors/get-other-while-evaluating/
main.js` and `get-other-while-dep-evaluating/main.js` - a deferred
namespace access (`ns.prop`) reaching (directly or transitively) back to
a module that's still evaluating must throw `TypeError`
(`EnsureDeferredNamespaceEvaluation`/`ReadyForSyncExecution`, spec
10.4.6.8) - it didn't, for a transpiled root.

An earlier version of this entry claimed "GaltaJS has no real module
status tracking" - imprecise. `JSInterpretedUnit` already has this fully
working (`ModuleStatus` enum + `readyForSyncExecution()`'s cycle-aware
dependency walk, including `getCycleRootOrSelf()`'s Tarjan-style SCC
merging via `linkModule()`'s own DFS-based linking algorithm) - it's WHY
interpreted mode passes these exact tests. The REAL, narrower bug: `Tra
nspiledGlobalRuntimeContext.importDeferredNamespace()` special-cased a
cached-but-not-`JSInterpretedUnit` target to bypass deferred-evaluation
wrapping entirely, returning the plain namespace object directly -
silently skipping the whole check for a self-/sibling-referencing
`JSTranspiledUnit` target instead of throwing.

Fixed WITHOUT porting the full SCC/cycle-root algorithm (confirmed
`getCycleRootOrSelf()`'s `cycleRoot`/`DFSIndex`/`DFSAncestorIndex` fields
are entirely private to `JSInterpretedUnit`, with no shared abstraction -
genericizing THAT remains out of scope, a substantial separate
undertaking in the same risk category as this codebase's own
`JSAsyncExecutor` coroutine redesign). Instead, gave `JSTranspiledUnit` a
DELIBERATELY NARROWER `ModuleEvalStatus` (UNLINKED/EVALUATING/EVALUATED/
ERRORED, no cycle-root merging) + `readyForSyncExecution()` - correct for
a direct self-reference or a simple sibling-dependency chain (exactly
what these 2 tests exercise), not for a genuine multi-module SCC where
the checked member differs from its cycle's root. The module-request list
`readyForSyncExecution()` walks is baked at transpile time from
`ASTProgram.getAllModuleRequests()` (a pure AST-structure computation,
already existed for `eval-rqstd-order.js`'s own hoisting) into a
`registerModuleRequests(...)` call, since `JSTranspiledUnit` retains no
AST at runtime to compute it from directly.

Wiring: `DeferredModuleView` widened from holding a `JSInterpretedUnit` to
a generic `JSModule` (its own delegate methods were already pure
`JSModule`-interface calls, so this needed no logic changes at all) so it
can wrap either module kind; `JSGlobalContext.peekModule()` widened the
same mechanical way (its body only ever used `JSModule.getDescriptor()`).
`ensureModuleEvaluated()`/`startAsyncDependencyEvaluation()` widened via
an ADDITIVE `instanceof JSTranspiledUnit` branch, preserving the existing
`JSInterpretedUnit` logic exactly unchanged. Also fixed a related,
previously-latent bug this surfaced: `JSInterpretedUnit.
requestedModuleReady()`'s fallback treated ANY non-`JSInterpretedUnit`
module as "always instantly ready" - correct for a genuinely instant-
construction native/JSON/text module, silently WRONG for a
`JSTranspiledUnit` now that it has real evaluation phases too; added the
same `instanceof`-dispatch there.

The remaining `import-defer`/`top-level-await` files (`flattening-order/
main.js` + the 3 TLA files) still need the FULL async/TLA scheduling
semantics this narrower mechanism deliberately doesn't attempt - any
async-executing module is unconditionally treated as never sync-ready
(mirroring spec's own conservative `[[HasTLA]]` fallback), matching
`JSInterpretedUnit`'s identical documented limitation. Verified via a full
interpreted-mode sweep (0 failures - this change touches shared
`JSInterpretedUnit`/`JSGlobalContext` code) plus directory-scoped
transpiled sweeps covering every top-level `language/` directory and a
complete `built-ins/` sweep: zero regressions.

**Three more fixes (2026-08-26), found by continuing to triage the
`language/module-code` cluster's remaining (pre-existing, not caused by the
above) failures now that self-/circular resolution actually works:**

- **Module-level `var` leaking onto the real global object.**
  `ASTVarContainer.transpilerDeclareStatement()`'s `global` flag (gates the
  `initGlobalVariables()` call that synchronizes top-level `var`/function
  declarations onto `globalThis`) was `jsContext instanceof
  TranspilerGeneratorMainContext` alone - true for the outermost declare
  call of BOTH a script and a module (both compile through the same
  `MainContext`), never distinguishing the two. Per spec,
  GlobalDeclarationInstantiation only runs for script/eval code, never
  module code (which gets its own separate InitializeEnvironment instead) -
  so a module's own `var x` was wrongly becoming a real, deletable
  `globalThis.x` property (test262 `instn-local-bndng-var.js`:
  `Object.getOwnPropertyDescriptor(globalObj, 'x')` must stay `undefined`
  even after the module's own `var x` runs). Fixed by also excluding
  `this instanceof ASTProgram program && program.isModule()`. Fixed 9
  files (`instn-local-bndng-{var,for,fun,gen,export-var}.js`,
  `instn-named-bndng-{dflt-fun-anon,dflt-gen-anon}.js`,
  `eval-export-dflt-{fun,gen}-anon-semi.js`).
- **Named import bindings never attempted at hoist time.** P4 (above)
  deliberately hoisted only namespace/default/deferred import bindings,
  leaving named items (`import {a,b} from '...'`) entirely at their own
  normal source position - safe but incomplete: a CROSS-module named
  import (not a self-/circular reference, where the source's own matching
  export genuinely might not exist yet) is always safe to resolve early,
  same as any other import form, since module linking fully evaluates a
  dependency before the importing module's own top-level code runs
  regardless of where the `import` statement sits in source (test262
  `instn-iee-bndng-var.js`: code reading the imported name BEFORE the
  `import` statement's own line, importing a plain `var` from a different
  file). Fixed by adding `ASTImport.transpileEarlyItemBindings()` - the
  same per-item `getExportAccessor().getValue()` read `transpileItemBindings()`
  already does at normal position, just attempted early too, wrapped in a
  single runtime try/catch spanning the whole clause: if resolution fails
  this early (the self-/circular case), it's silently skipped - the SAME
  existing normal-position code (always still run, whether or not the
  early attempt succeeded) resolves it correctly there instead, exactly as
  before this addition existed. Mirrors interpreted mode's own
  `hoistBindings()`/`unresolvedItems` retry mechanism. Fixed 4 files
  (`instn-iee-bndng-var.js`, `instn-named-bndng-{var,dflt-named,trlng-comma}.js`).
- **A module's `this` was never bound to `undefined`.** Unlike
  `JSInterpretedUnit.executeWithContext()` (which calls
  `gc.setThis(RuntimeUtil.UNDEFINED)` for a module root, overriding the
  constructor's own "no `this` specified, default to globalThis" fallback
  that's correct for a SCRIPT but not a module), `JSTranspiledUnit.
  runValue()` had no equivalent call at all - a transpiled module's
  top-level `this` fell through to the script-shaped default (test262
  `eval-this.js`: `assert.sameValue(this, undefined)`). Fixed by adding the
  identical call alongside `registerRootModule()`. Fixed 1 file.

A full unfiltered transpiled-mode sweep confirms these three fixed 14 files
(53 -> 39 failing, 63 -> 49 errors), zero regressions, diffed against the
53-file post-architecture-work baseline above.

**A fourth fix, same day**: `export default function F(){}` (and generator/
async-function/async-generator forms) - `F.foo = '';` right after the
declaration threw "Left part of member foo is undefined". Per spec, a
NAMED `HoistableDeclaration` default export also binds its OWN name within
the module (not just "default"), same as any other named declaration -
`ASTExport.transpileJavaStatement()`'s `defaultExport!=null` branch only
ever constructed the function value via expression-mode codegen
(`JSTranspiler.asValue()`/`transpileJavaExpression()`) and stored it as the
"default" export, never assigning it into `F`'s own module-level variable
slot at all (same category of bug as the earlier `export let X;` fix - the
right dispatch for a declaration with slot-assignment side effects is
`transpileJavaStatement()`, not expression mode). Fixed by dispatching a
`HoistableDeclaration` default export (named or anonymous alike - both
have a real slot, see `isHoistableDefaultExport()`'s own doc comment)
through `ASTFunctionDecl.transpileJavaStatement()` instead (which already
assigns correctly), then reading that SAME slot back for the "default"
alias - mirroring `ASTExport.evaluate()`'s own interpreted-mode read-back
instead of constructing a second, separate function instance. Fixed 4
files (`export-default-{function,generator,asyncfunction,asyncgenerator}-
declaration-binding.js`).

A full unfiltered transpiled-mode sweep confirms this fixed all 4 files,
zero regressions. Combined, the session total is 74 -> 35 failing files
(85 -> 45 errors). Interpreted mode stayed clean throughout every fix in
this section.

**Remaining ~35 failures, triaged into groups** (not individually fixed -
see each group's own root-cause note):
- The 4 already-diagnosed items above (non-live named imports, the
  sibling-test-as-module `assert` visibility edge case, and the 2
  deferred-namespace `[[Status]]`-tracking files) plus `get-self-while-
  evaluating.js`/`flattening-order/main.js` from "Remaining, narrower gaps"
  above - all pre-existing, independently diagnosed, genuinely deep gaps.
- **Non-live named imports, more instances**: `eval-gtbndng-indirect-
  update{,-as,-dflt}.js` (3 files) - same root cause as `instn-named-iee-
  cycle.js` above (a global function mutates the SOURCE module's exported
  variable after the importing module already took its one-time value
  snapshot; the import must observe the new value and doesn't).
**Fixed (2026-08-26)**: `instn-iee-bndng-{fun,gen}.js`/`instn-named-bndng-
{fun,gen}.js` - the "hoistable-function/class value not yet assigned at
hoist-registration time" gap noted above, previously flagged as a 3x-proven
regression trap and left unattempted. Root cause was exactly as diagnosed:
transpiled function hoisting is pure STATEMENT REORDERING (`HoistableNode`/
`ASTStatementList.hoistNodes()`), with no separate "assign the real value
early" pass the way interpreted mode's `ASTFunctionDecl.hoistValue()` is -
so a hoistable function/generator export's live accessor, read early by
`transpileEarlyItemBindings()`/an eagerly-triggered dependency, permanently
observed a placeholder. Fixed WITHOUT touching `HoistableNode`/
`hoistNodes()`/slot allocation at all (the exact mechanism the 3 prior
reverted attempts regressed by touching): added `ASTFunctionDecl.
transpileHoistValue()`, an additional, guarded early emission of the SAME
assignment codegen the declaration's own (reordered-to-front but still
statement-driven) normal position already uses, called from `ASTProgram`'s
own module-level hoist emission BEFORE the import-hoisting block - mirrors
interpreted mode's `hoistDeclarations()` call-site ordering exactly (bare
function, `export function`, `export default function`). `earlyHoistedTranspiled`
(a new, transpiled-only field, kept separate from interpreted mode's own
`earlyHoisted`) guards the later normal-position pass from re-emitting.
Full sweep confirms zero regressions: 19 -> 15 failing files (24 -> 19
errors).

As a side effect, root-caused `verify-dfs.js`'s OWN top-level symptom
precisely: it's a manifestation of this SAME gap, not a separate DFS-
ordering issue (its `check`/`evaluated` functions are read back through a
self-/circular import from `verify-dfs-a_FIXTURE.js`, exactly the pattern
above). The "Function does not exist, check" error is now gone, but the
file still failed with a NEW symptom: `Test262:AsyncTestFailure:TypeError:
Method sameValue is not a callable, undefined` inside `check()`'s own
`promise.then(() => { assert.sameValue(...) })` callback.

**Root-caused (2026-08-27), CONFIRMED test-harness limitation** (same
category and root mechanism as the `ambiguous-export-bindings` gap below,
not a distinct issue): inspected the actual generated Java source
(`compiled/language_module_code_verify_dfs.java`) directly. The read of
`assert` inside the callback is correct (`p_0[6]/*assert*/`, a plain
direct slot reference into root's own array - no codegen bug there).
The bug is in WHEN `assert.sameValue = function(...){...}` (part of the
`assert.js` HARNESS text, prepended by `combineShellAndScript()`) gets
assigned relative to WHEN `verify-dfs-a_FIXTURE.js` gets loaded: that
assignment is a plain property-assignment STATEMENT (not a hoistable
function declaration, so NOT covered by this fix's own early-value pass),
so it only runs at its own normal source position - but `import
'./verify-dfs-a_FIXTURE.js';` (part of the TEST's own text, appended
AFTER the harness text by the same `combineShellAndScript()`) is
early-hoisted (correctly, per spec - P4's own import-hoisting design)
to run BEFORE ANY body statement, including `assert.sameValue = ...`
despite that assignment being TEXTUALLY EARLIER in the combined file. The
FIXTURE's own top-level code calls `evaluated('A')`/registers the
`.then()` callback before `assert.sameValue` has been assigned at all.
This is not a transpiler bug: per spec, `import` declarations genuinely
must be evaluated before ANY of a module's own top-level code runs,
regardless of source position - a REAL standalone module with this exact
statement order would hit the identical issue. It only surfaces here
because `combineShellAndScript()` splices harness PREAMBLE code (never
meant to be subject to "runs after imports" module semantics) together
with the test's own import statements into ONE compiled unit. Interpreted
mode never hits this because its shell runs as a genuinely SEPARATE,
already-fully-executed script unit BEFORE the test's own script starts
(`BaseTestSuiteTest.loadShell()`) - the exact same asymmetry documented
for `ambiguous-export-bindings` below. **Fixed (2026-08-27)** together
with that entry - see its own account of the harness fix (running the
shell as a genuinely separate unit for `flags:[module]` tests). Moved out
of the DFS-ordering group below since its top-level symptom was never the
DFS-ordering one.
- ~~**Async/dynamic-import DFS evaluation-order edge cases**~~ - **FIXED
  (2026-08-27, 4th attempt)** - see the dedicated entry right after this
  bullet's own history for the fix that finally worked, a 4th attempt
  simpler than the 3rd's reverted full DFS/SCC port. Original description,
  kept for history: `top-level-
  await/{async-module-does-not-block-sibling-modules,module-async-import-
  async-resolution-ticks,module-import-unwrapped}.js` (3 files) -
  genuinely complex async-module-graph-ordering/`[[Status]]` tests.
  (`verify-dfs.js` moved out of this group - see the fix note above.)
  Triaged to concrete symptoms this session (still not fixed - `Module
  EvalStatus`/`readyForSyncExecution()` now exists on `JSTranspiledUnit`
  (see the `import-defer` fix above), but these 3 files need the FULL
  async/TLA scheduling semantics that mechanism deliberately doesn't
  attempt - it unconditionally treats any async-executing module as never
  sync-ready, matching spec's own conservative `[[HasTLA]]` fallback, so
  it can't help with tick-precise sibling-evaluation ordering or default-
  export-as-unresolved-promise wrapping):
  - **A real fix was attempted and empirically confirmed to net-regress
    (2026-08-27), not just assumed too risky**: root-caused
    `async-module-does-not-block-sibling-modules.js` precisely by tracing
    `JSAsyncExecutor.execute()`'s async branch - when a dependency
    module's own `execute(supplier,true)` call is reached synchronously
    (a static import, outside any coroutine's own drain loop, e.g. from a
    transpiled root module's own generated import sequence, or from
    interpreted mode's `linkModule()`), and `draining` is `false` at that
    point, `execute()`'s own tail unconditionally calls
    `drainPendingTasks()` before returning - running the dependency's
    ENTIRE async body (including everything past its first `await`) to
    completion before control ever returns to import the NEXT sibling.
    Interpreted mode already has the fix for exactly this
    (`JSInterpretedUnit.executeWithContext()` wraps its whole `linkModule(
    context)` dependency-walk in `gc.getExecutor().
    runWithDrainSuppressed(...)`, so a nested dependency's own
    `execute(true)` call sees `draining==true` and correctly defers to the
    true outermost drain instead) - transpiled mode has no equivalent,
    since its import calls are inlined generated statements, not a
    separate linking method. Implemented the equivalent for transpiled
    mode two ways, both built and verified via an isolated `AdHocProbeTranspilerTests`
    sweep of this whole directory: (1) wrapping `JSTranspiledUnit.
    runValue()`'s entire `_runValue(context)` call in
    `runWithDrainSuppressed()`, and (2) the narrower `JSInterpretedUnit`-
    style scoping, wrapping just `RuntimeUtil.importModule()`'s single
    delegate call (the common entry point both modes' import resolution
    routes through) instead. **Both fixed the targeted test (confirmed:
    `async-module-does-not-block-sibling-modules.js` disappeared from the
    failure list) but both, identically, newly broke 4 other tests in the
    same directory**: `dfs-invariant.js`, `module-import-resolution.js`,
    `module-sync-import-async-resolution-ticks.js`, and `pending-async-
    dep-from-cycle.js` (net: -1 fixed, +4 broken, both attempts producing
    the exact same 11-error/6-file result) - these evidently rely on the
    CURRENT eager-drain-to-completion behavior for their own correctness,
    meaning the two behaviors are in genuine tension under the engine's
    current single-flag (`draining`) scheduling model, not just an
    unproven-but-narrow gap. Both attempts were fully reverted (confirmed
    via a follow-up isolated sweep back to the original 3-file/6-error
    baseline, and `git status`/`git diff` showing zero leftover changes in
    the touched files). This is offered as a genuine (not assumed) data
    point that this specific cluster needs real, holistic async/TLA
    scheduling work - most likely tracking per-module (or per-dependency-
    edge) drain-readiness instead of one process-wide `draining` boolean -
    rather than a wiring-level fix, matching this file's own long-
    standing caution about the `JSAsyncExecutor` coroutine machinery's
    regression history.
  - **A THIRD, much more complete attempt (2026-08-27, same day) actually
    implemented the "real per-module drain-readiness" architecture the
    entry above called for - and got substantially further (3 of 4
    target files genuinely fixed) before finding a DIFFERENT, deeper
    architectural blocker that makes this specific implementation
    strategy a dead end.** Ported `JSInterpretedUnit`'s own spec-accurate
    `linkModule()`/`notifyAsyncParents()` algorithm (DFS/SCC cycle-root
    detection + a real `pendingAsyncDependencies` counter that DEFERS a
    module's own body until every async dependency genuinely settles, not
    just a draining-suppression wrapper) into `JSTranspiledUnit`, called
    from `runValue()` BEFORE `_runValue()` (the generated body) ever
    runs. Cross-kind dependencies (a transpiled unit's dependencies are
    ALWAYS `JSInterpretedUnit` in practice - modules aren't ahead-of-time
    transpiled per file, only the entry point is - confirmed by direct
    debug output, not assumed) interoperate cleanly through
    `JSInterpretedUnit`'s own EXISTING, generic, public API
    (`isAsyncEvaluation()`/`getModuleStatus()`/
    `addEvaluationCompletionCallback(Runnable)`) with zero changes needed
    to that class. Two real implementation bugs were found and fixed
    along the way (both instructive): (1) `allModuleRequests` needed to
    switch from `getAllModuleRequests()` (a flat specifier set that also
    includes `import defer` and import-attribute specifiers) to
    `getModuleEvaluationOrder()` (which excludes attributed specifiers
    entirely and tags each remaining one `deferred` or not) - the first
    version eagerly force-evaluated every deferred module up front and
    mis-resolved attributed specifiers through the wrong loader, breaking
    70+ otherwise-unrelated `import-defer`/`import-attributes` tests
    before this fix; (2) `registerModuleRequests()` needed to move from
    being the first statement inside `_runValue()` (too late - the new
    `linkModule()` call reads it BEFORE `_runValue()` ever starts) to the
    generated constructor. After both fixes, the full `top-level-await`
    directory passed with ZERO errors - all 3 non-defer target files
    fixed simultaneously, including `async-module-does-not-block-sibling-
    modules.js`, WITHOUT regressing `dfs-invariant.js`,
    `module-import-resolution.js`, `module-sync-import-async-resolution-
    ticks.js`, or `pending-async-dep-from-cycle.js` (the exact 4 files
    the two narrower attempts above regressed) - confirming the "real
    per-module drain-readiness" diagnosis from the prior attempts was
    correct.
    **The actual, fatal blocker, found during the full-suite regression
    sweep**: `verify-dfs.js` (a self-/circular-import test, already fixed
    once this session via the harness-split-shell work - see its own
    entry above) regressed with a NEW error, "does not export named
    entries." Root cause: `AbstractModule.registerLiveExport()` calls
    (emitted by `ASTProgram.transpileJavaStatement()` as the FIRST
    substantive statements inside `_runValue()`, specifically so a self-
    import reaching a name before its own `export` statement's source
    position can still resolve it - see that emission's own doc comment)
    reference the module's local variable-storage array (`p_0`/`p_1`/etc,
    e.g. `registerLiveExport("x", JSVarRef.of(p_0, 3, VAR_TYPE.LET))`) -
    and that array is allocated as a LOCAL variable inside `_runValue()`
    itself (`final Object[] p_0 = new Object[N];`), not a field. Calling
    `linkModule()` BEFORE `_runValue()` starts means live-export
    registration hasn't run yet either - so a fixture module, eagerly
    loaded by the new pre-resolution pass, that imports back the
    currently-linking root (exactly what `verify-dfs.js`'s own fixture
    does) finds no exports registered at all. **Fixing this properly
    would require promoting every module's variable-storage arrays from
    `_runValue()`-local variables to instance fields on the generated
    class**, so that a NEW, separately-callable generated method (holding
    just variable declaration + live-export registration, extracted from
    the front of `_runValue()`) could run before `linkModule()` while the
    REST of `_runValue()` (imports + body) still has access to the same
    slots afterward. That is a fundamentally larger, higher-risk change
    than module-linking itself - it touches how EVERY transpiled module
    represents ALL of its local state, not just its dependency graph -
    and was assessed as out of scope for this session (well beyond what
    was authorized, and in exactly the kind of core-representation
    territory this codebase's own history warns is expensive to get
    wrong). Reverted cleanly (confirmed via `git status`/`git diff`
    showing zero leftover changes, and a follow-up isolated sweep of
    `language/module-code` showing the EXACT original 3-file/6-error
    baseline restored, `verify-dfs.js` included). **Concrete, precise
    roadmap for a future attempt, if one is ever undertaken**: (1)
    promote per-module variable-storage arrays to instance fields; (2)
    split `ASTProgram.transpileJavaStatement()`'s generated output into
    two methods - "declare + hoist + register live exports" (call it
    `_declareModule()`, called unconditionally and immediately) and
    "everything else" (`_runValue()`, called only once
    `linkModule()`-computed readiness allows it); (3) re-apply the
    `linkModule()`/`notifyAsyncParents()`/`JSInterpretedUnit`-interop work
    already fully designed and verified-in-isolation by this attempt
    (correct on its own terms, confirmed by the clean `top-level-await`
    pass with zero of the previous 4 regressions) - none of that part
    needs to be redesigned, only re-sequenced relative to the new
    `_declareModule()` split.
  - `async-module-does-not-block-sibling-modules.js`: `assert.sameValue(
    check, false)` fails with `SameValue(true, false)` - `check` (from a
    SYNC sibling module) should observe `false` at the point this
    statement runs (per spec, a sibling module must be evaluated while an
    async dependency is still awaiting its promise), but GaltaJS's
    executor evaluates the sync sibling too late, after whatever sets
    `check` back to `true`.
  - `module-import-unwrapped.js`: `Module ./module-import-unwrapped_
    FIXTURE.js does not have a default export` (a genuine thrown
    exception, not an assertion failure) - the test's own premise is that
    importing a NAMED/default binding from a module that's still
    mid-async-evaluation (TLA, not yet settled) should yield the binding
    still wrapped as an unresolved Promise (a legacy/transitional TLA
    behavior the spec explicitly tests for), but GaltaJS's
    `getDefaultExport()`/`hasDefaultExport()` has no such wrapping concept
    at all and just throws outright when the real value isn't there yet.
**Fixed (2026-08-27)**: async-scheduling DFS-ordering cluster, 4th
attempt. The 3rd attempt above got 3 of 4 target files genuinely fixed before
hitting a fatal blocker (live-export registration needing a
`_runValue()`-local variable array that doesn't exist until the body
starts, conflicting with a pre-`_runValue()` linking phase) and was fully
reverted. This 4th attempt fixes the same 3 files via a materially
simpler mechanism that never needs anything to run before `_runValue()`
starts at all - avoiding that blocker by construction rather than working
around it.

**The mechanism** (three small, cooperating pieces, no field promotion,
no separate linking pass):

1. `RuntimeUtil.importModule()` wraps its delegate call in
   `gc.getExecutor().runWithDrainSuppressed(...)` - the same fix
   identified (and, alone, correctly found insufficient) by the first two
   attempts documented above: without it, a dependency's own nested
   `execute(supplier,true)` call eagerly drains the ENTIRE microtask queue
   before returning, running the dependency's whole async body to
   completion before control returns to import the next sibling.
2. `ASTProgram.transpileJavaStatement()`'s existing import-hoisting loop
   (the one calling `ASTImport.transpileEarlyImport()` for every import in
   source order) now also declares a `_pendingModuleDeps` list before the
   loop and, after it, checks: if anything was added, call
   `deferModuleUntilSettled(...)` and `return;`. `ASTImport.
   transpileNonItemBindings()` adds to that list, for EVERY import form
   (bare, default, namespace alike), whenever the resolved module is a
   still-`EVALUATING` `JSInterpretedUnit`. Deciding this ONLY ONCE, after
   every import has already had its own `importModule()` triggered, is
   the crux of why this doesn't just regress back to the first two
   attempts' failure mode: a naive per-import `return;` the moment ONE
   pending dependency is found would skip triggering any LATER sibling
   import's own `importModule()` call - which is exactly what
   `async-module-does-not-block-sibling-modules.js` requires NOT to
   happen (its second import, of an unrelated sync module, must still run
   while the first import's async dependency is still pending). Bare
   imports (no binding at all) needed the SAME treatment as default
   imports for a different reason: `dfs-invariant.js` and
   `pending-async-dep-from-cycle.js` have no import bindings to observe,
   but still need every transitive async dependency fully settled before
   their own final `assert` runs - exactly the case the first two
   attempts' drain-suppression-only fix broke (confirmed by reproducing
   that exact regression here before generalizing past the default-import-
   only version of this same idea).
3. `JSTranspiledUnit.deferModuleUntilSettled(context, List<JSInterpretedUnit>
   deps)` registers a completion callback (via `JSInterpretedUnit`'s own
   existing, generic `addEvaluationCompletionCallback()` - zero changes
   needed there) on every entry in `deps`, with a plain countdown so two
   dependencies settling in the same tick trigger exactly ONE retry, not
   two (which would double any of the module's own side effects). Once
   the countdown reaches zero, it calls a new `runOnce(context)` (`_runValue()`
   plus completion/error bookkeeping, factored out of `runValue()`
   unchanged) again, FROM SCRATCH. Restarting from the top is safe
   specifically because nothing observable can have happened yet at the
   point a module defers: only variable declaration, live-export
   registration, and imports precede any import statement, and all three
   are idempotent/re-computed identically on retry - the exact property
   the 3rd attempt's blocker would have violated if this design had tried
   to resume mid-method instead.

**Verification**: isolated `AdHocProbeTranspilerTests` sweeps of
`language/module-code` (0 errors, full directory - both the 3 target
files AND the 4 files the naive drain-suppression-only version of this
fix regressed, `dfs-invariant.js`/`module-import-resolution.js`/
`module-sync-import-async-resolution-ticks.js`/`pending-async-dep-from-
cycle.js`, all clean) and `language/import` (only the pre-existing,
untouched `flattening-order/main.js` failure remains - see its own entry
below, a genuinely separate gap this mechanism doesn't attempt), plus a
full interpreted-mode `Test262AllTest` sweep (0 failures - `RuntimeUtil.
importModule()` is shared code, used by `JSInterpretedUnit` too). A full
unfiltered transpiled-mode sweep was not completed this session (an
unrelated, unusually heavy concurrent Nashorn test262 run on the same
machine made full sweeps unreliably slow); the directory-scoped sweeps
above were chosen instead because this change's actual codegen footprint
(an always-declared, always-empty-unless-imports-exist local list plus a
trivial guard) is inert for any file with no `ASTImport` nodes, so the
two import/module-focused directories above cover its entire realistic
blast radius.

The one file this doesn't fix is `import-defer/evaluation-top-level-await/
flattening-order/main.js` - see its own entry below (and the running
session-total tracking further down, updated to reflect this fix); it
needs `import defer`'s own additional interleaved-ordering semantics
(`GatherAsyncParentCompletions`-style flattening across MULTIPLE deferred
namespace imports), a distinct mechanism from the single-module defer-
and-retry this fix implements.

**Fixed (2026-08-26)**: `eval-rqstd-order.js` - "`export ... from`
dependencies aren't hoisted the way `import` statements are." Two prior
attempts this same day: first attempt (interleave an early
`importModule()` trigger for export-from into the SAME hoist loop as
`ASTImport.transpileEarlyImport()`) fixed this file but REGRESSED
`namespace/internals/own-property-keys-binding-types.js` (early-triggering
a circular `export * from` source forced resolution through `ASTExport.
evaluate()`'s "existence confirmed but deferred" catch path, which
registers a live re-export accessor but never populates the source's
`namedExports` snapshot - and `mergeStarReExports()` only ever enumerated
that snapshot) - reverted. Second attempt was blocked on a prerequisite:
the early trigger is only safe once transpiled mode's hoistable function
declarations already have their real values first (same reasoning as
interpreted mode's `hoistDependencyEvaluation()`) - see the "Hoistable-
function/class value not yet assigned" fix above, which solved this.
Fixed by combining both pieces: (1) `mergeStarReExports()` now also walks
`sourceModule.getLiveExportedNames()` for any name missing from the
snapshot, resolving it via `getExportAccessor()` wrapped in `RuntimeUtil.
checkTDZ()` (skip-and-retry-later on failure via the existing settle-
callback re-run), closing the `own-property-keys-binding-types.js` gap;
(2) `ASTExport.transpileHoistDependencyEvaluation()` re-added the early,
side-effect-only module-resolution trigger, emitted from `ASTProgram`'s
import-hoisting loop in the same source-order pass as `ASTImport`'s own
early trigger. Verified via directory-scoped sweeps covering the full
`language/` and `built-ins/` subtrees (see the full-suite-reliability note
below): zero regressions, including `own-property-keys-binding-types.js`
staying clean this time.
**Fixed (2026-08-26)**: module-top-level `using`/`await-using` disposal in
transpiled codegen. `ASTProgram.transpileJavaStatement()` always called
`ASTBlock.transpileBlockStatements()` for its own body unconditionally, with
no disposal-boundary support at module scope at all -
`ASTVariableDeclUsing.transpileJavaStatement()` would throw "no enclosing
disposal-boundary support" for any `using`/`await-using` declared directly
at a module's own top level. Fixed by mirroring `ASTFunction`'s own
handling of the identical "root container, not an `ASTBlock` instance"
concern: gate on `ASTBlock.hasUsingDeclarations(this)`, and when true, call
`ASTBlock.transpileStatementsWithDisposal(jsContext, jsContext, b, this,
getStatements())` instead - the exact same reusable disposal-boundary
helper `ASTBlock`/`ASTFunction` already use for Block/function-body
positions, just set up directly against the module's own top-level context
(no separate child context needed, same as a function body). Fixed all 7
files (`language/statements/{using,await-using}/*`), zero regressions.

**Fixed (2026-08-26)**: `import.meta` entirely unimplemented in transpiled
codegen. `ASTNode.transpileJavaExpression()` threw `JSTranspilerException:
Node class ASTImportMeta is not (yet) supported by the transpiler to
evaluate` for ANY `import.meta` reference under a transpiled root - no
codegen path existed at all, unlike interpreted mode's `AbstractModule.
getImportMetaObject()`. Fixed by adding `ASTImportMeta.
transpileJavaExpression()`, mirroring `evaluate()`'s own `context.
getMainContext().getScriptUnit().getImportMetaObject()` call as a Java
expression fragment (`_ctx.getMainContext().getScriptUnit().
getImportMetaObject()`) - `_ctx` (`JSTranspiler.MAIN_CONTEXT`) is the
runtime-context parameter already threaded through every generated method
including nested function bodies, so this works regardless of how deeply
`import.meta` is nested inside ordinary function bodies within the module.
Fixed all 5 `import.meta/*` files.

While verifying, also found and fixed a related, previously-undiscovered
bug in the 2-argument `import(specifier, attributes)` form specifically:
`ASTImportCall.transpileJavaExpression()`'s attributes-present branch called
the RAW `RuntimeUtil.toString()` directly on the specifier value (unlike
the no-attributes/`import.defer` branches, which already use a "checked"
wrapper - `dynamicImportChecked()`/`dynamicImportDeferChecked()`, added
earlier this session's P1-P4 work) - an abrupt `ToString()` therefore
propagated as a real synchronous throw instead of rejecting the returned
promise (test262 `2nd-param-trailing-comma-reject.js` and its `2nd-param-
evaluation-*` siblings). Fixed by adding a new `RuntimeUtil.
dynamicImportChecked(context, specifierValue, Supplier<Object>
attributesValueSupplier)` overload - the attributes value is passed as a
`Supplier`, not a plain value, specifically so it's evaluated AFTER the
checked `ToString(specifier)` succeeds (mirrors `ASTImportCall.evaluate()`'s
own interpreted-mode ordering exactly: specifier expression, then checked
ToString, then the attributes expression as a genuine synchronous throw) -
a plain `Object` parameter would have Java evaluate it as a method argument
before the checked ToString ever ran. Fixed 2 more files
(`import-meta.js`, `2nd-param-trailing-comma-reject.js`).

A full unfiltered transpiled-mode sweep confirms both fixes: 28 -> 21
failing files (35 -> 26 errors), 7 fixed, zero regressions. Interpreted
mode stayed clean.

**Fixed (2026-08-26)**: plain `export function f(){}` (non-default, named)
own-binding not assigned in transpiled codegen - same expression-mode-vs-
statement-mode dispatch bug as the earlier `export let X;` and `export
default function F(){}` fixes, found in the third and last place it could
occur. `ASTExport.transpileJavaStatement()`'s `namedExport!=null` branch
only ever ran `ASTFunctionDecl` exports through expression-mode codegen
(`JSTranspiler.asRawValue()`/`transpileJavaExpression()`), which
constructs `new FnClass(...)` and returns it as a bare expression value -
correct for use as a for-loop-head fragment, but as a standalone statement
it constructs the function and immediately DISCARDS it without ever
assigning it into `f`'s own module-level variable slot. Fixed by
dispatching `ASTFunctionDecl` named exports through
`transpileJavaStatement(jsContext, b)` instead (which already finds the
declaration's own slot and assigns correctly) - the same fix already
applied to `ASTVariableDecl` exports and default-exported hoistable
declarations. `ASTClassDecl` named exports are deliberately NOT included -
their own `asRawValue()`-based codegen is a separate, unverified case not
touched by this fix. Fixed `instn-local-bndng-export-{fun,gen}.js`,
confirmed via full unfiltered sweep: 21 -> 19 failing files (26 -> 24
errors), zero regressions. Interpreted mode stayed clean throughout.

**Session total: 74 -> 5 failing files (85 -> 9 errors)**. The 5
remaining are the 4 files genuinely needing async/TLA scheduling
infrastructure and true SCC cycle-root merging (`top-level-await/*` +
`import-defer/evaluation-top-level-await/flattening-order/main.js`) and
the 1 `destructuring` file needing an architectural bridge for a
`with`-scope evaluation-order check in transpiled codegen - see each
one's own entry above for the precise, code-location-level root cause.

**Destructuring `with`-scope gap FIXED (2026-08-27)**, narrower than the
"needs its own dedicated investigation" assessment below turned out to
require: rather than threading a probe hook through every transpiled
destructuring call site (the "zero blast radius, every context forwards
the same factory" property `.assign()`'s `WithScopeResolvingFactory`
has), it's enough to add ONE targeted probe at `ASTVariableDecl`'s own
single `VariableFactory` callback for a `var {...} = ...;` VariableStatement
(`transpileJavaStatement()`'s `ASTObjectLiteral` branch) - the only call
site interpreted mode's own `WithScopeResolvingFactory` implementor
(`ASTVariableDecl.Entry.assign()`) covers either. Mirrors `getIdentifierWriteAccessor()`'s
own with-scope walk (same one a plain `var x = value` write already uses):
for a VAR-typed target only, walk `jsContext`'s parent chain collecting
`getWithJavaName()` values until reaching the level that owns the
variable, then emit a bare `getIdentifierAccessor(_ctx,"name",var,idx,false,
with1,...)` call purely for its `RuntimeUtil.hasProperty()`-probing side
effect (discarding the returned accessor - the actual write still goes
directly to the variable's own declared slot via `VarAccessor.destruct()`,
unchanged) immediately before that call. Emits nothing at all when the
declaration isn't lexically inside a `with` block (the overwhelmingly
common case) - zero-cost, zero-behavior-change there. Confirmed via `grep`
across the entire `test/` tree that this is the ONLY test262 file (in
`language/` or `built-ins/`) combining an actual `with` statement with a
`var {}=`/`var []=` destructuring pattern, so the change's reachable
blast radius is provably confined to this one file. Verified via isolated
`AdHocProbeTranspilerTests` sweeps: `language/destructuring` (0 errors,
full directory) and `language/statements` (0 errors, including every
`with`-statement test - Proxy/unscopables edge cases included) both clean,
plus a full interpreted-mode `Test262AllTest` sweep (0 failures - this
change is transpiled-only but verified anyway per project convention).
An earlier same-day attempt at the ASYNC-scheduling cluster (see its own
entry above) was implemented, tested, and reverted after net-regressing 4
files - by contrast, this fix is the "don't stop at first blocker" lesson
paying off in the other direction: the original "needs its own dedicated
investigation" assessment turned out to be based on the WRONG assumption
that a general, every-call-site hook was required, when in fact only the
ONE call site interpreted mode itself needed was reachable by any actual
test. Session total revised: **74 -> 4 failing files (85 -> 8 errors)**.
Further revised the same day by the 4th async-scheduling attempt (see its
own entry earlier in this same cluster's history, above): **74 -> 1
failing file (85 -> 5 errors)** - only `flattening-order/main.js`
remained. That file was fixed the next day (2026-08-28, see the
"transpiled ROOT that is itself a module" entry above): **74 -> 0
failing files** - no known transpiled-mode module/import gaps remain.

**Full-suite-reliability note (2026-08-26)**: the full unfiltered
`All262TranspilerTests` run (~2.5h) was repeatedly killed mid-run past
the ~90 minute mark this session, in an environment where it had
previously completed reliably. Verification for the last 2 fixes in this
file used directory-scoped sweeps instead (`Test262OneTest.TEST_FILE` set
to `"language"`, then `"built-ins"`, run via `AdHocProbeTranspilerTests`)
- together these cover the full `test/` tree the harness runs, so
regression confidence is equivalent to a full sweep, just split into
two runs that each complete inside the reliable window. If the full-suite
run is reliable again in a future session, this splitting isn't needed;
if not, prefer directory-scoped sweeps over fighting the timeout.

- ~~**Unrelated to modules** `language/destructuring/binding/keyed-
  destructuring-property-reference-target-evaluation-order-with-bindings.js`~~ -
  **FIXED (2026-08-27)** - see the dedicated entry above (right after this
  session's total) for the actual fix, which turned out to need only ONE
  targeted call site rather than the general hook this entry originally
  assessed as necessary.

---

## Formerly under "Not implemented" - now implemented

- **Explicit Resource Management** (ES2024 proposal) - implemented
  (`Symbol.dispose`/`Symbol.asyncDispose`, `SuppressedError`,
  `using`/`await using` declarations, `DisposableStack`/
  `AsyncDisposableStack`), in both interpreted and transpiled mode, for a
  `using`/`await using` declared in a Block, function body, the for-of loop
  HEAD, the for-AWAIT-of loop HEAD, or a C-style `for(...)` loop's own init
  clause, including the for-of-loop-variable immutable-binding restriction.
- **`for-await-of` grammar and runtime**: implemented (`for await (...)` in
  `JSParser.jj`, `ASTForOf`'s `isAwait` flag, `AsyncJavaIterator`/
  `AsyncFromSyncJavaIterator` adapters). The 1 remaining
  `language/statements/for-await-of` failure is covered under "Known
  flaky" below (genuine architectural gap, not a grammar/wholesale issue).

---

### ~~`language/import/import-defer/evaluation-top-level-await` - "flaky under concurrent system load"~~ - WRONG DIAGNOSIS, all 4 files fixed 2026-08-23

This section previously claimed all 4 files here were CPU-contention flakiness,
not a real gap - re-tested 2026-08-23 (with the actual suspected interference
process, `kill -STOP`'d for a clean isolated run, still failing identically
against a pristine pre-session baseline commit) and that diagnosis was
**confirmed wrong**: these were deterministic, 100%-reproducible bugs,
unrelated to system load. **Lesson: a "flaky under load" diagnosis from an
earlier session is not self-verifying - a genuine bug can happen to share a
failure signature with real environmental noise (a `compareArray`/timing
mismatch) seen elsewhere; re-confirm with the interference source actually
paused, not just inferred from a correlation.**

All 4 are now genuinely fixed, across two rounds:

- **Round 1** (`import-defer-transitive-async-module.js`,
  `sync-dependency-of-deferred-async-module.js`): `ASTImport.hoistBindings()`'s
  `import defer` branch was eagerly evaluating the WHOLE deferred target via
  `resolveModule()` whenever `gatherAsynchronousTransitiveDependencies` found
  ANY async piece anywhere in its graph, instead of evaluating only the
  actually-async pieces. Fixed by routing through a new
  `InterpretedGlobalRuntimeContext.startAsyncDependencyEvaluation()`, leaving
  the target itself genuinely deferred.
- **Round 2** (`async-cycle-dependency-of-deferred-module.js`,
  `flattening-order.js` - the deeper of the two bugs): the module CONTAINING
  an `import defer` statement needed to become properly async-pending on any
  in-flight cycle its deferred target transitively touches, AT LINK TIME, with
  each deferred target's gathered async pieces evaluated in their true
  source-order position (interleaved with ordinary eager imports, not as a
  separate pass afterward - spec's own `InnerModuleEvaluation` builds ONE
  `evaluationList` this way). Fixed by replacing `ASTProgram.
  getEagerModuleRequests()`/a first-draft `getDeferredModuleRequests()` with
  `getModuleEvaluationOrder()` (one ordered list of (specifier, phase) pairs,
  deduped only by EXACT (specifier, phase) match, not specifier alone - a
  specifier appearing both as `import defer` and a later plain import are
  genuinely separate request records per spec, confirmed by a self-introduced
  regression this round caught in `module-imported-defer-and-eager.js` before
  landing) and extending `JSInterpretedUnit.linkModule()`'s single combined
  walk to gather+start each deferred item's async pieces right there, in
  position, registering this module as pending against any already-resolved
  cycle root that's still async via a shared `registerDependencyPending()`
  helper. Also fixed a companion bug in the SAME family as the earlier
  `readyForSyncExecution()` fix:
  `gatherAsynchronousTransitiveDependencies()` treated ANY
  `moduleStatus==EVALUATING` as "someone else already owns this, skip" - but
  GaltaJS has no separate EVALUATING-ASYNC status (a module suspended
  mid-await stays `EVALUATING` throughout), so a suspended-but-genuinely-
  gatherable async module was never found at all (new `JSInterpretedUnit.
  isAsyncEvaluation()` accessor fixes this).

**Debugging note**: investigating Round 2 involved an apparent ~13-minute
"hang" during testing that turned out to be a stale-generated-Java-files
(~4000 files in `js-test-test262/src/test/java/compiled/`, left over from an
earlier transpiled-mode sweep) slow `javac` compile, NOT a genuine runtime
infinite loop - confirmed via `jstack` (stuck in javac's own `Attr`/
`checkDefaultMethodClashes`, not JS engine code) only after an initial,
overly-cautious revert of a materially similar fix attempt. Always clear
that directory before a fresh test run, and get a thread dump before
assuming a "hang" is a genuine runtime deadlock in freshly-changed code.
