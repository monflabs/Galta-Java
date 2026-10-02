# GaltaJS Transpiler Maven Plugin Tests

> Not published to Maven Central: it is a build-level check of the transpiler Maven plugin.

Exercises [js-transpiler-maven](../js-transpiler-maven/README.md) inside the
reactor: the module declares the plugin's `generate-sources` goal on its `js/`
folder (`file1.js`, `file2.js`, with `sourceMap` on), so a build transpiles them
into `target/generated-sources/js/js/` (`File1.java`, `File2.java` with their
`.js` and `.jsmap` companions) and compiles the result. A failing transpilation
or a generated class that does not compile fails the build.

## Running

From `galta/`, once the plugin and the engine are installed:

```sh
mvn install -pl parent-js/js-transpiler-maven-tests --also-make
```

## Contents

- `js/` - the JavaScript sources the plugin transpiles
- `jssource/` - a small module example (`math.js` importing `mod/math.js`), not transpiled by the build
- `tests.AllMavenTests` - the test suite; its only test, `tests.exec.FullInterpretedTest`, has its body commented out, so the module checks the build output, not the runtime behavior

## Documentation

- [Execution Modes - Maven plugin](../../../docs/GaltaJS/UserGuide/ExecutionModes.md#maven-plugin) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes?id=maven-plugin))
- [Testing and Compliance](../../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
