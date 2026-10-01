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
- Every `Test262BaseTest.FILTER` / `TRANSPILER_ONLY_FILTER` entry must be
  explained here. Both are empty: every test262 file in scope passes,
  negative tests included (checked strictly: expected error type and phase).
- Snippets show the current (non-conformant) behavior next to what the spec
  requires, so they double as regression checks once someone picks up the
  fix.

## Current test262 status

Re-verified 2026-09-30 with the strict negative-test harness (expected
error type and phase), an empty `FILTER` and no feature skipped:
interpreted over the whole suite, optimizer per top-level directory
(`annexB`, `language`, `built-ins`), transpiled in smaller chunks, Maven
3.9.9, JDK 21 (see "How to regenerate" below for the commands):

| Mode | Result |
|---|---|
| Interpreted (`Test262AllTest`) | 0 failing files |
| Interpreted + optimizer (`All262nterpretedOptimizedTests`) | 0 failing files |
| Transpiled (`All262TranspilerTests`) | 0 failing files |

Every `features:` tag of the pinned checkout is supported:
`Test262BaseTest.UNSUPPORTED_FEATURES` is empty. Out of scope, by path:
`intl402/` (GaltaJS has no ECMA-402) and `staging/`. Without ECMA-402,
`Temporal` supports the ISO 8601 calendar only and its `toLocaleString()`
methods return the ISO string.

## Language

### `accessor #x` (private auto-accessor) behaves as a plain private field

Public auto-accessors (`accessor x = 1`, static or not, computed names
included) are implemented per the decorators proposal. A *private* one
(`accessor #x = 1`) is parsed but stored as an ordinary private field - no
private getter/setter pair is minted. Unobservable from outside the class
body (a private name is only reachable inside it), so deliberately left as is.

## GaltaJS extension: import and export in scripts

By default GaltaJS accepts `import`/`export` declarations at the top level of
a script, not only of a module (`JSConfiguration.supportImportExportInScripts()`,
`true` by default), so scripts evaluated with `evaluateScript()` can import
modules. The specification makes them a `SyntaxError` outside module code;
build the environment with `.supportImportExportInScripts(false)` for that
behavior. The test262 environment does, so `language/global-code/{export,import}.js`
pass.

```js
import fs from 'node:fs'; // in a script: spec SyntaxError -- GaltaJS: accepted by default
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
   under `js-test-test262/src/test/java/compiled/` first. `-Dtest262.path`
   takes a comma-separated list of directories (`language/statements`,
   `built-ins/Temporal/PlainDate,...`) for smaller chunks. A transpiled
   chunk longer than the parent pom's 900 s fork budget ends with "There was
   a timeout in the fork" (exit 1) after its results are printed: read the
   `Tests run:` line and the per-file `Errors:` lines, not the exit code.
3. Any failing file not named in an entry above is either a new gap (add
   it) or a regression (fix it). `-Dtest262.noFilter=true` only matters if
   `FILTER` is non-empty; it also disables `EXCLUDED_FROM_TESTS`
   (fixtures/harness/intl402/staging), so don't use it for a normal sweep.
4. Run under low concurrent system load - a heavy unrelated process on the
   same machine has repeatedly produced spurious async/timing failures;
   re-run any unexpected failure in isolation before treating it as real.
