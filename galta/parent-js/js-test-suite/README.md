# GaltaJS Test Suite Support

> Not published to Maven Central: it is test infrastructure shared by the compliance suites.

The shared runner of the directory-based JavaScript compliance suites,
[js-test-test262](../js-test-test262/README.md) and
[js-test-rhino](../js-test-rhino/README.md). Its classes live in `src/test/java`
and are packaged as a `test-jar`, which those modules depend on in test scope.

## Contents

- `tests.suite.BaseTestSuiteTest` - the abstract runner: walks a suite directory
  recursively, runs each file in the interpreted, optimized or transpiled mode
  (the transpiled Java is generated, compiled and loaded), tracks the phase an
  error came from (`ExecPhase`: setup, parse, transpile, execute), applies the
  `FILTER`/`FILTER_ERRORS` skip lists, and lets a subclass supply the shell
  (`readShell`), preprocess each file and decide which exceptions are expected
  (`handleException`); it can also open a file in the Swing debugger
- `tests.suite.CharsetToolkit` - guesses the encoding of a byte buffer (not used by the runner at the moment)
- `test-case.js` - a JavaScript `TestCase` helper in the style of the Mozilla suites (not referenced by the build)

## Building

From `galta/` - the suites compile against the installed test-jar:

```sh
mvn install -pl parent-js/js-test-suite --also-make
```

## Documentation

- [Testing and Compliance](../../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
