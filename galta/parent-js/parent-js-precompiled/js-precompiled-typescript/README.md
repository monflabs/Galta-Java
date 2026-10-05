# GaltaJS Precompiled TypeScript

The [TypeScript](https://www.typescriptlang.org) compiler (`js/typescript.js`,
version 6.0.3, unmodified from the npm package - `js/download.sh` fetches it)
transpiled to Java with GaltaJS at build time, like the
[js-beautify modules](../README.md): the compiler runs as compiled Java classes,
with no JavaScript parsing at run time. A small facade, `Typescript`, transpiles
TypeScript source to JavaScript.

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-precompiled-typescript</artifactId>
  <version>0.8.0</version>   <!-- or from galta-bom -->
</dependency>
```

```java
Typescript ts = Typescript.newBuilder().build();   // or .environment(env)
String js = ts.execute("let x: number = 1;");       // ES2020, no module system
```

The jar is about 14 MB: the compiler, transpiled into the package
`org.monflabs.galtajs.precompiled.typescript`. Transpiling `typescript.js`
(about 9 MB of JavaScript) takes a few minutes of build time; it is skipped
while the generated sources are up to date, i.e. until `mvn clean` or a change
of the compiler or of the plugin options.

## Contents

- `org.monflabs.galtajs.precompiled.Typescript` - the facade (`newBuilder()`, `.environment(env)`, `execute(source)`), transpiling TypeScript to ES2020 JavaScript with no module system
- `js/typescript.js` - the TypeScript compiler source; `js/Practical Examples/` - sample `.ts` files with their expected JavaScript
- `tests.AllPrecompiledTypescriptTests` - `TypeScriptTest` and `TypeScriptPracticalExamplesTest`: the compiler, interpreted and transpiled, on `src/test/resources/source/sample.ts` and on every practical example, both giving the expected JavaScript

## Building and testing

From `galta/`:

```sh
mvn install -pl parent-js/parent-js-precompiled/js-precompiled-typescript -am
```

## Documentation

- [Execution Modes](../../../../docs/GaltaJS/UserGuide/ExecutionModes.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes)) - the transpiled mode and the Maven plugin
- [Companion Modules](../../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
