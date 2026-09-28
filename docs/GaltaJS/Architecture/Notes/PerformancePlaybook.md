# GaltaJS Runtime Performance Playbook

> **Historical note (2026-09-27).** The `parent-js/js-test-performance` module used by the
> verification commands below has been removed; cross-engine benchmarks moved to the separate
> JavascriptPerformance project, which does not include a GaltaJS executor yet. The commands
> and file paths are kept as a record of how each phase was measured.

> **Scope.** Phases 0–5 target the **interpreter**. §12 adds a parallel **transpiler**
> track (T1–T9). The two tracks are independent — the interpreter changes need not land
> before the transpiler track begins, though Phases 2a and 5a produce AST metadata that
> some transpiler phases consume.

> **Executable plan.** Any future session (or future you) can pick this up cold and drive it
> to completion. Every phase has: what to change, files to touch, verification commands,
> success criteria, and rollback. No context from prior conversation required.

---

## 0. Project Context

**Repo root**: `/Users/priand/phildev/monflabs/source/Galta-Java`
**Module**: `parent-js/js/` (working dir absolute:
`/Users/priand/phildev/monflabs/source/Galta-Java/galta/parent-js/js`)
**Maven root**: `galta/` (all `mvn` commands from there).
**Java**: 17.

### ⚠️ Concurrency invariant (READ THIS FIRST)

**A single parsed `ASTProgram` is executed concurrently by many threads.** Each thread has
its own `JSInterpretedRuntimeContext`, but the AST node instances are shared.

Consequences for this plan:

- **Parse/optimizer-time state** (fields set once, before the AST is published for
  execution) is safe as plain fields — treated as effectively `final`. This covers all
  Phase 1 hoisted flags and all Phase 2a/2b/2c resolution results (`scopeHops`,
  `slotIndex`, `resolvedVarType`, arity flags, `sequenceEnabled`, etc.). Publication safety
  is provided by `JSEnvironment.createScript` returning the fully-optimized AST once — any
  execution thread reads via a happens-before edge.
- **Runtime-mutable caches** (Phase 2d entry cache, Phase 3 PICs, Phase 5a type feedback)
  **must** avoid torn reads. The pattern is: pack the whole cache tuple into a small
  immutable record (all fields `final`), publish it via a **single `volatile` reference**
  on the node. Reads snapshot one ref; misses allocate a fresh record and publish. Two
  concurrent misses can each install their own record — last writer wins, which is
  correct (any published record is a valid cache) and avoids CAS overhead.
- **Never** update multiple mutable fields on a node in the fast path. Field-by-field
  writes without a common ref publish would let another thread observe a `class==X /
  accessor=fromRunOfClass Y` mix that dispatches wrong.
- `ThreadLocal` caches are rejected: `ThreadLocal.get()` on the hot path plus weak-map
  keying is more expensive than the volatile-ref pattern.

**What GaltaJS is**: a JavaScript engine implemented in Java. Two execution modes share the
same AST:
- **Interpreted** — pure tree-walk of `ASTNode` subclasses via
  `ASTNode.evaluate(JSInterpretedRuntimeContext, JSResult) → Signal` and
  `ASTNode.evaluateValue(JSInterpretedRuntimeContext, JSResult) → Object`.
- **Transpiled** — `JSTranspiler` emits Java source per AST, compiled in-memory.

**Phases 0–5 target the interpreted mode.** The tree-walk model stays. No bytecode, no JIT,
no Truffle rewrite. Transpiler-mode work is in §12 as its own track.

### Confirmed hot-path bottlenecks (from source read)

| # | Bottleneck | Location |
|---|------------|----------|
| 1 | Name-based scope lookup on every identifier read; walks parent chain, per-hop hash + `.equals()` | `AbstractRuntimeContext.java:92-106`, `VariableMap.java:195-213`, `ASTIdentifier.java:96` |
| 2 | No property inline cache; `env.getAccessor(instance)` per access | `ASTMember.java:188-234`, `RuntimeUtil.getProperty` around line 4006 |
| 3 | `Object[]` allocated per function call | `ASTBaseCall.java:110-143` |
| 4 | Function frame binds params by name via `createVariable` | `InterpretedFunctionRuntimeContext.java:26-52` |
| 5 | Full type coercion on every arithmetic op; only `add()` has `Integer+Integer` fast-path | `RuntimeUtil.java:222+` |
| 6 | `result.isSequence()` checked unconditionally on binary ops even when sequence extensions disabled | `ASTArithmeticOp`, `ASTMember` and siblings |

### Existing infrastructure to reuse

- **Transpiler already computes slot indices**: `ASTVarContainer.VariableDef.varIndex` and
  `getJavaVariableValue()` — used to generate indexed Java accesses. The interpreter ignores
  them today. Phase 2 closes that gap.
- **`VariableMap.VariableEntryArray`** stores locals in an `Object[]` + index — exactly the
  shape the interpreter should use.
- **`VariableMap.initVariable(name, array, index, type)`** and `VariableMap.cache(...)` are
  already implemented.
- **Optimizer pipeline**: `optimizer/ScriptOptimizer.java` with `NodeOptimizer[]` — currently
  `ConstantFoldingOptimizer` + `UnreachableCodeRemovalOptimizer`. A new
  `ScopeResolutionOptimizer` plugs in there.
- **`ASTIdentifier.id` is `.intern()`'d** (`ASTIdentifier.java:46`), so pointer-compare on
  the key is legal.

### Regression surface

Any interpreter change **must** pass all of:

```
AllGaltaJSInterpreterTests            # interpreter
AllGaltaJSInterpreterOptimizedTests   # interpreter + optimizer
AllGaltaJSTranspilerTests             # transpiled (unaffected code-wise, but shared JSOptimizerContext)
AllGaltaJSDecompiledTests             # AST → decompile → reparse → run
js-test-rhino                         # Rhino output parity
js-test-test262                       # Test262 ES conformance, all 3 modes
```

---

## 1. How to Execute This Plan

### 1.1 Setup (once per session)

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta

# Verify baseline build is green before touching anything
mvn clean install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests

