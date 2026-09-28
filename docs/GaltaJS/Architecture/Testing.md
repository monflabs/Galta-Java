# Testing and compliance

The engine is verified by its own JavaScript test suite run in four execution modes, by two external conformance suites (TC39 test262 and Mozilla Rhino's ECMA suite) run in three modes, by a benchmark harness comparing it with other JVM engines, and by the documentation samples. Deliberate deviations are catalogued in [Known ECMAScript gaps](/GaltaJS/KnownGaps).

## The `js` module suite

`tests.AllGaltaJSTests` (the only class surefire runs by default, together with `AllDocExamplesTests`) composes four suites over the same test list (`GaltaJSTestSuite.addStandardTests`), each toggling static flags on `tests.BaseProjectTestCase`:

| Suite | Flags | What it proves |
|---|---|---|
| `AllGaltaJSInterpreterTests` | none | interpreted semantics |
| `AllGaltaJSInterpreterOptimizedTests` | `_EXECUTE_OPTIMIZED` | the AST optimizer preserves them (an optimization trace is dumped at the end) |
| `AllGaltaJSTranspilerTests` | `_EXECUTE_JAVATRANSPILER` (+ `_TRANSPILER_SAVE_CLASS`) | transpiled code matches; generated Java is written under `src/test/java/compiled/` for inspection (excluded from compilation) |
| `AllGaltaJSDecompiledTests` | `_EXECUTE_DECOMPILER` | parse, `decompile()`, re-parse, run: the printer round-trips |

Conventions: each test class extends `BaseProjectTestCase` (JUnit 3 style over `org.monflabs.tests.__BaseTestCase`) and calls `execute()`, which loads the co-located `.js` resource with the same package and base name; `util.GlobalTestEnvironment` builds the reference embedding (`JavaLibrary`, `UnitTestLibrary`, a `StaticLibrary` exposing `obj`, `javaEX`, `javaTest`); `tests.galtajs.GaltaJSTestCase` adds `enableGaltaJSExtensions()`. Test scripts assert with `library/UnitTestLibrary` (`assertEquals`, `assertThrows`, `assertParseError`, `SPARSE`/`EMPTY` array helpers, `loadJsonResource`...), whose failures are `JSRuntimeUncatchableException`s. Before compiling, `BaseProjectTestCase` runs `ScriptPreProcessor` with the symbols `INTERPRETER`, `TRANSPILER`, `REGEXP_JDK` and `REGEXP_JONI`, so one `.js` file can adapt per mode.

```bash
# from galta/
mvn test -pl parent-js/js                        # AllGaltaJSTests + AllDocExamplesTests
mvn test -pl parent-js/js -Dtest=AllGaltaJSInterpreterTests
```

## Documentation samples

`doc_examples.AllDocExamplesTests` (`src/test/java/doc_examples/`) runs every sample referenced from these docs; it is included in the surefire configuration so a build fails when a documented snippet stops working. Samples are plain JUnit 3 classes over `__BaseTestCase` with the JavaScript embedded in text blocks; `DocExampleSupport` provides the shared helpers.

## test262 (`js-test-test262`)

- The suite is fetched at the commit pinned by `galtajs.test262.commit` in the module `pom.xml` (not a git submodule); the `fetch-externals` profile auto-activates when the checkout is missing, and `mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources` forces a refresh.
- `test.test262.Test262BaseTest` (over `tests.suite.BaseTestSuiteTest` from `js-test-suite`) loads `harness/sta.js` and `assert.js`, parses the YAML front matter, loads `includes:`, honors `negative:` expectations and `flags`, and skips the features in `UNSUPPORTED_FEATURES`; `FILTER` and `TRANSPILER_ONLY_FILTER` list paths to skip (both empty at the time of writing, so `KnownGaps.md` is the only record of non-conformance). `Test262TestLibrary` provides `print` and the `$262` host object.
- Modes: `All262InterpretedTests`, `All262nterpretedOptimizedTests` (sic), `All262TranspilerTests`, composed by `All262Tests` through each class's static `suite()`.
- The default surefire include is the no-op `AllBuildNoTest`; `-Dtest=Test262AllTest` runs a quick interpreted-only pass and the `test262` Maven profile (`-Ptest262`) switches the default to `All262Tests` for release builds (hours).

## Rhino ECMA suite (`js-test-rhino`)

Same pinned-commit mechanism (`galtajs.rhino.commit`). Suites: `AllEcmaTests`, `AllEcmaInterpretedTests`, `AllEcmaInterpretedOptimizedTests`, `AllEcmaTranspilerTests`; the shims `library/rhino/RhinoLibrary` and `RhinoShellLibrary` provide `version`, `print`, `options`, `quit`, `gc`, `load`.

## Performance

Cross-engine benchmarks (Octane, SunSpider, ubench, V8 benchmarks) live in the separate
[JavascriptPerformance](https://github.com/monflabs/JavascriptPerformance) project, which
compares the Monflabs Nashorn fork, OpenJDK Nashorn, Rhino, GraalJS and V8 (Javet). It does
not include a GaltaJS executor yet; the former in-repo `js-test-performance` module, which
did, has been removed.

## Other harnesses

- `js-transpiler-maven-tests` (`tests.AllMavenTests`) exercises the Maven plugin end to end and mixes interpreted and precompiled modules.
- `js-library-v8` is a test-only module pulling `com.caoccao.javet` with the OS-specific native artifact; `js` depends on it in test scope for cross-engine checks.
- `js-test-suite` (`tests.suite.BaseTestSuiteTest`) is the shared runner for directory-based suites (recursive walk, per-file execution, `handleException`).

## Source

`galta/parent-js/js/src/test/java/tests/AllGaltaJSTests.java`, `.../tests/BaseProjectTestCase.java`, `.../tests/GaltaJSTestSuite.java`, `.../util/GlobalTestEnvironment.java`, `.../doc_examples/AllDocExamplesTests.java`, `galta/parent-js/js/src/main/java/org/monflabs/galtajs/library/UnitTestLibrary.java`, `galta/parent-js/js-test-test262/src/test/java/test/test262/Test262BaseTest.java`, `galta/parent-js/js-test-test262/pom.xml`, `galta/parent-js/js-test-rhino/pom.xml`, `galta/parent-js/js-test-suite/`.
