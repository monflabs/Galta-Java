# Galta Playground Core

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/playground-core?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/playground-core)

The engine-agnostic core of the interactive scripting playground, without any
UI: snippets and their in-memory files, execution engines and their lifecycle.
A language is plugged in by implementing an `ExecutionEngine` and its factory;
the Swing user interface is [playground-ui-swing](../playground-ui-swing/README.md).

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>playground-core</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

## Contents

Package: `org.monflabs.playground`.

- `Snippet` - a folder of files (main file, `README.md`, data...).
  `createSnippetFs()` copies its visible files (not starting with `_` or `.`,
  plus the links of `_links.properties`) into a `MemoryFileSystemSnippet`, an
  in-memory file system remembering each file's origin and loaded content.
- `SnippetFactory` / `SnippetTree` - snippets from a root folder of a
  `FileSystem` (a directory or classpath resources) and their folder tree.
- `ExecutionContext` - the session state of one snippet: its in-memory files,
  the console streams and the execution options.
- `ExecutionEngine` - one instance per run: `execute()` runs the subclass's
  `_execute()` once (READY, RUNNING, COMPLETED); an engine can support a
  cooperative stop (`softInterrupt()`). `ExecutionResult` carries the outcome
  and is subclassed by engines.
- `ExecutionEngineFactory` - creates the engine for a context and names the
  main files that make a snippet executable (for example `main.js`).
- `ExecutionController` - runs the executions without a UI: debounced requests
  (`request(context, options, delay)`), one current run (a new one stops the
  previous), `stop()`, `cancel()`, `close()`; `Run.gate(PrintStream)` mutes
  the console of a superseded run.
- `SnippetStorage` - saves the in-memory files back: changed text files only,
  atomically, with conflicts reported for files modified on disk since loaded.
- `PlaygroundConfiguration` - the singleton holding the frame title, the
  editable flag, the snippet factory, the engine factory and the
  `PlaygroundLayout` (which file goes to which editor tab pane).

## Documentation

- [Companion modules](../../../docs/GaltaJS/UserGuide/CompanionModules.md) - the GaltaJS playground
- [API reference](https://monflabs.github.io/Galta-Java/#/API) (`playground-core`)
