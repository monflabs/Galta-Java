# Known ECMAScript Compliance Gaps

This document is the single record of where GaltaJS still deviates from the
ECMAScript specification, as measured against the
[TC39 test262](https://github.com/tc39/test262) suite
(`parent-js/js-test-test262`, pinned to the commit in that module's
`pom.xml`). Every entry below is a **current, verified** limitation: what
fails, a minimal reproduction, the root cause when known, and the test262
files affected.

Rules for keeping it accurate:

- **Add** an entry when a new deviation is discovered and deliberately left
  unfixed; **remove** it when the gap is fixed. Never turn a fixed entry
  into a changelog note - fix narratives belong in commit messages, or in
  the archived history at
  `docs/GaltaJS/Architecture/Notes/Test262KnownGapsHistory.md`.
- `Test262BaseTest.FILTER` / `TRANSPILER_ONLY_FILTER` must stay **empty**
  (they are, as of 2026-09-09): the suite runs unfiltered, so this document
  is the only place a known failure is recorded. If a `FILTER` entry ever
  has to be added (a pathological file, e.g. a deep-recursion hang), name it
  here too.
- Snippets show the current (non-conformant) behavior next to what the spec
  requires, so they double as regression checks once someone picks up the
  fix.

## Current test262 status

Re-verified 2026-09-10 (see "How to regenerate" below for the commands;
the transpiled and optimizer rows are from the 2026-09-09 full sweeps plus
directory-scoped transpiled runs of everything the source-phase-imports /
import-bytes work touched):

| Mode | Result |
|---|---|
| Interpreted (`Test262AllTest`) | 0 failing files |
| Interpreted + optimizer (`All262nterpretedOptimizedTests`) | 0 failing files |
| Transpiled (`All262TranspilerTests`) | 0 failing files |

The `features:` tags in the next section are skipped wholesale and are not
part of these counts. With the `cross-realm` skip lifted, the same sweeps
were 26 / 26 / 33 failing files, all in the families listed in the
appendix at the end of this document.

## Unsupported features (skipped wholesale)

`Test262BaseTest.UNSUPPORTED_FEATURES` skips every file carrying one of
these `features:` tags. File counts are from the pinned test262 checkout.

| Tag | Files | Why |
|---|---|---|
| `Temporal` | ~6700 | The whole `Temporal.*` namespace is unimplemented (no classes exist). |
| `ShadowRealm` | 64 | Unimplemented: a new global constructor plus callable-boundary wrapping on top of the multi-realm support (see the appendix). |
| `tail-call-optimization` | 35 | Proper tail calls are not implemented in either execution mode (would need trampolined calls in the interpreter and the transpiler). |
| `cross-realm` | 203 | Partially supported (26 of the 203 files fail interpreted, 33 transpiled); the rest needs a "current realm" switch on builtin entry, not planned. Details in the appendix at the end. |

## Language

### `accessor #x` (private auto-accessor) behaves as a plain private field

Public auto-accessors (`accessor x = 1`, static or not, computed names
included) are implemented per the decorators proposal. A *private* one
(`accessor #x = 1`) is parsed but stored as an ordinary private field - no
private getter/setter pair is minted. Unobservable from outside the class
body (a private name is only reachable inside it), so deliberately left as is.

### A class expression as a computed member name inside another class body crashes

Evaluating a class expression while another class body is being defined -
as a computed key of any element of the outer class - fails with a Java
`NullPointerException` (`ASTClassMethod.getFunctionDecl()` on a null
`ctor`), instead of producing the inner class as the key. The same class
expression in an object literal's computed key, or hoisted into a variable
first, works.

```js
class C { get [class {}]() { return 1; } } // spec: fine -- GaltaJS: Java NullPointerException
var k = class {}; class D { get [k]() {} }  // works
({ get [class {}]() {} });                  // works
```

Found 2026-09-09 while probing computed member names; no test262 file
covers it (the `cpn-*-accessors-computed-property-name-from-*.js` family has
no `class-expression` variant).

## Not implemented (separate features, not conformance failures)

### RegExp Annex B legacy static properties never track a match

`RegExp.$1`-`$9`, `input`, `lastMatch`, `lastParen`, `leftContext`,
`rightContext` exist with the right accessor shape and default to `""`, but
no `exec()`/`match()`/`replace()`/`split()` call updates them. This property
set is no longer in core ECMA-262 (it lives in the separate legacy-features
proposal); test262's `annexB/built-ins/RegExp/legacy-accessors/*` files only
check the accessor shape, so all of them pass and none would flip. A natural
hook exists in `RegExp.java`'s delegating methods (where all three regex
engines converge), plus `RegExpPrototype`'s `Symbol.split` fast path.

```js
/(a)(b)/.exec("ab");
RegExp.$1; // legacy proposal: "a" -- GaltaJS: ""
```

## Performance (not conformance)

### Class bodies disable the scope-resolution fast path for their enclosing function

`ASTClassDecl.evaluate()` wraps class-body evaluation in extra
`InterpretedBlockRuntimeContext` frames (different ones for the
definition-time and per-instance passes). Rather than model each precisely,
`ScopeResolutionOptimizer.scanForPoison()` treats any function/program
subtree that directly contains a class declaration/expression as poisoned,
so every identifier resolving through that scope takes the slow, name-based
lookup. Correct, just slower for hot code that defines classes inside
functions.

---

## How to regenerate

All commands run from `galta/`. The test262 module compiles against the
**installed** `js` jar, so install the engine first
(`mvn install -pl parent-js/js -Dmaven.test.skip=true` if the `js` module's
own tests should be skipped).