# Optional but recommended
mvn -pl parent-js/js-test-rhino -Pfetch-externals generate-test-resources
mvn test -pl parent-js/js-test-rhino
```

If any of the above is red **before** your changes, stop — fix the pre-existing failures or
change branch. Do not proceed on a red baseline; you cannot attribute regressions.

### 1.2 Baseline benchmark (once per session)

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2 \
  | tee /tmp/galtajs-baseline-$(date +%Y%m%d-%H%M).log
```

Record ms/op per JS file (interpreted profile). 3 warmup + 5 measured runs, take median.
Numbers live in `/tmp/galtajs-baseline-*.log`; keep them for phase-over-phase deltas.

### 1.3 Per-phase workflow

For each phase below:

1. Create a branch: `git checkout -b perf/phase-<N>-<slug>`
2. Follow the phase's **Changes** section.
3. Build: `mvn install -pl parent-js/js --also-make -DskipTests`
4. Run **Verification** exactly as listed. Any failure blocks the phase.
5. Re-run the benchmark set (§1.2) and diff against the previous phase's numbers.
   Regression gate: **any bench slower by >3% blocks merge**.
6. Commit with a descriptive message referencing the phase (`perf(interp): phase 1a — hoist
   sequence-extension flag`).
7. Merge into the perf branch integration point; continue.

### 1.4 Rollback

Every phase is one or more small commits. If a downstream phase reveals a subtle regression
attributable to an earlier phase, `git revert <phase-commit-range>` and re-run
verification. Do **not** paper over with feature flags.

---

## 2. Phase 0 — Add targeted micro-benchmarks

Existing UBench lacks a hot-property, hot-closure, and recursive-fib micro. Add three JS
files so per-phase attribution is clean.

**Location**: `parent-js/js-test-performance/src/main/resources/benchmarks/ubench/`
(inspect existing `ubench/*.js` for the exact harness pattern first — the corpus lives under
`parent-js/js-test-performance/src/main/resources/benchmarks/`).

**New files**:
- `interp_recursive_fib.js` — `fib(28)`; ~10⁶ calls; measures call + arithmetic.
- `interp_hot_property.js` — `let o={x:0}; for(let i=0;i<1e6;i++) o.x += i;` measures
  property read/write and PIC.
- `interp_hot_closure.js` — outer function creates inner reader/writer over a captured
  counter, hot loop invokes both; measures closure resolution.

**Wire-up**: register in
`parent-js/js-test-performance/src/main/java/org/monflabs/script/performance/BenchmarkExecutor.java`
under `runUBench()` (one entry per file, mirroring existing entries). Add expected-output
fixtures if the harness requires them (check other UBench entries).

**Verification**: `mvn -pl parent-js/js-test-performance test -Dtest=AllUBench` executes them
and reports timings.

---

## 3. Phase 1 — Quick wins (low risk)

Ship as a single commit series. Unblocks reliable measurement for Phase 2.

### 3.1 Changes

**1a. Hoist `supportSequenceExtensions()` into node state.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTNode.java` — add
  `protected boolean sequenceEnabled;` set in `init()` around line 190 from
  `initContext.getEnvironment().supportSequenceExtensions()`.
- Rewrite hot-path `result.isSequence()` sites in `node/binaryop/ASTArithmeticOp.java`,
  `node/ASTMember.java`, `node/ASTAssignment.java` to short-circuit when
  `!sequenceEnabled`.

**1b. Interned-key identity fast path in `VariableMap`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/rt/interpreter/VariableMap.java`.
- In `getEntry()` (line 195), `createVariable()` (line 292), `delete()` (line 224): before
  the current `if(e.hashcode==hashcode && e.key.equals(varName))`, prepend
  `if(e.key == varName) return e;` (i.e. `==` identity check for interned strings). Fallback
  path unchanged.

**1c. Monomorphic coercion fast paths in `RuntimeUtil`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java`.
- Prepend exact-class checks (`o.getClass() == Integer.class` etc.) to `toBoolean` (~line 2423),
  `toNumber` (~line 1904), `toInt32`, `numberType` (~line 163). Keep the existing `instanceof`
  chains as fallback.

**1d. Zero- / low-arity call fast paths.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTBaseCall.java`.
- In `init()` compute `private final int fixedArity = (hasSpread ? -1 : parameters.length);`.
- Add `call0`, `call1`, `call2`, `call3` fast-path methods that skip the `getParamValues`
  loop; route `call()` (line 86) to them when `fixedArity` matches.
- Also update `ASTCall`, `ASTNewCall` call sites if they invoke `call()` directly.

**1e. Constant-fold `undefined` / `NaN` / `Infinity` in `ConstantFoldingOptimizer`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/ConstantFoldingOptimizer.java`.
- For an `ASTIdentifier` whose id is `"undefined"` / `"NaN"` / `"Infinity"`: check via
  `JSOptimizerContext` that no enclosing scope declares a shadowing `var`/`let`/`const`. If
  clean, replace with a literal node (mirroring the transpiler pattern at
  `ASTIdentifier.java:308-313`).

### 3.2 Verification

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262
mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

**Success criteria**: all tests green; **5–15%** speedup on tight loops (`interp_hot_closure`,
UBench loop micros).

---

## 4. Phase 2 — Parse-time scope resolution (largest win)

Ship as three sub-commits: 2a+2c together, then 2b, then 2d.

### 4.1 Changes

**2a. `ScopeResolutionOptimizer`.**
- New file:
  `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/ScopeResolutionOptimizer.java`.
- Register in `optimizer/ScriptOptimizer.java` default pipeline
  (`defaultOptimizer()` around line 60).
- Walks AST; for each `ASTIdentifier`, uses `JSOptimizerContext.resolveSymbolVar(name)`
  (already exists — see `ASTIdentifier.isConstant()` at line 261 for the exact API) to
  compute `(scopeHops, slotIndex, varType)`.
- **Skip / leave unresolved** when the enclosing scope has any of:
  - `ASTFunction.useEval` set anywhere in the enclosing chain
  - `with` statement present
  - identifier resolves to global scope (leave for 2d cache)
  - identifier is `Arguments.ARGUMENTS` and the enclosing function has mapped-arguments
    semantics (non-strict + simple parameter list — see
    `InterpretedFunctionRuntimeContext.java:43`)

**2b. Bound slot on `ASTIdentifier`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java`.
- Add fields:
  ```java
  private byte scopeHops = -1;      // -1 = unresolved
  private int  slotIndex;
  private VAR_TYPE resolvedVarType;
  ```
