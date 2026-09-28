# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-ui` is a Swing-based UI framework. It is organised in two levels: a platform-agnostic commons layer and a Swing implementation layer that also includes IDE-grade editing components.

## Sub-module Hierarchy

```
parent-ui/
├── parent-ui-commons/
│   └── ui-commons/          # Platform-agnostic UI abstractions
└── parent-ui-swing/
    ├── ui-swing/             # Core Swing components and utilities
    └── ui-swing-ide/         # IDE components (editor, syntax highlighting)
```

## `ui-commons`

Package root: `org.monflabs.ui`

Defines abstractions that are independent of Swing:
- **`TextConverter`** — bidirectional converter between typed values and display strings
- **`AbstractLookup`** — base for lookup/dropdown data providers
- UI factory and lifecycle interfaces

## `ui-swing`

Package root: `org.monflabs.ui.swing`

Core components:
- **`UISwingApplication`** — Swing application lifecycle base class (startup, shutdown, look-and-feel setup), built on `UIApplication` from `ui-commons` (`org.monflabs.ui`)
- **`MultiSplitPane`** — flexible multi-pane splitter layout (supports arbitrary split trees, not just binary splits)
- **`JTreeTable`** — hybrid tree/table component backed by a `TreeTableModel`
- **`JMultiLineLabel`** — wrapping label component
- **`SwingUtil`** — helpers for component sizing, font scaling, threading checks, and rendering hints
- **`FontUtil`** — font loading and management

## `ui-swing-ide`

Package root: `org.monflabs.ui.swing.ide`

Builds on `ui-swing` and integrates:
- **RSyntaxTextArea** — syntax-highlighted editor component
- Language support descriptors for GaltaJS and other languages
- Spell-checking integration
- Used by the playground and the JS debugger

## Key Conventions

- All UI work that touches Swing components must run on the Event Dispatch Thread. Use `SwingUtil` helpers to dispatch tasks if needed.
- `MultiSplitPane` layout is described by a tree built with `MultiSplitLayout.row(...)`, `column(...)` and `pane(name, weight)` — not pixel coordinates; components are added with their pane name as the constraint. Original Apache-2.0 code (it replaced an LGPL SwingX copy).
