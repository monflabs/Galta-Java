# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-playground` is an interactive scripting environment — a Swing-based mini-IDE where users write and run code snippets, see output, and explore results. It is engine-agnostic at its core; the GaltaJS engine is plugged in via `parent-js/js-playground`.

## Sub-modules

| Module | Artifact | Purpose |
|---|---|---|
| `playground-core` | `playground-core` | Engine-agnostic runtime and data model (no Swing) |
| `playground-ui-swing` | `playground-ui-swing` | Swing UI for the playground |

Test suites: `AllSnippetTests` (core) and `AllPlaygroundSwingTests` (Swing, headless).

## `playground-core`

Package root: `org.monflabs.playground`

Core abstractions:
- **`ExecutionEngine`** — abstract class, one instance per run: the final `execute()` runs the subclass's `_execute()` once (READY → RUNNING → COMPLETED) and records the execution thread; the context is kept after the run; `isSoftInterruptable()`/`softInterrupt()` let an engine stop cooperatively instead of being interrupted
- **`ExecutionEngineFactory`** — creates the engine for an `ExecutionContext` and names the main files that make a snippet executable (`getMainExecutableFileNames()`, `getMainFileName()`, e.g. `main.js`)
- **`ExecutionController`** — runs the executions without any UI: debounced requests (`request(context, options, delay)`), one current run (a new one soft-interrupts or interrupts the previous, which is abandoned after the stop timeout), `stop()`, `cancel()` (another snippet), `close()`; runs carry an id and their options (`currentThreadRun()`), the listener only hears about the current run, and `Run.gate(PrintStream)` mutes the console of a superseded run. Daemon threads only
- **`Snippet`** — a folder of files (main file, `README.md`, data, ...); `createSnippetFs()` copies its visible files (not starting with `_` or `.`, plus the links of `_links.properties`) into an in-memory **`MemoryFileSystemSnippet`** that remembers each file's physical origin and its content as loaded
- **`ExecutionContext`** — the session state for one snippet: the snippet, its in-memory files (`getBytes()`/`getContent()`/`setContent()`, read and replaced in one step: a reader never sees a partial file; `isTextFile()`), console streams, and the execution options (`getExecutionOption()`, typed `getExecutionOption(key, type, default)`, `getBooleanOption()`, `isLogStatements()`)
- **`SnippetStorage`** — saves the in-memory files back: changed text files only, atomically (temp file + move), CRLF kept, binary files skipped, files modified on disk since loaded reported as conflicts (`save(force)`), read-only folders reported
- **`ExecutionResult`** — the outcome of a run; the base class only carries the exception, engines subclass it (e.g. `GaltaJSExecutionResult` with the script and its runtime context)
- **`SnippetFactory`** / **`SnippetTree`** — snippets from a root folder of a `FileSystem` (directory or classpath resources), and their folder tree
- **`PlaygroundConfiguration`** — a singleton (`get()`) holding the frame title, editable flag, snippet factory, execution engine factory and **`PlaygroundLayout`** (which file goes to which editor tab pane, in which order)

## `playground-ui-swing`

Package root: `com.monflabs.playground.swing` (plus `com.monflabs.swing`)

Swing IDE that hosts the playground (`PlaygroundFrame`, an `IDEFrame` from `ui-swing-ide`):
- Snippet tree on the left (with an auto-saved Scratchpad entry first, named after the engine's main file), the snippet's files in two editor tab panes (main/secondary), result tabs on the right (Console first; engines add their own tabs). The first snippet is loaded when the frame becomes displayable
- Editors are `SyntaxTextArea` (RSyntaxTextArea; the loaded text is not undoable); `.md` files are rendered read-only by `MarkdownRenderer` (CommonMark + GFM tables, links open in the browser, relative images resolve against the snippet folder); binary files open as a non-editable placeholder
- The editors' text is copied to the execution context when a run starts or the snippet is saved (`commitEditors()`), not on every keystroke; the editors are unregistered from the RSTA language supports when the snippet closes (only `.java`/`.jshell` editors register — the JavaScript support parses with Rhino, which the GaltaJS playground excludes)
- Execute / Stop toolbar (Cmd/Ctrl+Enter, Cmd/Ctrl+., save Cmd/Ctrl+S), auto-execution 500 ms after an edit, all through an `ExecutionController`; the UI options are captured on the event dispatch thread when the run is requested (`collectExecutionOptions()`), `processExecutionResult()` is called on the event dispatch thread for the current run only, and the console (`TextAreaOutputStream`) is updated asynchronously — `flush()` is the point where the text area is up to date
- Settings (toolbar options, window bounds, main divider, last snippet) are persisted under `playground/` when a `UiPersistentSettings` store is set (the GaltaJS launcher sets one)
- Java code completion for `.java`/`.jshell` files reads the JDK's `jmods` (`com.monflabs.swing.rtsyntax`)

## Connecting an Engine

To add a new language:
1. Subclass `ExecutionEngine` (and `ExecutionResult` if the UI needs more than the exception)
2. Set an `ExecutionEngineFactory` (and a `PlaygroundLayout`, a `SnippetFactory`) on `PlaygroundConfiguration.get()` before creating the frame - there is no service loader
3. Subclass `PlaygroundFrame` to add options (`collectExecutionOptions()`) or result tabs (`processExecutionResult()`); `initToolbarLeft()`/`initToolbarRight()` run from the super constructor, before the subclass's field initializers

The GaltaJS integration lives in `parent-js/js-playground` (`GaltaJSPlayground`, `GaltaJSPlaygroundFrame`, `GaltaJSExecutionEngine`).
