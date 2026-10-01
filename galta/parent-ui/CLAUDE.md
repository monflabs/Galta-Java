# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-ui` is a small Swing UI library: a toolkit-independent commons layer, the Swing components and utilities, and the IDE-grade editing components used by the playground (`parent-playground`).

## Sub-module Hierarchy

```
parent-ui/
├── parent-ui-commons/
│   └── ui-commons/          # Toolkit-independent: converters, lookups, application
└── parent-ui-swing/
    ├── ui-swing/             # Swing components, layouts, FlatLaf theme, utilities
    └── ui-swing-ide/         # RSyntaxTextArea editors, console stream, persisted settings
```

Each module has a JUnit 3 suite run by surefire (`AllUiCommonsTests`, `AllUiSwingTests`, `AllUiSwingIdeTests`); the Swing suites run headless (`java.awt.headless=true`): they use components, never a window.

## `ui-commons`

Package root: `org.monflabs.ui`
- **`UIApplication`** — the application singleton holding its `Config`
- **`converters.TextConverter`** — value <-> text for input fields (`stringConverter`, `booleanConverter`, `intConverter`, `longConverter`, `doubleConverter`). Contract: null <-> null, text trimmed, blank -> null, booleans case-insensitive, invalid text -> `IllegalArgumentException` (`NumberFormatException` for numbers)
- **`converters.BooleanConverter`**, **`ValueToStringConverter`** — boolean <-> value (with a default for unknown values), value -> text
- **`lookup.ILookup`** / **`AbstractLookup`** / **`StringArrayLookup`** / **`Lookups.empty()`** — value lists with display labels and change listeners. Index contract: `-1` is "no selection" (`getValue(-1)` is null, `getDisplayLabel(-1)` is `""`), any other out-of-range index throws `IndexOutOfBoundsException`

## `ui-swing`

Package root: `org.monflabs.ui.swing`
- **`UISwingApplication`** — a `UIApplication` with a `SwingTheme` (no lifecycle of its own)
- **`theme.SwingTheme`** — FlatLaf light/dark setup, following the OS appearance unless `ui/dark` is set; `ui/applicationName` (macOS menu bar name, the `apple.*` properties are set before the FlatLaf setup), `ui/hideFocusBorder` (opt-in). Chosen once at startup
- **`components.MultiSplitPane`** / **`MultiSplitLayout`** — split trees built with `row(...)`, `column(...)`, `pane(name, weight)` (not pixel coordinates); components are added with their pane name as constraint. Original Apache-2.0 code (it replaced an LGPL SwingX copy)
- **`components.JMultiLineLabel`**, **`components.image.JMImagePanel`** / **`ImageUtil`**, **`components.models.LookupListModel`** (a `ListModel` over an `ILookup`)
- **`layouts.VerticalFlowLayout`**, **`dialogs.JDialogEx`**, **`dialogs.JTreeUtil`** (`expandAllNodes()`: fast with a fixed row height and a large model)
- **`frame.JFrameCloseable`** — asks `canClose()`; closing the last one exits the JVM unless `setExitOnLastFrameClosed(false)`
- **`util.SwingUtil`** — `centerWindow()`, `findParentOfType()`, `findFirstChildOfType()`, `scale()` (HiDPI through FlatLaf's `UIScale`)
- **`util.FontUtil`** — `boldify()` only
- **`util.SwingIdleUpdater`** / **`UIUpdater`** — refresh component states when the event queue goes idle (hook installed on the first `attach()`, components held weakly)

## `ui-swing-ide`

Package root: `org.monflabs.ui.swing` (`ide`, `settings`)
- **`ide.IDEApplication`** — builder (`config`, `theme`, `applicationName`) creating an `IDETheme`
- **`ide.theme.IDETheme`** — the Swing theme plus the matching RSyntaxTextArea theme (syntax colors fixed at startup)
- **`ide.frame.IDEFrame`** — a `JFrameCloseable` with the macOS transparent title bar and `initialSize()`
- **`ide.syntax.SyntaxTextArea`** — RSyntaxTextArea with the theme applied, find/replace/go-to dialogs (Cmd/Ctrl+F/R/G, disposed with the text area) and a debugger current-line arrow (`setArrowPosition()`, color following the background). **`ide.syntax.TextArea`** — a themed plain `RTextArea`. No language-support descriptors and no spell checking here: language support (code completion) is set up by the playground (`LanguageSupportFactory`, Java only)
- **`ide.components.TextAreaOutputStream`** — a `PrintStream` appending to a `JTextArea` from any thread: UTF-8, coalesced `invokeLater()` publication, `flush()` waits for the text area (out of the stream lock, so the event dispatch thread can print meanwhile), keeps the last 200 000 characters
- **`settings.UiPersistentSettings`** (the `Config` store set by the application, `isAvailable()`), **`settings.ComponentStateManager`** (persists check box / combo box values; applies the defaults when persistence is off), **`settings.MultipartTextFile`**

`org.monflabs.js.debugger` (`parent-js/js-debugger`) does not use these modules: it depends on RSyntaxTextArea directly.

## Key Conventions

- Swing components are touched on the event dispatch thread only (`SwingUtilities.invokeLater()`/`invokeAndWait()`); `TextAreaOutputStream` is the thread-safe way to write to a text area.
- No published API without a user: classes nothing used (JTreeTable, several lookups and converters) were removed - grep the peer repositories before adding or removing API.
