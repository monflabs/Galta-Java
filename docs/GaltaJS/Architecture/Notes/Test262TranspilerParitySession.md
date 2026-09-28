# Test262 Transpiler-Only Parity: Session Record

This document is an exhaustive record of the work done in this session to drive
`TRANSPILER_ONLY_FILTER` (in `parent-js/js-test-test262/src/test/java/test/test262/Test262BaseTest.java`)
to **zero entries** — full parity between GaltaJS's interpreted and transpiled execution
modes across the entire test262 suite, for every test that isn't already excluded for
reasons unrelated to the transpiler (module/script goal-symbol handling, `for-await-of`
grammar, Joni regex-engine limits, BigInt, etc. — tracked separately in the general
`FILTER` array and `UNSUPPORTED_FEATURES` set, out of scope for this document).

This is a historical changelog, not living documentation — for the current state of
any remaining gaps, see `KnownGaps.md`. Nothing in this document should be trusted as
still-accurate code behavior without checking the current source; treat it as a record
of what happened, not a specification.

## Starting point and scope

At the start of this session's visible work, `TRANSPILER_ONLY_FILTER` stood at **123
entries** (down from **510** in earlier, not-directly-visible work). By the session's
end it reached **0**. The two phases:

- **Phase 1 (123 → 46):** the bulk of the classic, well-isolated bugs — see the
  "Phase 1" section below for a summary derived from that phase's own end-of-phase
  report.
- **Phase 2 (46 → 0):** the final, hardest 46 entries — many previously believed
  architecturally unfixable — described in full detail below, since this phase was
  directly observed start to finish in this conversation.

## Standing methodology (established and refined across the whole session)

1. **Fresh install mandatory before every probe.** `mvn test -pl parent-js/js-test-test262`
   alone does **not** rebuild `parent-js/js`'s own jar in `~/.m2` — it only recompiles
   the test262 module's sources against whatever `js` jar is already installed. A
   source change under `parent-js/js` is invisible to any probe until
   `mvn -pl parent-js/js,parent-js/js-test-suite,parent-js/js-test-test262 --also-make install -DskipTests`
   is re-run. Skipping this once produced a fully fabricated "fix works" result
   (the class-self-name-binding fix, first attempt — see below).
