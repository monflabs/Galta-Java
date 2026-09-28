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
- **`ExecutionEngine`** — interface implemented by each language backend to execute a snippet and return a result
- **`Snippet`** — a named, typed piece of source code (script, markdown, data, …)
- **`ExecutionContext`** — holds the runtime state for a playground session (variables, imports, output buffer)
- **`ExecutionResult`** — the outcome of running a snippet (value, console output, error, duration)
- **`SnippetFactory`** — creates `Snippet` instances from strings or files
- **`PlaygroundConfiguration`** — settings (theme, engine options, filesystem roots)

Markdown support: snippets of type `markdown` are rendered via **CommonMark** and displayed in a read-only panel, allowing documentation to be mixed with runnable code.

## `playground-ui-swing`

Package root: `com.monflabs.playground.swing` (plus `com.monflabs.swing`)

Swing IDE that hosts the playground:
- Split-pane layout: snippet list on the left, editor in the centre, output on the right
- Editor uses `ui-swing-ide` components (RSyntaxTextArea) with syntax highlighting
- Output panel renders both plain text and structured JSON results
- Run / Stop toolbar triggers `ExecutionEngine.execute()` asynchronously and streams output back to the UI

## Connecting an Engine

To add a new language:
1. Implement `ExecutionEngine`
2. Register it with the playground via `PlaygroundConfiguration` or a service-loader entry
3. Provide a syntax highlighting descriptor in `ui-swing-ide` if desired

The GaltaJS integration lives in `parent-js/js-playground`, which wires `JSEnvironment` into `ExecutionEngine`.
