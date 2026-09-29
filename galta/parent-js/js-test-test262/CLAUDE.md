# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`js-test-test262` runs GaltaJS against the official TC39 test262 suite, checked out under `test262/` at a commit pinned by the `galtajs.test262.commit` property in this module's `pom.xml` (not a git submodule - see `parent-js/README.md`). This measures spec compliance beyond what the Rhino suite covers.

## Fetching the suite

Automatic: the first `mvn test`/`mvn install` on this module fetches `test262/` on its own (the `fetch-externals` profile auto-activates when it's missing). To fetch ahead of time, or to force a re-fetch after bumping `galtajs.test262.commit`:

```bash
mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources
```

## Running Tests

`mvn test`/`mvn install` on this module - including a full `mvn clean install` from the repo root - does NOT run the real suite by default (it runs `AllBuildNoTest`, a no-op that just proves the module compiles). `All262Tests` runs all 3 execution modes (interpreted, interpreted+optimized, transpiled) against the WHOLE suite - hours, not minutes - so it's opt-in only:

```bash
# Quick check, interpreted mode only (~10 min)
mvn test -pl parent-js/js-test-test262 -Dtest=Test262AllTest

# Full suite, all 3 modes (hours) - also foldable into a release build,
# e.g. mvn clean install -P javadoc,sources,codesigning,test262
mvn test -pl parent-js/js-test-test262 -Ptest262
```

## Test Structure

```
test/
├── All262Tests                       # Suite entry point (all 3 modes, -Ptest262)
├── All262InterpretedTests            # Interpreted mode
├── All262nterpretedOptimizedTests    # Interpreted + optimizer
├── All262TranspilerTests             # Transpiler mode
├── AllBuildNoTest                    # Default no-op for plain builds
└── test262/
    ├── Test262AllTest                # Runs the full test262 tree
    ├── Test262OneTest                # Runs the directories given by -Dtest262.path=a,b
    └── Test262BaseTest               # Shared file-walking logic

org/monflabs/galtajs/test/test262/
└── GlobalTest262Environment          # JSEnvironment configured for test262
```

`Test262BaseTest` extends `BaseTestSuiteTest` (from the `js-test-suite` test-jar).

## Test262 Harness

The test262 suite requires harness files (from `test262/harness/`) to be loaded before each test. `GlobalTest262Environment` handles this setup.

Test262 files carry YAML front-matter that specifies expected outcomes (`negative` tests, `flags`, `features`). The test runner parses this metadata to determine whether a test is expected to throw, what features it requires, etc.

Negative tests are checked strictly (`Test262BaseTest.negativeMismatch()`): the thrown error must have the expected constructor name (`SyntaxError`, `ReferenceError`, ...), a `phase: parse` error must come from parsing (`env.createScript()`, before anything runs; `BaseTestSuiteTest.execPhase` tracks the phase), and a negative test that throws nothing fails. Reaching `$DONOTEVALUATE()` is always a failure. Each mismatch is logged as `NEGATIVE MISMATCH <file>: <reason>`.

In transpiled mode the harness (`sta.js`, `assert.js`) and the test are compiled as one program (`Test262BaseTest.combineShellAndScript()`), except for `flags: [raw]` files, which run exactly as written: they use no harness function, and several depend on their own first line (a `"use strict"` directive prologue, an HTML close comment only valid at the start of the source). Interpreted mode always runs the harness as a separate script.

To see what the engine really does with a parse-negative file, run with `-Dtest262.stripDoNotEvaluate=true`: the `$DONOTEVALUATE()` guard is removed, so the mismatch says whether the invalid code is rejected at run time or accepted. It is a diagnostic only (a correct engine fails nothing either way).

After a transpiled run, clean the generated sources with `find src/test/java/compiled -name '*.java' -delete` (a plain `rm compiled/*.java` overflows the argument list).
