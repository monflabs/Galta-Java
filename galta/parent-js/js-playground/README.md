# GaltaJS Playground

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-playground?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-playground)

An interactive Swing application to write and run JavaScript snippets with the
GaltaJS engine, built on the engine-agnostic playground framework
([`playground-core`, `playground-ui-swing`](../../parent-playground/README.md)).
It comes with a library of snippets (plain JavaScript, ECMAScript features,
host objects such as timers and a Node-style `fs`, the GaltaJS extensions, and
the js-beautify libraries), a console, and views of the current script's syntax
tree, transpiled Java code and decompiled source. Options switch the GaltaJS
extensions, strict mode and the optimizer. Scripts can be debugged in the
built-in [debugger](../js-debugger/README.md) panel, or exposed to an external
Chrome DevTools Protocol client (Chrome DevTools, VS Code).

Shortcuts: Cmd/Ctrl+Enter executes, Cmd/Ctrl+. stops, Cmd/Ctrl+S saves the
scratchpad (the snippets of the library are never saved). The
options, window bounds and last snippet are kept in
`~/.monflabs/playground-galtajs/settings.json`.

## Running it

- **In the browser**, with nothing to install: <https://monflabs.github.io/Galta-Java/playground/>
  (the CheerpJ build, [js-playground-cheerpj](../js-playground-cheerpj/README.md)).
- **On the desktop**: every [GitHub release](https://github.com/monflabs/Galta-Java/releases)
  attaches the runnable fat jar as `galtajs-playground-<version>.jar`:

  ```sh
  java -jar galtajs-playground-<version>.jar
  ```

- **From source** (from `galta/`), which builds the fat jar `target/jsplayground.jar`:

  ```sh
  mvn -pl parent-js/js-playground -am -DskipTests package
  java -jar parent-js/js-playground/target/jsplayground.jar
  ```

## Usage

The thin jar is published to Maven Central, for embedding the playground in
another application:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-playground</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare `<version>` directly.

## Contents

- `playground.GaltaJSPlayground` - the application's `main` (Main-Class of the fat jar)
- `playground.impl` - `GaltaJSPlaygroundFrame` (the window, its result tabs and debugger buttons) and its layout
- `com.monflabs.playground.galtajs` - `GaltaJSExecutionEngine` (runs a snippet's `main.js`), `SnippetEnvironment` (the engine configuration of a run), `SnippetLibrary`, `ASTTrace`
- `src/main/resources/snippets` - the bundled snippet library

## Documentation

- [Debugging](../../../docs/GaltaJS/UserGuide/Debugging.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/Debugging))
- [Companion Modules](../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
