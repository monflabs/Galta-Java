# Galta Playground Swing

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/playground-ui-swing?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/playground-ui-swing)

The Swing user interface of the interactive scripting playground, built on
[playground-core](../playground-core/README.md) and
[ui-swing-ide](../../parent-ui/parent-ui-swing/ui-swing-ide/README.md):
RSyntaxTextArea editors, Markdown rendered with CommonMark (GFM tables), a
console and result tabs, and Java code completion through the RSTA
`languagesupport` library.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>playground-ui-swing</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

To connect an engine, set an `ExecutionEngineFactory` (and optionally a
`PlaygroundLayout` and a `SnippetFactory`) on `PlaygroundConfiguration.get()`
before creating the frame, and subclass `PlaygroundFrame` to add options
(`collectExecutionOptions()`) or result tabs (`processExecutionResult()`).

## Contents

- `com.monflabs.playground.swing.PlaygroundFrame` - an `IDEFrame` with the
  snippet tree (an auto-saved Scratchpad entry first), the snippet's files in
  two editor tab panes, and the result tabs (Console first). `.md` files are
  rendered read-only; binary files open as a placeholder. Executions run
  through an `ExecutionController`, with auto-execution 500 ms after an edit.
  Settings (toolbar options, window bounds, last snippet) are persisted under
  `playground/` when a `UiPersistentSettings` store is set.
- `PlaygroundExecutionContext` - the execution context whose console is the
  frame's console text area.
- `com.monflabs.swing.components.MarkdownRenderer` - the Markdown view (links
  open in the browser, relative images resolve against the snippet folder).
- `com.monflabs.swing.rtsyntax` - locates the Java runtime classes for the
  `.java`/`.jshell` code completion (the JDK's `jmods`, else the running JVM's
  `jrt:/` image).

Shortcuts: Cmd/Ctrl+Enter executes, Cmd/Ctrl+. stops, Cmd/Ctrl+S saves the
scratchpad, Cmd/Ctrl+F/R/G find, replace and go to a line in an editor.

Only the scratchpad is saved (automatically). The snippets of the library can be
edited and run, but are never saved: leaving a modified snippet asks once whether to
discard the changes.

## Documentation

- [Companion modules](../../../docs/GaltaJS/UserGuide/CompanionModules.md) - the GaltaJS playground, which plugs the engine into this UI
- [API reference](https://monflabs.github.io/Galta-Java/#/API) (`playground-ui-swing`)
