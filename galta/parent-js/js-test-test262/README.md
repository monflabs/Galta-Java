# GaltaJS test262

> Not published to Maven Central: it is the engine's TC39 test262 compliance suite, run from source.

Runs the official TC39 [test262](https://github.com/tc39/test262) suite against
GaltaJS, in three execution modes: interpreted, interpreted with the optimizer,
and transpiled to Java. Every file in scope passes in the three modes, negative
tests included (checked strictly: the expected error type, thrown while parsing
for `phase: parse`); out of scope are `intl402/` (GaltaJS has no ECMA-402) and
`staging/`.

The suite is checked out under `test262/` at the commit pinned by the
`galtajs.test262.commit` property of `pom.xml` - not a git submodule. The
`fetch-externals` profile activates on its own when the checkout is missing, so
the first build fetches it and later builds stay offline.

## Running

From `galta/`. The module compiles against the installed `js` and
`js-test-suite` jars, so install them first
(`mvn install -pl parent-js/js-test-suite --also-make`).

A plain `mvn test`/`mvn install` - including a full build - does **not** run the
suite: it runs `AllBuildNoTest`, a no-op that only proves the module compiles.

```sh
# Interpreted mode only, the routine check (~10 min)
mvn test -pl parent-js/js-test-test262 -Dtest=Test262AllTest

# All 3 modes (hours); also usable in a release build:
# mvn clean install -P javadoc,sources,codesigning,test262
mvn test -pl parent-js/js-test-test262 -Ptest262

# Some directories only (interpreted; -Dtest=AdHocProbeTranspilerTests for transpiled)
mvn test -pl parent-js/js-test-test262 -Dtest=Test262OneTest -Dtest262.path=language/statements,built-ins/Map

# Fetch the suite ahead of time, or again after bumping galtajs.test262.commit
mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources
```

After a transpiled run, delete the generated sources with
`find parent-js/js-test-test262/src/test/java/compiled -name '*.java' -delete`.

## Contents

Test sources only (`src/test/java`):

- `test.All262Tests` - the 3 modes, composed of `All262InterpretedTests`, `All262nterpretedOptimizedTests` (sic) and `All262TranspilerTests`
- `test.AllBuildNoTest` - the default no-op of plain builds
- `test.AdHocProbeInterpreterTests`, `test.AdHocProbeTranspilerTests` - run `Test262OneTest` interpreted with the optimizer, or transpiled (the latter first deletes the generated sources)
- `test.test262.Test262BaseTest` - the runner (over `BaseTestSuiteTest` from [js-test-suite](../js-test-suite/README.md)): loads the `sta.js`/`assert.js` harness and the `includes:`, parses the YAML front matter, honors `flags`, `features` and `negative`
- `test.test262.Test262AllTest` (whole suite), `Test262OneTest` (directories given by `-Dtest262.path`), `Test262RegexpReportTest` (a report of the RegExp tests under both regexp engines)
- `org.monflabs.galtajs.test.test262` - `GlobalTest262Environment`, `Test262TestLibrary` (`print`, the `$262` host object) and `Test262AgentManager` (the `$262.agent` workers)

## Documentation

- [Known ECMAScript Gaps](../../../docs/GaltaJS/KnownGaps.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/KnownGaps)) - the current status and how to regenerate it
- [Testing and Compliance](../../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
- [Building and Releasing](../../../docs/BuildAndRelease.md) ([online](https://monflabs.github.io/Galta-Java/#/BuildAndRelease))