1. Interpreted mode, the routine check (~10 min):
   `mvn test -pl parent-js/js-test-test262 -Dtest=Test262AllTest -DforkedProcessTimeoutInSeconds=3000`
   (the bare `mvn test` on this module runs `AllBuildNoTest`, a no-op).
2. The other two modes (each ~2.5 h): `-Dtest=All262TranspilerTests` and
   `-Dtest=All262nterpretedOptimizedTests` (the class name carries a
   historical typo). If a full run gets killed, use directory-scoped runs
   instead: set `Test262OneTest.TEST_FILE` to `"language"`, `"built-ins"`
   and `"annexB"` in turn and run `-Dtest=AdHocProbeTranspilerTests` /
   `-Dtest=AdHocProbeInterpreterTests`. Delete any stale generated files
   under `js-test-test262/src/test/java/compiled/` first.
3. Any failing file not named in an entry above is either a new gap (add
   it) or a regression (fix it). `-Dtest262.noFilter=true` only matters if
   `FILTER` is non-empty; it also disables `EXCLUDED_FROM_TESTS`
   (fixtures/harness/intl402/staging), so don't use it for a normal sweep.
4. Run under low concurrent system load - a heavy unrelated process on the
   same machine has repeatedly produced spurious async/timing failures;
   re-run any unexpected failure in isolation before treating it as real.

---

## Appendix: multiple realms (`features: [cross-realm]`, skipped wholesale)

A second realm is a second `JSEnvironment` with its own root context
(`JSEnvironment.getRealmContext()`), which `$262.createRealm()` exposes as
`.global` and `.evalScript`. `GetFunctionRealm` (`RuntimeUtil.getFunctionRealm()`:
bound function -> target, Proxy -> target, else the object's own
environment) drives `GetPrototypeFromConstructor` for every builtin
constructor, `new other.Function()` creates its function in `other`, an
`eval` reached through another realm's `eval` runs in that realm, and
`ArraySpeciesCreate` ignores another realm's `%Array%`. That leaves
26 of the 203 tagged files failing (33 in transpiled mode). The tag is
skipped wholesale so that the routine sweeps report zero; to measure it,
temporarily remove `"cross-realm"` from `Test262BaseTest.UNSUPPORTED_FEATURES`
and run the sweep - what fails, by family, as of 2026-09-09:

### Errors thrown by a builtin come from the caller's realm, not the builtin's

`RuntimeUtil.typeError()`/`rangeError()`/... resolve the error constructor
through the ambient thread-local `JSContext` - the realm of the *calling*
code. Per spec an error is created in the *current* realm, which becomes the
builtin's own realm the moment it is invoked; GaltaJS has no "current realm"
switch on builtin entry (adding one means re-establishing the ambient
context - executor, microtask queue included - around every native call
whose realm differs, which was judged too invasive for a test-only benefit).
Affects every test that does `assert.throws(other.TypeError, ...)` against a
builtin from `other`: `RegExp/prototype/*/cross-realm.js` (9 flag getters),
`String/prototype/{toString,valueOf}/non-generic-realm.js`,
`Function/prototype/apply/{argarray-not-object,this-not-callable}-realm.js`,
`Function/internals/{Call/class-ctor,Construct/derived-return-val,Construct/derived-this-uninitialized}-realm.js`,
`ThrowTypeError/distinct-cross-realm.js`, `Error/prototype/stack/setter-cross-realm.js`,
`annexB/built-ins/RegExp/prototype/compile/this-cross-realm-instance.js`,
`Function/call-bind-this-realm-value.js`; *transpiled only*: the private
brand-check `TypeError`s in
`language/expressions/class/private-*-multiple-evaluations-of-class-realm.js`
(5 files - interpreted mode happens to throw through the class's own realm
there).

```js
var other = $262.createRealm().global;
try { other.String.prototype.toString.call(1); } catch (e) {
  e instanceof other.TypeError; // spec: true -- GaltaJS: false (it is the caller realm's TypeError)
}
```

### Boxed primitives are tracked per environment

A wrapper object (`new other.Boolean(false)`, `other.Object(other.BigInt(1))`)
is a primitive plus an entry in *that* environment's `PrimitivePropertyMap`;
another realm's accessor sees only the primitive, so `instanceof`,
prototype changes and prototype method lookups (e.g. a `toJSON` installed on
`other.BigInt.prototype`) don't apply across realms:
`Proxy/get-fn-realm-recursive.js`, `JSON/stringify/value-bigint-cross-realm.js`;
*transpiled only*: `language/types/reference/{get,put}-value-prop-base-primitive-realm.js`
(a primitive base's property lookup boxed in the other realm).

### Narrower prototype-realm cases

- `GeneratorFunction`/`AsyncGeneratorFunction` `proto-from-ctor-realm-prototype.js`:
  the created generator function's own `.prototype` object must inherit from
  `newTarget`'s realm's `%GeneratorPrototype%`; it inherits from the
  constructor's.
- `Function/prototype/bind/get-fn-realm.js`: `Reflect.construct(realm3.Date, [], boundFn)`
  where the bound target comes from `realm1` - the `Date` instance gets a
  prototype that is not `realm1.Date.prototype` (not yet diagnosed).
- `language/expressions/super/realm.js`: `super()` into a plain base function
  with a `newTarget` from another realm resolves `%Object.prototype%` from
  the base function's realm rather than `newTarget`'s.