- Rewrite `evaluateValue()` (line 94): if `scopeHops >= 0`, walk `context.parent` that many
  times and return `ctx.variables.getSlot(slotIndex)`; otherwise fall through to the current
  `RuntimeUtil.getIdentifierValue` path.
- Same treatment in `evaluateAssign()` (line 172). Respect `resolvedVarType == CONST`.
- Add `VariableMap.getSlot(int idx)` / `setSlot(int idx, Object v)` that read/write the
  underlying `VariableEntryArray.array` directly. Expose `AbstractRuntimeContext.variables`
  as package-private (or add an accessor) for the direct-slot fast path.

**2c. Function frame slot array.**
- File:
  `parent-js/js/src/main/java/org/monflabs/galtajs/rt/interpreter/InterpretedFunctionRuntimeContext.java`.
- Query `ASTFunction.getLocalSlotCount()` (add this method if not present; sum of
  `VariableDef.varIndex + 1` across the container's declarations — the transpiler already
  tracks this).
- In the constructor (line 26), pre-allocate `Object[] slots = new Object[nSlots]`.
- For each parameter, write `slots[i] = values[i]` directly (skip name-keyed
  `createVariable`).
- Seed `VariableMap` entries via `VariableMap.initVariable(name, slots, i, type)` so
  name-based fallback still resolves and mapped-arguments still writes back through the
  shared array.
- **Mapped-arguments correctness**: `Arguments.create(..., simpleParameterNames)` at line 44
  writes back via `VariableEntryArray.setValue` — verify with a targeted test
  (`test262/language/arguments-object/mapped/*`).

**2d. Monomorphic entry cache for globals / cross-scope binds.** *(thread-safe: shared AST)*
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java`.
- Immutable cache record + single `volatile` reference:
  ```java
  private static final class IdentIC {
      final VariableMap   owner;   // identity guard
      final int           epoch;   // owner.mutationEpoch snapshot
      final VariableEntry entry;
      IdentIC(VariableMap o, int e, VariableEntry v) { owner=o; epoch=e; entry=v; }
  }
  private volatile IdentIC ic;
  ```
- Add `VariableMap.mutationEpoch` (int; bumped from `createVariable`, `delete`, `rehash`,
  `clear`; declared `volatile` so an epoch bump on the writer thread is visible to
  concurrent readers without a lock).
- Read path (single volatile load):
  ```java
  IdentIC snap = ic;
  if (snap != null && snap.owner == context.variables
      && snap.epoch == snap.owner.mutationEpoch) {
      return snap.entry.getValue();
  }
  // slow path: resolve, then publish a fresh record — plain assign is fine
  // (volatile-ref write publishes all final fields safely; two racing writers
  // simply overwrite each other with equally-valid caches)
  ic = new IdentIC(owner, owner.mutationEpoch, entry);
  ```
- Do not update `owner`/`entry`/`epoch` as separate mutable fields — they would tear.

### 4.2 Verification

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests

# All modes because scope resolution feeds the optimizer, transpiler, decompiler
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262

# Regression-sensitive test262 subsets — check output carefully
# (Run a full Test262 pass; then eyeball language/eval-code, language/statements/with,
#  language/arguments-object/mapped, built-ins/Function/prototype/bind,
#  built-ins/GeneratorFunction, language/statements/generators)

mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

**Success criteria**: all tests green; **30–60% speedup** on `interp_recursive_fib`,
`interp_hot_closure`, `Bench_UBench_loopsum`, `Bench_Sunspider_AccessNSieve`.

---

## 5. Phase 3 — Property inline cache

> **Design note.** An earlier draft of this phase cached only the `JSAccessor` per site.
> That's an accessor-dispatch cache, not a real PIC — the final call
> `accessor.getProperty(instance, name, ...)` still walks the prototype chain and re-hashes
> by name. Skipping `env.getAccessor(instance)` saves ~10 ns; the actual work is inside
> `getProperty`. A real PIC (see Nashorn's `PropertyMap` + `ScriptObject.spill[]`) caches
> the **resolved location** — the concrete value or slot index — not the dispatcher.
>
> Phase 3 is therefore split into **3a (Level 1 — prototype-value cache)** which ships next,
> and **3b (Level 2 — shapes + slot PIC)** which is a larger design and ships as its own
> project after 3a lands. Level 1 is a subset of Level 2's guard structure, so 3a is not
> throwaway work.

### 5.1 Phase 3a — Level 1: Prototype-value cache *(ships in this playbook)*

Targets the most common pattern in real JS: **method / prototype-property reads that never
change per shape** — `arr.push`, `arr.length`, `str.charAt`, `obj.toString`, `p.then`, …
For a receiver of class `X` with prototype chain `[X.prototype, Object.prototype]`, the
value of `push` on any instance is the *same function object* until someone mutates
`X.prototype` — which is nearly all the time.

**3a.1. Cache record + volatile refs.** *(thread-safe: shared AST)*
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTMember.java`.
- Immutable record; never update fields piecewise:
  ```java
  private static final class PropIC {
      final Class<?> receiverClass;
      final Object   propertyKey;      // interned string or Symbol
      final int      shapeToken;       // env.shapeVersion() snapshot
      final byte     kind;             // PROTO_VALUE | GETTER | UNCACHEABLE
      final Object   resolvedValue;    // method / data value from proto
      final Object   getterFn;         // set iff kind == GETTER
      PropIC(Class<?> c, Object k, int s, byte kd, Object v, Object g) {
          receiverClass=c; propertyKey=k; shapeToken=s; kind=kd; resolvedValue=v; getterFn=g;
      }
  }
  private volatile PropIC readIC;
  private volatile PropIC writeIC;      // separate slot for `obj.x = v`
  ```
