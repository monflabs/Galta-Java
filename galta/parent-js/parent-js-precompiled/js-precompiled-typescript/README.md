# GaltaJS Precompiled TypeScript

> Not published to Maven Central: the precompiled compiler is not functional yet - the transpilation of `typescript.js` is disabled in this module's `pom.xml`.

Meant to ship the [TypeScript](https://www.typescriptlang.org) compiler
(the checked-in `js/typescript.js` is version 6.0.3, although `js/download.sh` names 5.9.2) transpiled to Java with
GaltaJS, like the [js-beautify modules](../README.md). The plugin execution that
would transpile it is commented out in `pom.xml`, so no compiled compiler exists:
`Typescript.execute(source)` throws an `IllegalStateException` until it is
re-enabled. The tests run the compiler interpreted instead.

## Contents

- `org.monflabs.galtajs.precompiled.Typescript` - the facade (`newBuilder()`, `.environment(env)`, `execute(source)`), which would transpile TypeScript to ES2020 JavaScript with no module system
- `js/typescript.js` - the TypeScript compiler source; `js/Practical Examples/` - sample `.ts` files
- `tests.AllPrecompiledTypescriptTests` - `TypeScriptTest` (checks that the facade reports the missing compiler, and runs the compiler interpreted on `src/test/resources/source/sample.ts`) and `TypeScriptPracticalExamplesTest` (the practical examples, interpreted)
- `tests.AllBuildNoTest` - the no-op that a plain build runs (the only class surefire includes)

## Running the tests

From `galta/`:

```sh
mvn test -pl parent-js/parent-js-precompiled/js-precompiled-typescript -Dtest=AllPrecompiledTypescriptTests
```

## Documentation

- [Execution Modes](../../../../docs/GaltaJS/UserGuide/ExecutionModes.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes)) - the transpiled mode and the Maven plugin
- [Companion Modules](../../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
