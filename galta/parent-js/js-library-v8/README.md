# GaltaJS V8 Library

> Not published to Maven Central: it only serves the engine's own tests.

Brings the V8 JavaScript engine to the GaltaJS tests, through
[Javet](https://www.caoccao.com/Javet/index.html), so GaltaJS results can be
cross-checked against V8. The module has no main code: it declares the `javet`
dependency plus the native V8 artifact of the build machine, picked by an
OS/architecture profile that activates on its own (macOS x86_64 and arm64,
Windows x86_64, Linux x86_64/amd64 and arm64). The [`js`](../js/README.md) module
depends on it in test scope.

## Contents

- `pom.xml` - the Javet dependency and the per-platform `javet-v8-*` profiles
- `tests.sample.HelloWorldTest` (suite `tests.AllV8Tests`) - checks that the V8
  native library loads and runs a script on the current platform

## Running the check

From `galta/`:

```sh
mvn test -pl parent-js/js-library-v8
```

## Documentation

- [Testing and Compliance](../../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