- `kind` codes: `PROTO_VALUE` (data property inherited from a prototype); `GETTER`
  (accessor on a prototype); `UNCACHEABLE` (own-property, proxy, sequence receiver, etc. —
  poisoned; always fall through).

**3a.2. Cache-hit predicate.**
```java
PropIC snap = readIC;
if (snap != null
    && instance != null
    && instance.getClass() == snap.receiverClass
    && snap.propertyKey == memberName
    && snap.shapeToken == env.shapeVersion()
    && snap.kind != UNCACHEABLE
    && !hasOwnFast(instance, memberName)) {         // own-property shadow check
    switch (snap.kind) {
        case PROTO_VALUE: return snap.resolvedValue;
        case GETTER:      return RuntimeUtil.invoke(env, snap.getterFn, instance);
    }
}
// slow path below; on completion publish a fresh PropIC (plain assign — see §0)
```
- `hasOwnFast(instance, key)` is the accessor-provided "does this instance have its own
  binding for `key`?" fast check. Add it to `JSAccessor` for the receiver types that hit
  the PIC most: `JSObject`, `JsonObject`, arrays, strings, boxed numbers, `Function`,
  `Date`, `RegExp`, `Map`, `Set`, `Promise`. Other classes get `UNCACHEABLE` and pay no
  PIC-related cost.
- **Bypass PIC entirely** for `instance instanceof JSProxy` — trap semantics require full
  dispatch. Same for GaltaJS `Sequence` receivers when `sequenceEnabled` (broadcasting).

**3a.3. Miss / publish.**
- On miss, run the current `RuntimeUtil.getProperty` path *and* record what happened:
  where along the prototype chain the property was found, whether it was data or accessor,
  whether the receiver had its own binding.
- If `own-property` → publish `PropIC(kind=UNCACHEABLE, …)` to short-circuit the guard
  next time (avoids running the full lookup just to discard the cache).
- Otherwise → publish `PropIC(kind=PROTO_VALUE|GETTER, resolvedValue|getterFn, …)`.

**3a.4. Reuse the PIC on `ASTCall` receiver `foo.bar()`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTCall.java`.
- Evaluate the receiver exactly once. The PIC returns the callee value; the `this`-binding
  is the receiver expression's evaluated object — same object, no re-lookup.

**3a.5. Well-known-symbol / `.length` sites in `RuntimeUtil`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java`.
- Attach a `PropIC[]`-per-call-site (fields on the caller `ASTNode`) at hot loops calling
  `getProperty(obj, Symbol.ITERATOR)` and `getProperty(obj, "length")`. Search sites via
  grep on `Symbol.ITERATOR` under `rt/`.

**3a.6. `JSEnvironment.shapeVersion()`.** *(thread-safe)*
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/JSEnvironment.java`.
- Read on every PIC hit across threads; written by any thread performing a shape-mutating
  op:
  ```java
  private volatile int shapeVersion = 0;
  public int shapeVersion() { return shapeVersion; }
  public void bumpShapeVersion() { shapeVersion++; }
  ```
  Racing double-bump can lose an increment; net effect is still "shape changed" → next PIC
  read sees a mismatch and invalidates. Safe. Promote to `AtomicInteger` only if the bump
  ever becomes hot.
- Bump from: `Object.setPrototypeOf`, `Object.defineProperty`, `__proto__=` assignment,
  class inheritance mutation, `with` scope entry, `Array.prototype.*` extension. Search
  sites via grep on `setPrototype` and `defineProperty` under `rt/builtins/`.

**Expected win**: 10–20% on method-call-heavy micros. Every `for (…) arr.push(x)` /
`str.charCodeAt(i)` / `promise.then(…)` benefits.

### 5.2 Phase 3b — Level 2: Object shapes + slot PIC *(separate playbook)*

Own-property reads on `JSObject` cannot be sped up further without knowing *where* the
property lives in the underlying storage. Today `JSObject` is backed by `JsonObject` (a
name-keyed hash map). Level 2 introduces hidden classes ("shapes") + a slot array so PIC
hits become a single array access.

Sketch, **for a separate design doc** — do not implement inline with 3a:
```java
class JSObject {
    Shape    shape;    // maps propertyName → slotIndex; transitions cached
    Object[] slots;    // direct-indexed storage
}
class OwnIC {
    final Shape  shape;
    final Object propertyKey;
    final int    slotIndex;
}
// hot path:
OwnIC snap = ownIC;
if (instance instanceof JSObject o
    && snap != null && o.shape == snap.shape
    && snap.propertyKey == memberName) {
    return o.slots[snap.slotIndex];               // one array load
}
```

Non-trivial cost (why it isn't in-band with 3a):
- `jsonfactory/` package rewrite. `JsonObject`-backed instances stay name-keyed for JSON
  interop; only `JSObject` gets shapes.
- Every property add/delete triggers a shape transition (cache the transition tree).
- ES spec property-enumeration order must be preserved.
- Serialization / iteration must match today's behaviour bit-for-bit.
- Interaction with proxies, `Object.defineProperty` custom descriptors, sealed/frozen.

**Deliverables for 3b (later)**: design doc, migration plan for `jsonfactory`, own Phase-0
benchmark before/after, own regression matrix. Expected additional win over 3a: 15–30% on
own-property-heavy loops.

### 5.3 Verification (Phase 3a only)

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262

# Extra scrutiny areas in test262:
#   built-ins/Object/setPrototypeOf/*
#   built-ins/Proxy/*
#   language/statements/class/*
#   built-ins/Symbol/*
#   built-ins/Array/prototype/*

mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

**Success criteria**: all tests green; **10–20% speedup** on `interp_hot_property` (method
receiver flavour), `Bench_Sunspider_CryptoSHA1`, Octane classes-heavy suites. Own-property
loops (`o.x += i` micro) are largely unaffected — those wait for 3b.

---

## 6. Phase 4 — Call path specialization

### 6.1 Changes

**4a. Slot-based param binding** — already realized by Phase 2c. No new code here; this
phase captures the gain by ensuring `getParamValues` results are passed directly into the
slot array without an intermediate `VariableMap.createVariable`.

**4b. Arity-specialized `RuntimeUtil.call`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java`.
- Add `call1/call2/call3(env, function, _this, a0[, a1[, a2]])`. When `function instanceof
  BuiltinFunctionInterpreter && !hasRestOrSpread && arity matches`, invoke without
  allocating an `Object[]`. Otherwise fall back to the current varargs `call`.
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTBaseCall.java` — route
  from `call0/call1/call2/call3` (added in Phase 1d) to the matching `RuntimeUtil` fast path.

**4c. Do not pool `InterpretedFunctionRuntimeContext`.** Recorded as an explicit non-goal.
Closures / generators / `arguments` retention make pooling unsafe; JVM escape analysis
handles the short-lived common case.

### 6.2 Verification

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262

# Extra scrutiny:
#   built-ins/Function/*
#   built-ins/Function/prototype/bind/*
#   built-ins/GeneratorFunction/*
#   language/expressions/tagged-template/*   (spread edge cases)

mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

**Success criteria**: all tests green; **5–15% speedup** on `interp_recursive_fib`,
`Bench_Sunspider_ControlflowRecursive`, `Bench_UBench_functionsum`.

---

## 7. Phase 5 — Numeric specialization

### 7.1 Changes

**5a. In-node type feedback on binary ops.** *(thread-safe: shared AST)*
- Files: `parent-js/js/src/main/java/org/monflabs/galtajs/node/binaryop/ASTArithmeticOp.java`
  and siblings (`ASTAdd`, `ASTSub`, `ASTMul`, `ASTMod`, `ASTComparisonOp`).
- Type-feedback state must be a single monotonic word — no torn reads across threads.
  Use a `volatile int` bit-packed state (2 bits per operand + a "poisoned" flag), and
  update it with a CAS-free monotonic transition: state can only ever move from a more
  specific to a more general classification.
  ```java
  // bits 0-1: lhs class code, bits 2-3: rhs, bit 4: poisoned (fall through forever)
  //   0 = unknown, 1 = Integer, 2 = Double, 3 = other
  private volatile int feedback;
  ```
  Any thread observing an incompatible operand class simply publishes `feedback | POISONED`
  with a plain write — the volatile monotonicity means a poisoned value is never read as
  clean. A benign race where two threads simultaneously specialize with different classes
  will resolve to poisoned on the second write and forever fall through to the generic
  path. That is correct.
- Fast-path check: single volatile-int read at top of `evaluate`. If not poisoned and both
  operands match the recorded class exactly, take the inlined path (`Math.addExact` for
  int/int, raw `d1+d2` for double/double). Otherwise generic dispatch.
- Never "un-poison". Do not oscillate between specialized states.

**5b. Extend `Integer+Integer` fast path across `RuntimeUtil`.**
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java`.
- `add()` already fast-paths `Integer+Integer` (line 226). Mirror the same guard at the top
  of `sub`, `mul`, `mod`, `lshift`, `rshift`, and the comparison operators. These currently
  fall into `promoteNumber(env, numberType(n1), numberType(n2))` unconditionally.

