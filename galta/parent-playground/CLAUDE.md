# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-playground` is an interactive scripting environment — a Swing-based mini-IDE where users write and run code snippets, see output, and explore results. It is engine-agnostic at its core; the GaltaJS engine is plugged in via `parent-js/js-playground`.

## Sub-modules

| Module | Artifact | Purpose |
|---|---|---|
| `playground-core` | `playground-core` | Engine-agnostic runtime and data model |
| `playground-ui-swing` | `playground-ui-swing` | Swing UI for the playground |

## `playground-core`

Package root: `org.monflabs.playground`

Core abstractions:
- **`ExecutionEngine`** — abstract class, one instance per run: the final `execute()` runs the subclass's `_execute()` once (READY → RUNNING → COMPLETED) and records the execution thread; `isSoftInterruptable()`/`softInterrupt()` let an engine stop cooperatively instead of being interrupted
- **`ExecutionEngineFactory`** — creates the engine for an `ExecutionContext` and names the main files that make a snippet executable (`getMainExecutableFileNames()`, e.g. `main.js`)
- **`Snippet`** — a folder of files (main file, `README.md`, data, ...); `createSnippetFs()` copies its visible files (not starting with `_`, plus the links of `_links.properties`) into an in-memory **`MemoryFileSystemSnippet`** that remembers each file's physical origin for saving
- **`ExecutionContext`** — the session state for one snippet: the snippet, its in-memory file system (`getContent()`/`setContent()`, synchronized), console streams, and the execution options (`getExecutionOption()`, `isLogStatements()`)
- **`ExecutionResult`** — the outcome of a run; the base class only carries the exception, engines subclass it (e.g. `GaltaJSExecutionResult` with the script and its runtime context)
- **`SnippetFactory`** / **`SnippetTree`** — snippets from a root folder of a `FileSystem` (directory or classpath resources), and their folder tree
- **`PlaygroundConfiguration`** — a singleton (`get()`) holding the frame title, editable flag, snippet factory, execution engine factory and **`PlaygroundLayout`** (which file goes to which editor tab pane, in which order)

## `playground-ui-swing`

Package root: `com.monflabs.playground.swing` (plus `com.monflabs.swing`)

Swing IDE that hosts the playground (`PlaygroundFrame`, an `IDEFrame` from `ui-swing-ide`):
- Snippet tree on the left (with an auto-saved Scratchpad entry first), the snippet's files in two editor tab panes (main/secondary), result tabs on the right (Console first; engines add their own tabs)
- Editors are `SyntaxTextArea` (RSyntaxTextArea); `.md` files are rendered read-only by `MarkdownRenderer` (CommonMark)
- Execute / Stop toolbar, auto-execution 500 ms after an edit: runs are debounced and executed on a background thread; the UI options are captured on the event dispatch thread when the run is requested (`collectExecutionOptions()`), and the console (`TextAreaOutputStream`) is updated asynchronously — `flush()` is the point where the text area is up to date
- Java code completion for `.java`/`.jshell` files reads the JDK's `jmods` (`com.monflabs.swing.rtsyntax`)

## Connecting an Engine

To add a new language:
1. Subclass `ExecutionEngine` (and `ExecutionResult` if the UI needs more than the exception)
2. Set an `ExecutionEngineFactory` (and a `PlaygroundLayout`, a `SnippetFactory`) on `PlaygroundConfiguration.get()` before creating the frame - there is no service loader
3. Subclass `PlaygroundFrame` to add options (`collectExecutionOptions()`) or result tabs (`processExecutionResult()`, called on the event dispatch thread)

The GaltaJS integration lives in `parent-js/js-playground` (`GaltaJSPlayground`, `GaltaJSPlaygroundFrame`, `GaltaJSExecutionEngine`).
