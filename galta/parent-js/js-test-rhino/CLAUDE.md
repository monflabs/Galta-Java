# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`js-test-rhino` runs GaltaJS against the ECMA test suite originally used by Mozilla's Rhino engine. Tests are `.js` files checked out under `rhino/` at a commit pinned by the `galtajs.rhino.commit` property in this module's `pom.xml` (not a git submodule - see `parent-js/README.md`).

## Fetching the suite

Automatic: the first `mvn test`/`mvn install` on this module fetches `rhino/` on its own (the `fetch-externals` profile auto-activates when it's missing). To fetch ahead of time, or to force a re-fetch after bumping `galtajs.rhino.commit`:

```bash
mvn -pl parent-js/js-test-rhino -Pfetch-externals generate-test-resources
```

## Running Tests

Maven surefire is configured to run only `AllEcmaTests`:
```bash
# from galta/
mvn test -pl parent-js/js-test-rhino
```

**To run or debug a single test file**, edit `EcmaOneTest.TEST_FILE` and run the class:
```java
// src/test/java/tests/ecma/EcmaOneTest.java
public static final String TEST_FILE = "ecma/Expressions/11.4.8.js";
```

`EcmaOneTest.main()` launches the visual JS debugger, useful for stepping through a failing test interactively.

## Test Structure

```
tests/
├── AllEcmaTests              # Suite entry point (runs interpreted + optimized + transpiler)
├── AllEcmaInterpretedTests   # Interpreted mode only
├── AllEcmaInterpretedOptimizedTests  # Interpreter + optimizer
├── AllEcmaTranspilerTests    # Transpiler (JS→Java) mode
├── ecma/
│   ├── EcmaAllTest           # Runs ecma/ and ecma_2/ folders
│   ├── EcmaAllDevelopingTest # Same, for work in progress (not in the suites)
│   ├── EcmaOneTest           # Runs a single file (edit TEST_FILE)
│   └── EcmaBaseTest          # Shared logic + FILTER and FILTER_ERRORS arrays
├── rhino/
│   ├── RhinoTestCase         # Base of the two tests below
│   ├── RhinoShellTest        # Rhino shell compatibility tests
│   └── JavaPackageTest       # Java package access from scripts
└── rhinoes6_testsrc/
    ├── JsAllTest             # Rhino ES6 test set (testsrc/jstests)
    ├── JsTestBaseTest        # Base + FILTER for ES6 tests
    └── JsTestOneTest         # Single-file runner for ES6 tests
```

The environment and the Rhino-specific libraries are in `org.monflabs.galtajs.test.rhino` (same test source tree).

## Test Environment

`RhinoTestEnvironment.newBuilder()` returns a `JSEnvironment.Builder` (with `supportParseIntOctal(true)`) that registers:
- `StandardLibrary` — standard GaltaJS stdlib
- `UnitTestLibrary` — `assertEquals`, `assertThrows`, etc.
- `RhinoShellLibrary` — `print`, `load`, `gc`, `readFile`
- `RhinoTestLibrary` — Rhino-specific test helpers
- `JavaPackageLibrary` — `java.lang.*` access from scripts
- `JavaLibrary` — Java interop

`RhinoTestEnvironment.create()` builds it and runs the `toSource()` polyfill (`polyfills/toSource.js`).

The environment is built without `JSEnvironment.Builder.enableGaltaJSExtensions()`, so tests run in standard (non-GaltaJS-extended) mode.

## Known Skipped Tests

`EcmaBaseTest.FILTER` lists test files that are intentionally skipped, each with a comment explaining why (e.g., Rhino-specific extensions, known spec deviations, unsupported deprecated features). Before adding a new skip, check whether the failing behaviour is a GaltaJS intentional divergence or a bug to fix.

`EcmaBaseTest.FILTER_ERRORS` lists files that are run but whose failures are ignored.