**5c. Deferred.** Standard-global constant folding (`undefined` / `NaN` / `Infinity`) was
already implemented in Phase 1e — skip.

### 7.2 Verification

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262

# Extra scrutiny:
#   built-ins/Math/*
#   built-ins/Number/*
#   built-ins/BigInt/*
#   Number edge cases (Infinity, NaN, -0)

mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

**Success criteria**: all tests green; **10–20% speedup** on `Bench_Sunspider_math-cordic`,
`Bench_Sunspider_crypto-*`.

---

## 8. Risk & Parity Matrix

| Phase | Interp | InterpOpt | Transpiler | Decompiler | Rhino | Test262 | Extra scrutiny |
|-------|:------:|:---------:|:----------:|:----------:|:-----:|:-------:|----------------|
| 1a-1e |   ✓    |     ✓     |     —      |    ✓ (1e)  |   ✓   |    ✓    | none of concern |
| 2a-2c |   ✓    |     ✓     |     ✓      |     ✓      |   ✓   |    ✓    | eval, with, mapped-arguments, generators, `Function.prototype.bind` |
| 2d    |   ✓    |     ✓     |     —      |     —      |   ✓   |    ✓    | delete on shared entries, cross-scope shadowing, `VariableMap.clear` |
| 3a-3d |   ✓    |     ✓     |     —      |     —      |   ✓   |    ✓    | proxies, `setPrototypeOf`, class inheritance, well-known symbols |
| 4a-4b |   ✓    |     ✓     |     —      |     —      |   ✓   |    ✓    | arguments, spread, `Function.prototype.apply/call/bind` |
| 5a-5b |   ✓    |     ✓     |     —      |     —      |   ✓   |    ✓    | BigInt, `-0`, `NaN`, coercion asymmetry |

Any red test in any suite is a **stop-ship**. No `@Ignore` opt-outs. No feature flags to
paper over.

Any bench slower by **>3%** after any phase blocks that phase — investigate the diff, revert
if you can't attribute the cause.

---

## 9. Critical Files Reference

Grouped by phase for fast lookup.

**All phases**:
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTNode.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/interpreter/VariableMap.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/interpreter/AbstractRuntimeContext.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/interpreter/InterpretedFunctionRuntimeContext.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java`

**Phase 1**:
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/binaryop/ASTArithmeticOp.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTMember.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTAssignment.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTBaseCall.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTCall.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTNewCall.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/ConstantFoldingOptimizer.java`