2. **Full `AllGaltaJSTests` regression required, not just test262 directory sweeps.**
   A fix can pass every targeted test262 sweep and still break GaltaJS's own internal
   unit-test suite (confirmed twice this session — see the call-expression
   double-evaluation fix's first, reverted attempt). The full suite
   (`mvn test -pl parent-js/js -Dtest=AllGaltaJSTests -q`, ~3520 tests) must be run
   early (right after the first working probe) and again at the end of every fix
   attempt, not only at the very end of a session.
3. **PROBE methodology:** comment out a target filter entry with a `//PROBE` prefix,
   point `Test262OneTest.TEST_FILE` at the target, clean stale `compiled/` output
   directories in both modules, run `mvn test -pl parent-js/js-test-test262
   -Dtest=AdHocProbeTranspilerTests -q` (forces transpiled mode). Restore the entry
   before moving to the next file. Stale `compiled/` directories left over from a
   previous probe produce spurious, unrelated `javac` errors — clean them every time,
   not just once.
4. **Revert completely on any regression, don't patch forward.** Every reverted
   attempt in this session was reverted in full (`git diff --stat` confirmed back to
   baseline) rather than iterated on, and the regression's root cause was documented
   even when no fix was kept.
5. **Parallel background agents must not share files without worktree isolation.**
   Discovered mid-session: two agents dispatched in parallel, both able to touch
   `Test262BaseTest.java`/`Test262OneTest.java`, raced each other — one agent's
   `//PROBE` comment-out was clobbered by the other's concurrent write, producing a
   false-positive "pass" (the file being silently skipped, not genuinely executed).
   From that point on, only one agent at a time was ever dispatched against these
   two files.
6. **Independent re-verification of every agent-reported fix.** After every
   background agent reported a fix, the fix was independently re-confirmed directly
   (not just trusted) by: a fresh install run personally, a direct grep-based count
   of the filter array before/after, and a personally-launched
   `AllGaltaJSTests` run watched to completion. This caught nothing false this
   session, but was applied uniformly regardless.

---

## Phase 1 summary (123 → 46)

Condensed from the end-of-phase report available at the point this document's
detailed phase began. Full mechanism detail for these may still be present in
`KnownGaps.md` at the time this document was written, but is not reproduced here.

- GlobalDeclarationInstantiation validation cluster: ~10 files (script-level
  var/function/lexical declaration duplicate-detection).
- Parameter-scope vs. body-scope split (`TranspilerParameterScopeContext`): ~39 files
  — a function whose parameter list has default/destructuring expressions AND whose
  body redeclares a parameter name needs the parameter list's own expressions to
  resolve free identifiers past the function, not into it. The session's biggest
  single lever at the time, and the template later reused successfully several more
  times in Phase 2.
- Private-field-in-eval: 4 files, fixed on a second, narrower attempt after an
  earlier (pre-session) 10-file regression.
- Intercalated static/non-static computed class fields: 2 files, fixed on a second,
  narrower attempt after an earlier 65-file regression.
- Strict-mode eval var-leak: 3 files.
- `do`-`while` unreachable-statement-after-labeled-break detection: 2 files (1 bonus
  side effect).
- Unicode identifier tables: 2 files — turned out to be a 64KB-per-method JVM
  bytecode limit on a large compile-time `String[]` constant, not a stale Unicode
  table as first assumed.
- Indirect eval `ClassCastException` when called from interpreted code nested inside
  a transpiled program.
- Numerous stale filter-entry removals (files that had already started passing as
  side effects of other fixes, caught by periodic re-sweeps) and a few
  false-positive-removal corrections.
- Class self-name-binding: **attempted and reverted** (first attempt) — appeared to
  pass isolated probes, but a fresh, correctly-rebuilt full-directory sweep showed
  the outer `let`/`const` binding's own value corrupted. This was the incident that
  established methodology rule #1 above. Left filtered, revisited and fixed in
  Phase 2 (see below).
- Call-expression base double-evaluation (property-lookup-before-arguments
  ordering): **attempted and reverted** (first attempt) — passed every test262
  sweep but broke `ProxyHandlerTest`/`TemplateStringTest` in the internal suite via
  a shared-`TEMP_VAR` reentrancy bug. This was the incident that established
  methodology rule #2 above. Revisited and fixed in Phase 2.
- Assignment/compound-assignment eval-shadow gap: investigated, correctly diagnosed
  as needing the single most broadly-shared identifier-read codegen path touched,
  not attempted at the time (12 files left filtered). Revisited and fixed in
  Phase 2.
- For-loop-head closure-hoisting family (for/for-of/for-in scope-* tests, ~27 files
  total across all its sub-variants): investigated multiple times, confirmed
  genuinely broken with concrete generated-code evidence, not attempted at the time.
  Revisited and substantially fixed in Phase 2.

At the end of Phase 1, 46 entries remained, roughly: 12 compound-assignment/
assignment eval-shadow, ~17 for/for-of/for-in loop-head/body closure variants,
2 `with`-statement, 1 `try`/`catch`, 2 `switch`, 1 call-expression, 1
`Function.prototype.toString`, 1 indirect-eval-through-`with`, 2 for-loop
test/update-clause closures, 2 `await using` per-iteration disposal, plus a handful
of singletons.

---

## Phase 2: the final 46 (fully detailed)

Each entry below reflects the fix as independently re-verified by the primary
conversation thread (not just as reported by a background agent), via a fresh
`mvn -pl parent-js/js,parent-js/js-test-suite,parent-js/js-test-test262 --also-make
install -DskipTests` followed by a personally re-run `AllGaltaJSTests` (and, for the
higher-risk fixes, personally re-run directory sweeps) after every single fix, no
exceptions.

### 1. `RGI_Emoji.js` — array-literal bytecode-limit fix (1 file)

**File:** `built-ins/RegExp/property-escapes/generated/strings/RGI_Emoji.js`.
Previously misdiagnosed as a Joni regex-engine limitation; re-investigation found a
deterministic `javac` `StackOverflowError` from an ~3968-entry deeply nested array
literal, the same class of problem as the earlier 64KB-bytecode-limit fix but for
`ASTArrayLiteral` codegen specifically.

**Fix:** `ASTArrayLiteral.transpileJavaExpression()` already had a complete,
correct array-literal-splitting codegen path (chunked `.litValues(new
Consumer<JSArrayImpl>(){...})` blocks), gated behind the opt-in
`JSTranspilerOptions`/`TranspilerCodeSplitter` feature that the test harness never
enables. Added a narrow, unconditional fallback: when the general splitter is
`null` (not opted in) **and** the literal has more than
`TranspilerCodeSplitter.DEFAULT_ARRAY_SPLIT_MAX` (150) entries, construct a
standalone `TranspilerCodeSplitter` so the existing, already-correct split path
runs anyway. Mirrors the earlier `JSTranspiler.createSplitStringArrayConstant` fix
for oversized `String[]` constants. Only ever activates for literals already far
past the size where the JVM bytecode limit becomes a real risk — every normal-sized
array literal in real code is untouched.

**Files touched:** `ASTArrayLiteral.java`.

### 2. Class self-name-binding, second attempt (10 files)

**Files:** `language/statements/class/{scope-name-lex-close,
scope-name-lex-open-heritage, scope-name-lex-open-no-heritage}.js`, `name-binding/
{const,expression}.js`, the `language/expressions/class/` siblings of the three
`scope-name-lex-*` files, and `static-init-scope-{lex-derived,private}.js`.

**Root cause of the gap:** a named class (`class C {...}` or `let x = class C
{...}`) creates its own separate, immutable inner lexical binding for its own name,
usable inside the class body, distinct from any outer binding of the same name.
GaltaJS's transpiler previously had no such separate binding at all — code inside
the class body referencing the class's own name just resolved to whatever outer
binding happened to exist.

**Root cause of the first attempt's failure (from Phase 1):** the first attempt
modeled the self-binding as a *runtime-context-chain object*
(`TranspiledClassPrivateScopeRuntimeContext`-style, mirroring the private-name
resolution mechanism), but ordinary identifier reads are resolved at **compile
time** by walking a codegen-time parent chain to find a `VariableDef`
(a compile-time record of a Java array slot), never touching a runtime context
object at all for a plain read/write — so the new machinery was never actually
wired into the path that mattered, and was largely dead code.

**Second attempt's design (successful):**
1. `ASTClassDecl.init()` registers a **second, genuinely separate** `VariableDef`
   (synthetic name, unstartable-with-digit so it can never collide with a real
   identifier; `VAR_TYPE.CONST`) into the *same enclosing array* the outer binding
   already lives in — a real, distinct Java array slot. New
   `TranspilerClassSelfNameContext` (mirrors the `TranspilerParameterScopeContext`
   "narrow view" template) redirects lookups of the class's own name to this slot.
   `initClass()` transitions it from TDZ to the real class value before any class
   element runs.
2. **The non-obvious second half:** a class element's method body is never emitted
   inside `ASTClassDecl`'s own codegen scope (`ASTClassDecl` isn't itself an
   `IContextBlockContainer`) — each element's `ASTFunction.init()` registers into
   whatever real enclosing container exists, built *before* the class statement is
   even reached. So step 1 alone was invisible to methods (confirmed via generated
   Java: a method's write still hit the outer slot). Fixed by extending
   `ASTVarContainer.getNestedFunctionParentContext` (the existing per-nested-function
   hook, already used for the parameter-scope split) with a new
   `wrapForEnclosingClasses` default that walks up from the function being declared
   and wraps with any enclosing named class's self-binding context.

**Verification:** isolated probes for both directions; generated-Java inspection
confirming the outer `let`/`const` slot and the new self-binding slot are genuinely
separate (a method's `C = 42` now emits `throwAssignmentToConstant` against the new
slot, the outer slot untouched); full `language/statements/class` sweep run twice
(223 then 222 errors — the 1-file delta traced to unrelated pre-existing test-order
flakiness, not a regression); full `language/expressions/class` sweep (223,
matching baseline); two independent full `AllGaltaJSTests` runs, both clean.

**Files touched:** `ASTClassDecl.java`, `ASTVarContainer.java`, `ASTFunction.java`,
new `TranspilerClassSelfNameContext.java`.

### 3. `private-class-field-on-nonextensible-objects.js` (1 file, side effect)

Already-documented as "likely also fixed" by fix #2 above (same root cause, a
static field initializer referencing the class's own name before the outer slot's
TDZ clears), but deliberately left filtered pending its own isolated verification.
Independently PROBEd after a fresh install: passes cleanly. Removed.

