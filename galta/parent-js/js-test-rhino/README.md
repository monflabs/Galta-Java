# GaltaJS Rhino Tests

> Not published to Maven Central: it is the engine's run of Mozilla Rhino's test suite, run from source.

Runs GaltaJS against the test suites of Mozilla's
[Rhino](https://github.com/mozilla/rhino) engine: the historical ECMA suite
(`ecma/`, `ecma_2/`), Rhino's ES6+ `jstests` (`es6`, `es2019` to `es2025`), and
Rhino shell and Java package checks. The environment is plain ECMAScript (no
GaltaJS extension) with Rhino-compatible shell functions (`print`, `load`,
`gc`, ...). Each suite runs interpreted, interpreted with the optimizer, and
transpiled to Java.

The suite is checked out under `rhino/` at the commit pinned by the
`galtajs.rhino.commit` property of `pom.xml` - not a git submodule. The
`fetch-externals` profile activates on its own when the checkout is missing, so
the first build fetches it and later builds stay offline.

## Running

From `galta/` (the module compiles against the installed `js-test-suite`
test-jar: `mvn install -pl parent-js/js-test-suite --also-make`):

```sh
# Runs AllEcmaTests, the only class surefire includes
mvn test -pl parent-js/js-test-rhino

# Fetch the suite ahead of time, or again after bumping galtajs.rhino.commit
mvn -pl parent-js/js-test-rhino -Pfetch-externals generate-test-resources
```

To run or debug a single file, set `EcmaOneTest.TEST_FILE` (or
`JsTestOneTest` for the ES6+ tests) and run that class; `EcmaOneTest.main()`
opens the file in the Swing debugger.

## Contents

Test sources only (`src/test/java`):

- `tests.AllEcmaTests` - the entry point, composing the three modes (`AllEcmaInterpretedTests`, `AllEcmaInterpretedOptimizedTests`, `AllEcmaTranspilerTests`)
- `tests.ecma` - `EcmaAllTest` (the `ecma/` and `ecma_2/` folders), `EcmaOneTest`, and `EcmaBaseTest` with the `FILTER` (skipped files) and `FILTER_ERRORS` (failures ignored) lists, each entry commented
- `tests.rhinoes6_testsrc` - `JsAllTest`, `JsTestOneTest` and `JsTestBaseTest` for Rhino's `testsrc/jstests`
- `tests.rhino` - `RhinoShellTest` and `JavaPackageTest`
- `org.monflabs.galtajs.test.rhino` - `RhinoTestEnvironment` and the Rhino-specific test libraries (`RhinoTestLibrary`, `JavaPackageLibrary`)
- `src/test/resources/polyfills/toSource.js` - a `toSource()` polyfill installed in the environment

## Documentation

- [Testing and Compliance](../../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
- [Known ECMAScript Gaps](../../../docs/GaltaJS/KnownGaps.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/KnownGaps))