**Phase 2**:
- `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/ScriptOptimizer.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/ScopeResolutionOptimizer.java` **(new)**
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/control/ASTFunction.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTVarContainer.java`

**Phase 3**:
- `parent-js/js/src/main/java/org/monflabs/galtajs/JSEnvironment.java`

**Phases 4-5**: subset of the above (already listed).

**Phase 0 benchmarks**:
- `parent-js/js-test-performance/src/main/resources/benchmarks/ubench/interp_recursive_fib.js` **(new)**
- `parent-js/js-test-performance/src/main/resources/benchmarks/ubench/interp_hot_property.js` **(new)**
- `parent-js/js-test-performance/src/main/resources/benchmarks/ubench/interp_hot_closure.js` **(new)**
- `parent-js/js-test-performance/src/main/java/org/monflabs/script/performance/BenchmarkExecutor.java`

---

## 9.5 Concurrency Test Protocol

Because the AST is shared across threads, add one dedicated stress test before merging
any phase that installs a runtime-mutable cache (2d, 3, 5a). Location:
`parent-js/js/src/test/java/org/monflabs/galtajs/rt/interpreter/PICConcurrencyTest.java`
(new).

Minimum coverage:

- **Test A — PIC snapshot atomicity.** Parse one script exercising 3a's `ASTMember` cache
  (e.g. `function f(o){return o.x}` in a loop over shapes A and B). Run N=16 threads for T=10s
  each calling `f` with alternating `{x:...}` classes. Assert every returned value matches
  what a synchronous single-thread run would have produced for that input. A torn cache
  would surface as an occasional `ClassCastException` or wrong-property value.
- **Test B — Shape invalidation visibility.** Thread 1 hot-loops `o.x`. Thread 2
  periodically `Object.setPrototypeOf(o, ...)` mutating shape. After T=5s, `env.shapeVersion()`
  must have advanced and every read on thread 1 must have returned a correct value.
- **Test C — Monotonic poisoning of 5a feedback.** Feed one binary op a mixed workload of
  int/int and double/double from two threads; assert final `feedback` is poisoned and no
  intermediate observer computed a numeric result inconsistent with the actual operands.

Test may be `@Tag("stress")` and excluded from the default fast test run, but must be
green in a scheduled CI job before any phase merges.

---

## 10. Rejected / Non-Goals

- **Bytecode / register VM / method-handle dispatch / Truffle rewrite** — out of scope.
- **`invokedynamic` / Dynalink-style dispatch in the interpreter** — this is what Nashorn
  did (each JS site emitted as a JVM `invokedynamic`, cached `MethodHandle` per site,
  HotSpot C2 inlining through the guard). It requires compiling to bytecode. GaltaJS's
  counterpart to that design is **transpiled mode** — the tree-walk interpreter cannot get
  C2 to specialize through a guarded field load no matter how cleverly the PIC is written.
  If someone wants "Nashorn-fast", the answer is `JSTranspiler`, not further interpreter
  tuning. Interpreter PIC (Phase 3a/3b) gives us the algorithmic win — O(1) lookup, no
  hashing — but not the last-mile inlining win.

  **Forward reference — `invokedynamic` proper (covered here only as far as plain-Java
  can go; see §12 for the actual transpiler track).**
  The transpiler emits Java source today, so it cannot emit `invokedynamic` directly
  (`javac` reserves that instruction for lambdas, string concat, records, pattern switch —
  no user syntax maps to it). Three routes exist, in increasing cost:
  - **Option A — `MutableCallSite` + `MethodHandle.invokeExact` from plain Java source.**
    The transpiler emits `static final MutableCallSite CS_site_N` fields plus
    `H_site_N.invokeExact(instance)` at each property/call site. The bootstrap handler
    resolves once, then installs a `guardWithTest` chain via `setTarget`. `SwitchPoint`
    handles shape invalidation cheaply. Gets ~80% of a real `invokedynamic` — no bytecode
    generation needed, no new dependencies, generated Java stays readable.
  - **Option B — ASM post-pass** that rewrites transpiler-emitted `INVOKESTATIC PIC.get(...)`
    placeholders into real `INVOKEDYNAMIC` with a bootstrap in a runtime helper. Real indy
    at the cost of an ASM dependency in `javacompiler`.
  - **Option C — Rewrite `JSTranspiler` to emit bytecode directly** (ASM / ByteBuddy /
    `java.lang.classfile`). Biggest rewrite, highest ceiling — also unlocks numeric
    unboxing and typed locals.

  Recommendation when the transpiler track opens: start with Option A; graduate to B only
  if measurements demand it. C is only justified alongside a numeric-specialization
  project.
- **`InterpretedFunctionRuntimeContext` pooling** — closures, generators via `Yielder`,
  `arguments` retention all make the context out-live the call.
- **Separate `ASTAddInt` / `ASTAddDouble` node classes** — the in-node type-feedback bit
  (Phase 5a) buys the same runtime win with a fraction of the parser/transpiler/decompiler
  surface.
- **Phase 3b (shapes + slot PIC) in this playbook** — see §5.2. Separate design doc /
  migration plan required. Not started until 3a lands and its numbers are validated.

---

## 11. Session Handoff Checklist

If you're picking this playbook up in a fresh session:

- [ ] Read §0 (context) and §1 (execution model).
- [ ] Check git log for phase commits already merged; skip completed phases.
- [ ] Run §1.1 setup and §1.2 baseline benchmark before touching code.
- [ ] Do interpreter phases in order — 0 → 1 → 2a+c → 2b → 2d → 3a → 4 → 5.
- [ ] Do transpiler phases in order — T1 → T2 → T3+T5 → T4 → T6 → T7 → T8 → T9.
      Independent of the interpreter track — either can be picked up first, though T2 and
      T6 pick up AST metadata from interpreter Phases 2a and 5a respectively (fall back to
      no-op emit if that metadata isn't yet populated).
- [ ] Never open a new phase before the previous phase's verification is green.
- [ ] Never merge a phase whose bench delta is worse than −3% vs. the previous phase.

---

## 12. Transpiler Track (parallel to Phases 0–5)

The transpiler emits Java source per AST, hands it to `javacompiler`, and loads via
`PathClassLoader`. This track keeps that pipeline — no bytecode generation — and improves
what gets emitted.

### 12.0 Baseline — what the transpiler emits today

Confirmed from source and compiled test output under
`parent-js/js/src/test/java/compiled/`:

| Pattern | Emit today | Cost |
|---|---|---|
| Local var read | `p_N[i]` (`Object[]` slot) | free ✓ |
| Global / cross-scope read | `getIdentifierValue(_ctx, "name", true)` | scope walk + hash |
| Property read | `memberGet(obj, "foo")` (see `ASTMember.java:339-347`) | full accessor + proto walk, per hit |
| Chained `a.b.c` | 3 × `memberGet` | 3× above |
| Function call | `invokeFunction(_ctx, f, thisArg, new Object[]{…})` | Object[] alloc per call |
| Method call `o.m(x)` | `invokeMethod(_ctx, o, "m", args)` | property lookup + array alloc |
| Arithmetic `a+b` | `add(a,b)` (`ASTAdd.transpileJavaExpression`) | full JS coercion |
| For-of | Iterator hoisted upfront ✓ | good |
| `ScriptOptimizer` | commented out in `JSTranspiler.java:111-113` | dead constant folding on this path |
| Constants pool | present (`JSTranspiler.createConstantPool`, line ~253) | ✓ |

Register in the same regression matrix as the interpreter phases: any red test in
`AllGaltaJSTranspilerTests`, `AllGaltaJSDecompiledTests`, `js-test-rhino`, `js-test-test262`
is a stop-ship.

### 12.1 Setup / verification template

For every T-phase:

```bash
cd /Users/priand/phildev/monflabs/source/Galta-Java/galta
mvn install -pl parent-js/js --also-make -DskipTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests
mvn test -pl parent-js/js-test-rhino
mvn test -pl parent-js/js-test-test262