### 4. Bare-statement loop-body per-iteration scope (3 of 10 files investigated)

**Files fixed:** `language/statements/for-of/scope-body-lex-boundary.js`,
`for-in/scope-body-lex-boundary.js`, `for/scope-body-lex-boundary.js`.

**Root cause:** a closure's Java class declaration hoists to the nearest enclosing
`ASTVarContainer`, found by walking the AST **parent chain at construction time**
(before `init()` even runs). A loop body that's a bare statement (no `{}`) has no
block container of its own to hoist a closure into, so the closure hoists to the
loop node itself — outside the already-correct per-iteration redirect window that
block bodies get.

**A prior, different attempt (documented, reverted before this session) had tried
splitting `ASTVarContainer.transpilerDeclareStatement()` into a
declaration-vs-initialization split and deferring just the declaration half — this
fixed 1 file but broke 5 others with Java "cannot find symbol" compile errors,
because a closure's declaration and its various instantiation sites (some
structurally distant, e.g. reachable only from the collection expression) stopped
being co-located.**

**This session's fix (different mechanism):** `ASTFor`/`ASTForOf`/`ASTForIn`
constructors now call `wrapBareBodyIfNeeded(t, bodyNode)`, which wraps a
non-block body in a synthetic single-statement `ASTBlock` — but **only** when a
new `mayContainClosure()` tree-walk (structurally mirroring the existing
`mayCaptureAcrossIterations()` helpers) finds a closure or `eval` inside it. This
routes the bare-statement case through the exact same, already-working block-body
mechanism instead of inventing a new one, so it can't hit the previous attempt's
declaration/instantiation-site mismatch.