# Benchmark in transpiled profile (make sure BenchmarkExecutor is running under
# JSScriptExecutor with the transpiled context, not interpreted)
mvn -pl parent-js/js-test-performance test \
    -Dtest=AllUBench,AllSunSpider,AllOctaneMaster2
```

Baseline transpiled-mode numbers **must** be captured before T1 so per-phase deltas are
meaningful. Same >3% regression gate as §1.4.

### 12.2 T1 — Turn on `ScriptOptimizer` in the transpiler path

- File: `parent-js/js/src/main/java/org/monflabs/galtajs/transpiler/JSTranspiler.java`
  lines ~111-113 — currently commented-out `ScriptOptimizer` / `UnreachableCodeRemovalOptimizer`
  invocations. Enable them.
- Because interpreter Phase 2a's `ScopeResolutionOptimizer` (if landed) also plugs into
  `ScriptOptimizer`, this also brings resolved-slot metadata to the transpile stage — a
  precondition for T2.
- Verify existing constant-folding tests still match expected emit (some `AllGaltaJS...`
  golden output may need refresh — regenerate goldens, review the diff manually to confirm
  it's only folded constants and dead-code removal, then commit).

**Success criteria**: all suites green; **5–10%** speedup on any transpiled benchmark that
had unfolded constants or unreachable code (e.g. `Bench_UBench_deadloop`, `Bench_Sunspider_math-cordic`).

### 12.3 T2 — Emit direct slot access for resolved identifiers

Depends on: interpreter Phase 2a (populates `ASTIdentifier.scopeHops` / `slotIndex` /
`resolvedVarType`).

- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java` —
  `getIdentifierReadAccessor()` (~line 278) and its assign counterpart. When
  `scopeHops >= 0`, emit `((TranspiledContext) _ctx.parentN(<hops>)).slots[<idx>]`
  instead of `getIdentifierValue(_ctx, "<name>", true)`.
- Add `TranspiledRuntimeContext.parentN(int hops)` (fast walk) or expose the parent chain
  as an array so the walk is a single indexed load.
- If `scopeHops == -1` (unresolved: eval / with / global late-bind) → fall back to today's
  `getIdentifierValue`.

**Success criteria**: all suites green; **20–40% speedup** on any bench dominated by
cross-scope reads (`AccessGlobalTest`-shaped workload, closure-heavy micros).

### 12.4 T3 + T5 — Site-local PIC in emitted Java + method-call fold

Same shape as the interpreter Phase 3a PIC, but emitted per site in generated Java. **No
`invokedynamic`, no `MutableCallSite`, no ASM** — plain fields + `volatile` refs in a
runtime `PropSite` helper class.

- New runtime helper:
  `parent-js/js/src/main/java/org/monflabs/galtajs/rt/transpiler/PropSite.java`
  ```java
  public final class PropSite {
      private final Object propertyKey;   // interned
      private volatile PIC pic;
      public PropSite(String key) { this.propertyKey = key.intern(); }
      public Object get(JSEnvironment env, Object instance) { … Phase-3a Level-1 hot path … }
      public Object invoke(JSEnvironment env, Object instance, Object[] args) { … method fast path … }
      public void set(JSEnvironment env, Object instance, Object value) { … }
      private static final class PIC { final Class<?> receiverClass; final int shapeToken;
          final byte kind; final Object value; final Object getterFn; /* … */ }
  }
  ```
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTMember.java` —
  `transpileChainingNode`: instead of `memberGet(chain, "foo")`, emit:
  ```
  private static final PropSite PS_17 = new PropSite("foo");
  … PS_17.get(env, chain) …
  ```
  The `PS_N` fields live on the generated class; `N` is a per-class monotonic index —
  add to `JSTranspilerGeneratorContext` (same mechanism as the constant pool).
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTCall.java` —
  `transpileChainingNode` (~line 249): for the `o.m(args)` shape, emit
  `PS_17.invoke(env, o, argsArray)` in one step. Kills the "get property then call it"
  double-dispatch and one accessor round-trip.
- **Bypass** for `instance instanceof JSProxy` and (when enabled) `Sequence` — same rule
  as interpreter Phase 3a.
- Requires `JSEnvironment.shapeVersion()` — reuse the one from interpreter Phase 3d.

**Success criteria**: all suites green; **15–30% speedup** on `Bench_Sunspider_CryptoSHA1`,
Octane classes-heavy suites, `interp_hot_property` (transpiled profile), any method-heavy
loop.

### 12.5 T4 — Arity-specialized invoke helpers

- New: `RuntimeUtil.invokeFunction0/1/2/3` and `invokeMethod0/1/2/3` — no `Object[]`
  allocation on the call path.
- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTBaseCall.java`
  `transpileParams` (line ~145) — when `!hasSpread && parameters.length <= 3`, emit the
  arity-specialized helper instead of `new Object[]{…}`.
- File: `ASTCall.transpileChainingNode` — mirror the arity branch for method calls (route
  to `PS_N.invoke0/1/2/3` if T3 is in, otherwise `invokeMethod0/1/2/3`).
- Arity > 3 keeps the current path.

**Success criteria**: all suites green; **5–15% speedup** on `interp_recursive_fib`
(transpiled), `Bench_UBench_functionsum`. Measurable GC reduction in transpiled JMH runs.

### 12.6 T6 — Numeric specialization in emit, gated on type-feedback

Depends on: interpreter Phase 5a (populates `ASTArithmeticOp.feedback` bits) — or, more
robustly, a parse-time `JSType` classification when both operands are provably numeric
(e.g. integer literal, `length`, a locally-typed variable).

- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/binaryop/ASTAdd.java`
  (and siblings — `ASTSub`, `ASTMul`, `ASTMod`, comparisons): in `transpileJavaExpression`,
  when both operands are `JSType.INTEGER` and `feedback` is non-poisoned int/int, emit:
  ```java
  Integer.valueOf(((Integer)a).intValue() + ((Integer)b).intValue())
  ```
  or the `Math.addExact` variant when overflow-promotion is desired. HotSpot escape
  analysis erases the box on the hot path.
- Same for `DOUBLE + DOUBLE`.
- Fall back to `add(a,b)` for mixed / unknown / string-concat / BigInt / BigDecimal.
- Because transpiled code is compiled by `javac`, correctness is easy to eyeball: the
  emitted method compiles or it doesn't.

**Success criteria**: all suites green (extra scrutiny: `built-ins/Math`, `built-ins/BigInt`,
`-0` / `NaN` edge cases); **10–20% speedup** on `Bench_Sunspider_math-cordic`,
`Bench_Sunspider_crypto-*`.

### 12.7 T7 — Local hoisting of repeated identifier reads

Independent of T2 (both help but for different reasons). New optimizer pass:
`parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/LocalHoistingOptimizer.java`.

- Walk each basic block (region between control-flow boundaries and function calls).
- For each `ASTIdentifier` read to a resolved binding, if:
  - the binding is not reassigned within the block, AND
  - no unresolved / eval / with call sits between reads, AND
  - the identifier is read more than once,
  then insert a synthetic `let _tN = <read>` at first use and rewrite subsequent reads to
  `_tN`.
- Runs after `ScopeResolutionOptimizer`; benefits both transpiler emit and interpreter
  hot loops (bonus win on interpreter mode).
- Golden-output diffs must be reviewed manually to confirm equivalence.

**Success criteria**: all suites green; **5–15% speedup** on `AccessGlobalTest`-shaped
loops (transpiled and interpreted).

### 12.8 T8 — Intern property-name literals

- File: `parent-js/js/src/main/java/org/monflabs/galtajs/transpiler/JSTranspiler.java` —
  extend the constant pool (line ~253) to include property-name strings encountered by
  `ASTMember` / `ASTCall`. Emit `private static final String KEY_foo = "foo";` once per
  class (`String` literals in Java are already interned, so this is mostly ergonomics —
  the win comes from the fact that `PropSite.propertyKey` compare then reduces to `==`).
- File: `ASTMember.transpileChainingNode` — reference `KEY_foo` instead of the literal
  `"foo"` at every site. Small but composes with T3 (PIC guard becomes `==`).

**Success criteria**: all suites green; measurable but small (**1–3%**) speedup on
property-heavy benchmarks. Value is compounding via T3.

### 12.9 T9 — Emit slot-chain closure captures

Depends on: T2 (parent-chain walk API), interpreter Phase 2b (VariableEntryArray slot
addressing).

- File: `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java` —
  `getIdentifierReadAccessor` currently walks `for(JSTranspilerGeneratorContext ctx=…)`
  at *emit* time (that's fine) and emits a lookup via `getOwnVariable` at *run* time
  (that's what we're removing).
- When `scopeHops >= 0`, emit `((TranspiledContext) _ctx.parentN(<hops>)).slots[<idx>]`
  (same as T2, but for captures from inner functions crossing multiple lexical scopes).
- Non-goal here: turning captures into a dedicated closure object. The transpiler is not
  emitting `class Closure_N { … }`. Slots stay on the runtime context objects; the walk
  just goes through fewer of them.

**Success criteria**: all suites green; **10–20% speedup** on nested-closure workloads
(`interp_hot_closure` transpiled profile, decorators, currying-heavy code).

### 12.10 Transpiler track — regression / parity notes

- **`AllGaltaJSDecompiledTests`** runs `AST → decompile → reparse → run`. Nothing in the
  transpiler track changes AST shape (except T7's synthetic `let`, which decompiles
  losslessly to a real `let` in the source). Still: run this suite on every T-phase.
- **Rhino parity**: transpiled mode's output *must* match Rhino / interpreted mode. Any
  divergence caused by numeric specialization (T6) is a stop-ship — likely a coercion bug
  in the emitted fast path.
- **Golden-file drift**: T1, T7, T8 will change the emitted Java source. Regenerate
  goldens per phase; the reviewer's job is to eyeball the diff — do not blindly accept.
- **Non-goal here**: real `invokedynamic` (§10 forward-ref), tail-call optimization,
  primitive-typed locals throughout (would require rewriting the local-slot infrastructure
  from `Object[]` to typed arrays — bigger project).

### 12.11 Transpiler track — file reference

- `parent-js/js/src/main/java/org/monflabs/galtajs/transpiler/JSTranspiler.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/transpiler/TranspilerJavaBuilder.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/transpiler/TranspiledGlobalRuntimeContext.java`
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/transpiler/PropSite.java` **(new — T3)**
- `parent-js/js/src/main/java/org/monflabs/galtajs/optimizer/LocalHoistingOptimizer.java` **(new — T7)**
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTIdentifier.java` (T2, T9)
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/ASTMember.java` (T3, T8)
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTCall.java` (T3, T4, T5)
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/call/ASTBaseCall.java` (T4)
- `parent-js/js/src/main/java/org/monflabs/galtajs/node/binaryop/ASTAdd.java` and
  siblings (T6)
- `parent-js/js/src/main/java/org/monflabs/galtajs/rt/RuntimeUtil.java` (T4)