**The other 7 of the original 10 files stayed filtered**, for genuinely different
causes not reachable by this fix: 5 files have the closure in the loop's own
**collection expression** (head), not the body; `for/scope-body-lex-open.js` has
the analogous gap in the C-style **init clause**;
`for-in/S12.6.4_A7_T2.js` turned out to be a misattribution (see fix #5).

**Files touched:** `ASTFor.java`, `ASTForOf.java`, `ASTForIn.java`.

### 5. For-in delete-during-enumeration (1 file)

**File:** `language/statements/for-in/S12.6.4_A7_T2.js` — a test where a nested
for-in loop deletes properties of the same object the outer for-in loop is
enumerating. Confirmed to genuinely be unrelated to the closure-hoisting family
it had been grouped with.

**Root cause:** `RuntimeUtil.keyIterator()` snapshots the enumerable key list
eagerly at loop start in both execution modes. The interpreter's `ASTForIn.
evaluate()` already re-verifies each collected key via
`RuntimeUtil.isStillEnumerableProperty()` immediately before using it, skipping
stale (deleted-before-their-turn) keys — the transpiled codegen had no equivalent
check and fed `_it.next()` straight into the assignment/body.

**Fix:** `ASTForIn.transpileJavaStatement()` now caches the collection value once
in a Java local (`_col`), and each iteration assigns `_it.next()` to a `_key`
local and inserts `if(!isStillEnumerableProperty(env,_col,_key)) continue;` before
the existing assignment/body codegen — mirroring the interpreter's existing
per-key check exactly.

**Verification:** confirmed genuinely transpiler-only first (interpreted mode
passes on its own); full `language/statements/for-in` sweep, both modes, clean
(112/115 files).

**Files touched:** `ASTForIn.java`.

### 6. Loop-head (collection-expression/init-clause) closure gap — investigated, correctly declined (6 files, at the time)

The 5 for-of/for-in and 1 for-loop files whose closure lives in the collection
expression or init clause (excluded from fix #4). Generated-code inspection
confirmed every closure hoisted to a loop node's own container captures that
container's shared `Object[]` array **by Java lexical reference** — an array that
is *later* mutated in place by the per-iteration copy-back trailer needed for
legitimate cross-iteration propagation of body-declared closures. A head closure
therefore observes the array's *final* post-loop state instead of its own
creation-time value. Concretely: `for/scope-body-lex-open.js`'s `probeDecl`
closure (in a `ForDeclaration`'s own default-value expression) shares the exact
same array as the still-failing `probeExpr`/`probeBefore` closures, and any fix
that stopped the copy-back from clobbering the latter would, by the same
mechanism, break the former — the moment a multi-iteration variant existed. This
round correctly declined to force a narrow fix and instead re-diagnosed
precisely; **fixed properly in round 12 below** via a genuinely different,
per-closure snapshot mechanism.

### 7. Call-expression base double-evaluation, second attempt (1 file)

**File:** `language/expressions/call/11.2.3-3_3.js` — `foo.bar()` where `foo.bar`
doesn't exist must still evaluate the call's arguments before throwing "not a
function" (spec: the callable-check happens after `ArgumentListEvaluation`, but a
null/undefined-base property-lookup throw must precede the arguments).

**First attempt (Phase 1, reverted):** split the property lookup into
`getMethodTarget`/`getPrivateMethodTarget` helpers using the existing shared
`JSTranspiler.TEMP_VAR` field (write `base` into it, read it back later as
`this`). Regressed `ProxyHandlerTest`/`TemplateStringTest`: `getMethodTarget`'s own
body can trigger arbitrary JS execution (a Proxy `get` trap or getter) — if that
nested execution performs even one more method call, it clobbers the same shared
`TEMP_VAR` slot before the outer call reads it back.

**Second attempt's design:** replaced the shared field with the codebase's
existing `executeIsolated(context, Supplier)` pattern (already used elsewhere,
e.g. `ASTArrayLiteral.transpileJavaAssignment`) — wraps a block of Java statements
with their own genuinely fresh, per-invocation local variable (declared via
`jsContext.generateUniqueId`) inside a lambda, usable anywhere a normal
expression is needed. New `JSTranspiledUnit.resolveMethodTarget(context, base,
method)` does the property lookup (the part that can throw or trigger a
getter/Proxy trap) and returns `Object[]{base, function}`; a new
`invokeResolvedMethod(...)` family (arity 0–10 plus `Object[]`-params) does only
the callability check. `ASTCall.transpileChainingNode` wraps the call in
`invokeResolvedMethod(_ctx, executeIsolated(_ctx, () -> resolveMethodTarget(...)),
"method", args...)`. Because each `executeIsolated` invocation gets its own fresh
local (not a shared field), a nested re-entrant call during property lookup gets
its *own* separate `executeIsolated` call with its own separate local — nothing
left to clobber, by construction rather than by careful sequencing.
`super.m()`/private-field method calls/computed-index calls were left untouched
(not needed for this file).

**Verification:** isolated probe; `AllGaltaJSTests` run three times across the
attempt (all clean, 3520/0/0); `ProxyHandlerTest`/`TemplateStringTest` run
explicitly and individually (both passing); `language/expressions/call` sweep
clean; `language/expressions/optional-chaining` sweep showed one pre-existing,
unrelated `for-await-of` grammar failure, confirmed identical before/after by
temporarily reverting and re-running.

**Files touched:** `ASTCall.java`, `JSTranspiledUnit.java`.

### 8. Eval-shadow read fix (12 files)

**Files:** `language/expressions/compound-assignment/S11.13.2_A6.{1..11}_T1.js`,
`language/expressions/assignment/S11.13.1_A6_T2.js`.

**The gap:** a sloppy-mode direct `eval("var x = ...")` inside a function can
create a new binding that shadows an outer same-named binding for the rest of
that function's execution. GaltaJS's transpiler resolves every free-variable read
statically to a fixed compile-time slot, so any read occurring anywhere after
such an eval call, of a name the eval could have shadowed, needs to become a
dynamic runtime lookup instead.

**Why this had been declined before:** the obvious fix point,
`ASTIdentifier.getIdentifierReadAccessor()`, is the single most broadly shared
codegen touchpoint in the transpiler — every identifier read in every transpiled
program flows through it, and this session had already been burned twice by
under-gated changes to shared codegen paths.

**What made it tractable this time:** apply the same "conditionally allocated,
zero-cost by default" template already proven for the parameter-scope split and
the class-self-name-binding fix. Investigation also found the write side was
*also* broken (not previously known): a direct eval's bundled free-variable
snapshot (`ASTCall.getVariableJavaReferences`) included the outer function's `x`
even when the eval's own calling function never declared it, so
`StandardLibrary.TranspiledEvalContext` was silently overwriting the *outer* slot
instead of creating a new binding scoped to the calling function
(EvalDeclarationInstantiation, ECMA-262 §19.2.1.3 step 5) — the compound
assignment's own write-back only coincidentally masked this for the target
cluster's second assertion.

**Fix (two coordinated, independently gated halves):**
1. **Write side:** new `VarAccessor.isOwnScope()` tag (default `true`, inert
   everywhere except inside the eval bundle), computed per-entry in
   `ASTCall.collectOwnScopeNames`, honored by
   `StandardLibrary.TranspiledEvalContext.getLocalVariableEntry()`/
   `findParentAccessor()`.
2. **Read side:** `ASTFunction.hasNonStrictDirectEvalInOwnBody()` — a one-time,
   per-function scan (never descending into nested functions/classes) detecting a
   literal, syntactically visible, non-strict direct `eval(...)` call in a
   function's own body. Only then does `transpileFunctionBody` wrap
   `functionContext` in a new `TranspilerEvalShadowContext` for that function's
   body transpilation — a pure pass-through except for a new
   `isEvalShadowBoundary()` marker, checked by `ASTIdentifier.
   getIdentifierReadAccessor()` the same way it already checks
   `getWithJavaName()`. Crossing that boundary wraps a read in
   `JSTranspiledUnit.evalShadowRead(_ctx, name, staticValue)`, which checks only
   the local runtime context's own dynamic bindings before falling back to the
   static value.

**Zero-cost verification:** a byte-for-byte generated-Java diff of an unrelated,
eval-free file, comparing output with the fix's codegen reverted vs. restored —
`diff` exit code 0.

**Verification:** first working probe → immediate full `AllGaltaJSTests` (clean);
full sweeps of both `language/expressions/compound-assignment` and
`language/expressions/assignment` (every file, not just the 12 targets) — both
exit 0; final fresh install + full `AllGaltaJSTests` — 3520/0/0.

**Files touched:** `VarAccessor.java`, `JSVarRef.java`, `JSTranspiledUnit.java`,
`ASTCall.java`, `ASTFunction.java`, `ASTIdentifier.java`,
`StandardLibrary.java`, `JSTranspilerGeneratorContext.java`, new
`TranspilerEvalShadowContext.java`.

### 9. `with`-statement closure-capture fix (2 of 3 files)

**Files fixed:** `language/statements/with/{scope-var-open,scope-var-close}.js`.

**Root cause:** identical in shape to fix #4's bare-body gap — `ASTWith` is not
itself an `ASTVarContainer`, so a closure inside a bare (non-`{}`) `with` body
skipped it during the container-claim walk and registered with the with
statement's *enclosing* function/program instead, whose closure-class
declarations are all emitted at the top of the generated method, textually
*before* the with statement's own `final Object with_N = ...;` local exists — a
Java local/inner class can only capture a local declared before it. A `with`
body *with* its own braces was never affected (`ASTBlock` is already its own
container).

**Fix:** same `wrapBareBodyIfNeeded()`/`mayContainClosure()` pattern as fix #4,
applied to `ASTWith`'s own constructor (a separate, duplicated implementation
matching that precedent's own choice not to share the helper across node types).
`with_N` itself stays a plain Java local — no runtime-context redesign needed.

**What stayed filtered:** `language/eval-code/direct/global-env-rec-with.js` — a
direct `eval()` lexically inside a `with` block, a genuinely different mechanism
(see fix #14 below).

**Verification:** generated-Java inspection confirming the closure class moved
from the top of the method to nested inside the synthetic block, now correctly
capturing `with_N`; early full `AllGaltaJSTests` (clean, from a genuinely clean
`mvn clean install` after an unrelated stale-artifact false alarm was ruled out);
full `language/statements/with` sweep (181 files, clean); `language/eval-code/
direct` sweep (only the pre-existing, unrelated `import.js` module-resolution
gap); final fresh install + full regression clean.

**Files touched:** `ASTWith.java`.

### 10. `Function.prototype.toString` source fidelity (1 file)

**File:** `built-ins/Function/prototype/toString/method-computed-property-name.js`.

Previously investigated and re-confirmed at least twice as "genuinely deep — no
per-function source-position tracking exists anywhere in the transpiled runtime
path." That framing turned out to be too pessimistic: the needed infrastructure
already existed, just wasn't wired up.

**What was already there:** `ASTProgram` unconditionally stores the full
original source text regardless of execution mode; `ASTFunction.
extractOriginalSource()` (already used by `BuiltinFunctionInterpreter`, with
dedicated bracket-matching logic for exactly this computed-method-name shape) is
pure AST-plus-source-text computation with no interpreter-only runtime
dependency, so it's just as callable while the transpiler is walking the AST;
`ASTFunction.transpileJavaExpression()` is the single, universal codegen call
site that constructs every transpiled function object (confirmed via grep — the
only `new F1(...)` builder for declarations/expressions/methods/arrows/
generators/async); `ASTLiteral.encodeLiteral()` already handles embedding
arbitrary strings as Java literals safely.

**Fix:** `BuiltinFunctionTranspiler` gained an `originalSource` field and a 6-arg
constructor; `ASTFunction.transpileJavaExpression()` computes
`extractOriginalSource()` once at transpile time and passes it as the 6th
constructor argument via `ASTLiteral.encodeLiteral`; `ASTVarContainer`'s two
function-class codegen sites (per-function-class and shared-dispatcher modes)
forward the new parameter; `BuiltinFunctionPrototype`'s `toString` case gained a
`BuiltinFunctionTranspiler` branch mirroring the existing interpreter one.

**Verification:** isolated probe; early full `AllGaltaJSTests` (clean); full
`built-ins/Function` directory sweep (526 files, clean); final fresh install +
full regression clean.

**Files touched:** `BuiltinFunctionTranspiler.java`, `ASTFunction.java`,
`ASTVarContainer.java`, `BuiltinFunctionPrototype.java`.

### 11. Try/catch destructuring-default closure fix (1 file)

**File:** `language/statements/try/scope-catch-param-lex-open.js` — `catch
([x, _ = probe = function(){return x;}])`: a closure in the catch parameter's own
destructuring default value, referencing an earlier-bound name in the *same*
pattern, must see that name's real (caught) value.

Previously grouped with the for-head/switch-discriminant family as needing
"execution-order-aware resolution the transpiler can't express" — re-investigated
with fresh rigor and found to be a much narrower bug, not that family at all.

**Root cause (ground-truthed via generated Java):** `ASTCatch`'s own variables
container (it extends `ASTVarContainer`) was always left empty by design —
`ASTCatch.init()` declared the destructured catch parameter's bound names
directly onto `getBodyNode()` (the catch block) instead of onto `this`. But a
closure nested in the *pattern's own default value* is a child of the binding
pattern, which is a **sibling** of `bodyNode` under `ASTCatch` — never its
ancestor. The container-claim/free-variable-resolution ancestor walks only check
ancestors, so such a closure could never reach the real value; it walked straight
past `ASTCatch`'s always-empty container and kept climbing to an unrelated outer
same-named binding.

**Fix:** declare the destructured catch-parameter bound names on `this`
(`ASTCatch`'s own container) instead of `getBodyNode()`. `this` was already the
correct container for the closure's own Java *class* declaration — only the
*variable* declaration was on the wrong node. This also matches the actual spec
shape (CatchParameter's environment wraps the Block's own). The plain-identifier
`catch(e)` case (no default value) was untouched.

**Verification:** generated-Java inspection before/after confirming the write
moved from the outer slot to the catch clause's own slot; early full
`AllGaltaJSTests` (clean); full `language/statements/try` sweep, both modes (225
files including the 93-file `dstr/` early-error/redeclaration subdirectory,
relevant since this changes which container owns these bindings) — clean; final
fresh install + full regression clean.

**Files touched:** `ASTCatch.java`.

### 12. For-loop-head closure fix via per-instantiation snapshot (6 files)

**Files:** `language/statements/for-of/scope-body-lex-open.js`,
`for-of/scope-head-lex-close.js`, `for-in/scope-body-lex-open.js`,
`for-in/scope-head-lex-close.js`, `for-in/scope-head-lex-open.js`,
`for/scope-body-lex-open.js`. (The remaining 6 of the original loop-head-closure
cluster left open by round 6.)

**The new angle:** rather than moving *where* a closure's class is declared
(both prior attempts in this family), change *what value* it captures. A closure
hoisted to a loop node's own container captures that container's array by Java
lexical reference; instead, give its generated class an **extra constructor
parameter**, named identically to the container's own array variable (Java
name-shadowing then makes the closure's own field win inside its own body,
verified safe with a standalone `javac`/`java` test before implementing), and
pass `java.util.Arrays.copyOf(currentArrayVar, size)` into it at the closure's
own instantiation site — a genuine snapshot, immune to the later per-iteration
copy-back mutation.

**Mechanism:** `ASTVarContainer.needsHeadClosureSnapshot()` (new, `false` by
default), overridden by `ASTFor`/`ASTForOf`/`ASTForIn` to their existing
`needsPerIterationBinding()`; `ASTVarContainer.
transpilerDeclareFunctionClasses()`'s single-class branch adds the extra
field+constructor-parameter only when true; `ASTFunction.
transpileJavaExpression()` supplies the `Arrays.copyOf` snapshot at the
instantiation site, using whichever array is live there (correct in both the
pre-loop TDZ case and the live per-iteration-redirect-window case).

**Verified, honestly documented limitation (not a regression, no known test262
file hits it):** an ad hoc stress test (not part of test262, written to probe
this specifically, then discarded) found that a closure immediately followed, in
the *same* head clause, by a further write to the same captured variable (e.g.
`for(let i=0; i<3; probes.push(function(){return i;}), i++)`) gets snapshotted
one step too early — this is the residual "captured too early" limitation
documented in `KnownGaps.md` and directly informed round 16 below.

**Verification:** all 6 probed individually; early full `AllGaltaJSTests`
(3520/0/0); fresh sweeps of `for-of`/`for-in`/`for` (the `for` sweep's only 2
residual failures were the pre-existing, unrelated `S12.6.3_A2.{1,2}.js` parse
bugs); final fresh install + full regression (3520/0/0).

**Files touched:** `ASTVarContainer.java`, `ASTFunction.java`, `ASTFor.java`,
`ASTForOf.java`, `ASTForIn.java`.

### 13. `await using`/`using` per-iteration disposal — implemented from scratch (2 files)

**Files:** `language/statements/await-using/initializer-Symbol.{asyncDispose,
dispose}-called-at-end-of-each-iteration-of-forofstatement.js`.

**The gap (a genuinely missing feature, not a subtle bug):** `for (using x of
iterable)`/`for (await using x of iterable)`'s per-iteration resource must be
disposed at the end of **each** iteration, not once at loop exit. Confirmed via
probe: disposal never ran mid-loop at all in transpiled mode.

**Root cause:** `ASTVariableDeclUsing.transpileJavaStatement()` only registered a
resource into the nearest enclosing disposal boundary's list
(`ASTBlock.transpileWithDisposal`'s `List<DisposableResource>`) for the plain
declaration-statement form. A for-of loop-head declaration instead goes through
the inherited `ASTVariableDecl.transpileJavaAssignment()` (the per-iteration
value-binding codegen `ASTForOf` calls), which had no disposal-boundary
awareness at all. Unlike `ASTFor`'s C-style loop (whose init-clause `using` is a
plain statement and so already got correct once-at-loop-exit disposal),
`ASTForOf` never wrapped its own per-iteration body in a fresh disposal boundary.

**Fix:** `ASTVariableDeclUsing` gained a `transpileJavaAssignment()` override
that assigns the value and registers it against
`jsContext.getDisposablesListVar()`, mirroring the existing statement-form logic.
`ASTForOf.transpileJavaStatement()`, when the loop head declares `using`/`await
using`, now wraps the per-iteration assignment+body in
`ASTBlock.transpileWithDisposal()` — textually inside the generated `for(;;)`, so
a fresh `List<DisposableResource>` is created every iteration at runtime,
disposed via try/finally on every exit path (normal, `break`/`continue`/`return`,
or exception). The anticipated CPS/async complexity for `await using` turned out
not to apply: `await` here transpiles to a plain blocking `RuntimeUtil.await_()`
call (no CPS state machine in this engine), and `DisposeResourcesUtil.dispose()`
already calls that internally for async resources — both variants share the
identical mechanism.

**Two genuinely pre-existing, unrelated gaps discovered during the verification
sweeps** (both affect interpreted and transpiled modes equally, left
undisturbed): a module-vs-script goal-symbol distinction gap in
`ASTVariableDeclUsing.checkNotAtTopLevelOfScript()`, and an async-function
microtask-timing gap for plain-statement (not for-of) `await using`.

**Verification:** both files probed individually and pass; early + final full
`AllGaltaJSTests` (both clean); `language/statements/for-of` sweep clean;
`language/statements/using`/`await-using` sweeps clean apart from the two
pre-existing gaps above (confirmed via inspection to be genuinely unrelated,
never touching for-of).

**Files touched:** `ASTVariableDeclUsing.java`, `ASTForOf.java`.

### 14. Switch-discriminant closure fix via a new synthetic container (2 files)

**Files:** `language/statements/switch/{scope-lex-open-case,
scope-lex-open-dflt}.js`.

Investigated **four separate times this session**: once concluding it needed
genuine execution-order-aware resolution; once (immediately before this fix,
same day) re-confirming via generated-code evidence that `ASTSwitch` is the
sole `IContextBlockContainer` reachable from both the discriminant and the
`cases[]` array (both direct children of `ASTSwitch`, with `ASTCase` a plain,
non-container `ASTNode`) — concluding no existing structural seam could be used
to separate them, and that repointing the pervasively used AST `parent` field
would be too broad and risky to fully audit.

**The reframing that finally worked:** the prior conclusion only considered
*reassigning* declarations across an existing seam (as fix #11's `ASTCatch` case
allowed). It didn't consider *inserting* a brand-new seam — the same technique
already used successfully for the bare-body gaps (fixes #4 and #9).

**Fix:** new `ASTSwitchCaseBlock` node, inserted between `ASTSwitch` and its
`cases[]` array (never wrapping `exprNode`, the discriminant). `cases[]` are now
`ASTSwitchCaseBlock`'s own children, and it alone carries the CaseBlock's shared
`IContextBlockContainer` role — **one** shared container for all cases (not one
per `ASTCase`), preserving the spec's single cross-case lexical environment (a
`let y` in `case 1:` must be visible, TDZ-respecting, in `case 2:`'s own code).
`ASTSwitch` no longer extends `ASTVarContainer`/implements
`IContextBlockContainer` at all — it now extends `ASTNode` directly (like
`ASTWith`), holding `exprNode` and the new `caseBlock` as two separate,
sibling children; its `evaluate()`/`transpileJavaStatement()`/
`decompileStatement()` now read declared variables/functions from `caseBlock`
instead of from itself. Because `exprNode` is a sibling of `caseBlock`, not a
descendant, a closure in the discriminant now walks straight past the whole
switch statement to whatever truly encloses it, while anything in `cases[]`
resolves through `caseBlock`.

**Verification:** generated-code inspection confirming the discriminant closure
now reads the outer slot while the case-body closure reads the CaseBlock's own,
separate slot; two full `AllGaltaJSTests` runs (early + final, both clean); full
`language/statements/switch` sweep in both interpreted and transpiled+optimized
modes (clean, including the cross-case shared-environment/TDZ tests
`scope-lex-{let,const}.js`, `scope-lex-close-{case,dflt}.js`, confirming the
single-shared-CaseBlock-environment semantics weren't broken).

**Files touched:** new `ASTSwitchCaseBlock.java`; `ASTSwitch.java` restructured.

### 15. `global-env-rec-with.js` via with-aware eval context (1 file)

**File:** `language/eval-code/direct/global-env-rec-with.js` — a direct `eval()`
lexically inside a `with` block must see the with-object first when resolving
free identifiers.

**Root cause:** `StandardLibrary.TranspiledEvalContext.resolveOwnIdentifierEntry()`
checked its static `VarAccessor[]` free-variable bundle (built at transpile time)
*before* ever consulting an enclosing `with`-object, because transpiled `with` is
represented purely as a plain Java local (`with_N`, see fix #9), never pushed
onto any runtime-context chain eval's own parent-context walk could reach.
Interpreted mode never had this gap — its `InterpretedEvalContext` walks a real
`InterpretedWithRuntimeContext` via the actual runtime chain.

**Fix, applying the eval-shadow fix's (#8) established pattern to a different
gating condition** (per-eval-call-site rather than per-function, since a
function can contain both with-wrapped and non-with-wrapped eval calls): new
`ASTCall.collectEnclosingWithJavaNames(jsContext)` walks the codegen context
chain the way `ASTIdentifier`'s own resolution already does for
`getWithJavaName()`, but unconditionally (every enclosing `with`, not stopping
at a function boundary, since a `with` can lexically enclose a nested closure).
`transpileSpecialFunctions()` appends the result as an extra trailing element in
the eval call's existing runtime argument bundle — only when non-empty.
`StandardLibrary.TranspiledEvalContext` gained a 3-arg constructor taking
`withObjects` (nearest-first); `resolveOwnIdentifierEntry()` now checks each
with-object first (`hasProperty` + `@@unscopables`, mirroring
`InterpretedWithRuntimeContext`'s own logic exactly) before falling through to
the static bundle.

**Zero-cost verification:** generated-Java inspection — a non-with eval call's
argument bundle is byte-for-byte unchanged (still ends at the same element
count); the with-wrapped probe file gets exactly one new trailing element
appended.

**Verification:** isolated probe; early full `AllGaltaJSTests` (touching the
shared direct-eval codegen path, treated as critical — clean, 3520/0/0); full
`language/eval-code/direct` sweep (only the pre-existing unrelated `import.js`
gap); full `language/statements/with` sweep (181 files, clean); final fresh
install + full regression clean.

**Files touched:** `ASTCall.java`, `StandardLibrary.java`.

### 16. For-loop test/update-clause closures — final two files

**`let-closure-inside-condition.js`:** turned out to be a **stale filter entry**
by this point — fix #12's `needsHeadClosureSnapshot()` mechanism already handles
this exact shape correctly (the closure is in the TEST clause and nothing writes
to the loop variable again during that same clause's evaluation, so the existing
eager snapshot already lands at the right moment). Confirmed passing with **zero
code changes**, via isolated probe plus full sweeps of `for`, `let`, `const`,
`continue`, `break`, `for-in`, `for-of` (all clean apart from the pre-existing,
already-documented parse bugs). Removed.

**`let-closure-inside-next-expression.js` — the final entry, the single
most-repeatedly-confirmed-deep item this whole session (5+ separate
investigations across the day):** its closure sits in the INCREMENT clause,
immediately followed by `++i` in the same comma expression — a write the closure
must observe per spec, but fix #12's eager snapshot happens one write too early
(this is exactly the residual limitation fix #12 itself discovered and
documented).

An investigation into a `while(true)`-based loop rewrite (to get real hook points
between test/update evaluation) concluded that correctly preserving `continue`'s
semantics (jump to run the increment, then re-test) under such a rewrite would
require `ASTContinue`/`ASTBreak` — currently just emitting bare
`continue;`/`break;` or reusing the JS source label as a single Java label — to
map one JS label to two distinct, differently targeted Java labels, which looked
like new plumbing reachable from every `break`/`continue` in the language. A
reframing was then attempted: have `ASTContinue`/`ASTBreak` query their
*resolved target loop node* for which Java label to emit, defaulting everywhere
except a specifically-opted-in loop to today's exact existing output (the same
"gate on the specific case" discipline that made fix #14 tractable after an
identical-shaped false start).

**On investigation, that querying mechanism turned out not to be needed either.**
A second, genuinely new obstacle was found instead: a head closure's generated
Java **class is declared once, before the native loop starts** (not inside it),
so it cannot implicitly capture a fresh per-round local the way body closures
can — meaning even a correctly labeled `while(true)` rewrite would not, by
itself, have been *correct*.

**The fix that actually worked — deferring** *when* **the existing snapshot is
taken, not restructuring the loop at all:**
- `ASTFor.findDeferredIncClosure()` (new): a static scan detecting the exact
  shape — a closure literal directly among the increment clause's own top-level
  comma terms, followed by a later sibling term containing any write.
- `ASTFunction.setDeferredHeadSnapshotTempVar()` (new field, `null` everywhere
  else): flags just that one closure node; its `transpileJavaExpression()`
  additionally assigns its construction into a named temp local.
- `ASTVarContainer.transpilerDeclareFunctionClasses()` makes that one closure's
  class implement a new `org.monflabs.galtajs.rt.transpiler.
  HeadClosureSnapshotHolder` interface (non-final field + setter) — every
  sibling closure in the same container (e.g. one in the TEST clause) is
  unaffected, gated per-function, not per-container.
- `ASTFor.transpileForNode()` appends one extra Java-native comma-separated
  entry to the for-header's own update clause (Java's for-statement update slot
  already accepts multiple statement-expressions) that overwrites the snapshot
  with a fresh copy taken *after* the write.

No changes to `ASTBreak`, `ASTContinue`, or any loop's control-flow shape.

**Verification:** isolated probe; two full `AllGaltaJSTests` runs (early + final,
both 3520/0/0); fresh full-directory sweeps of `for`, `for-in`, `for-of`, `let`,
`const`, `continue`, `break`, `do-while`, `while` — all clean apart from the two
pre-existing parse bugs (`S12.6.3_A2.{1,2}.js`) and the one pre-existing
`let-closure-inside-initialization.js` parse bug, all confirmed via their actual
`ParseException` output, not new regressions; manual generated-Java inspection of
the target file, the (unchanged) sibling `let-closure-inside-condition.js`, and a
labeled/nested-loop file (`continue/nested-let-bound-for-loops-labeled-
continue.js`, confirmed zero bytes of new codegen — proving the gate is a true
no-op for ordinary loops).

**Files touched:** new `HeadClosureSnapshotHolder.java`; `ASTFunction.java`,
`ASTVarContainer.java`, `ASTFor.java`.

---

## Final state

`TRANSPILER_ONLY_FILTER` is empty (0 entries) as of the end of this session. Every
fix above was independently re-verified in the primary conversation thread (not
just accepted from a background agent's report) via: a fresh `mvn ... install
-DskipTests`, a direct count of the filter array before/after, and a
personally-launched, personally-watched `AllGaltaJSTests` run for every single fix
— no exceptions.

Files that were part of the *investigation* process but ended up with no net code
change (declined attempts, stale-entry confirmations) are not listed as "files
touched" above even where a filter-array/documentation edit was made.

Remaining, out-of-scope gaps (module-vs-script goal-symbol handling for `using`/
`await using`, `for-await-of` grammar, async-function microtask timing for
plain-statement `await using`, Joni regex-engine limits, BigInt, various
`UNSUPPORTED_FEATURES`) are tracked in the general `FILTER` array and
`UNSUPPORTED_FEATURES` set, not `TRANSPILER_ONLY_FILTER`, and are documented (as
of the state after this session's own cleanup pass) in `KnownGaps.md`.
