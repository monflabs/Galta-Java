# Galta UI Swing IDE

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/ui-swing-ide?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/ui-swing-ide)

IDE-grade Swing editing components: syntax text areas based on
[RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) (with the
RSTAUI find, replace and go-to dialogs), a thread-safe console stream and
persisted UI settings. It builds on [ui-swing](../ui-swing/README.md) and is the
editing layer of the [playground](../../../parent-playground/README.md).

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>ui-swing-ide</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../../README.md#modules)),
or declare a `<version>` directly.

## Contents

- `org.monflabs.ui.swing.ide.IDEApplication` - a `UISwingApplication` created
  with a builder (`config`, `theme`, `applicationName`).
- `ide.theme.IDETheme` - the Swing theme plus the matching RSyntaxTextArea
  theme (syntax colors are fixed at startup).
- `ide.frame.IDEFrame` - a `JFrameCloseable` with the macOS transparent title
  bar and `initialSize()`.
- `ide.syntax.SyntaxTextArea` - an `RSyntaxTextArea` with the theme applied,
  find/replace/go-to dialogs (Cmd/Ctrl+F, R, G) and a debugger current-line
  arrow (`setArrowPosition()`); `ide.syntax.TextArea` is a themed plain
  `RTextArea`. Language support (code completion) is not set up here.
- `ide.components.TextAreaOutputStream` - a `PrintStream` appending to a
  `JTextArea` from any thread (UTF-8, coalesced updates on the event dispatch
  thread, `flush()` waits for the text area, the last 200 000 characters kept).
- `org.monflabs.ui.swing.settings` - `UiPersistentSettings` (the `Config`
  store set by the application), `ComponentStateManager` (persists check box
  and combo box values) and `MultipartTextFile` (several named texts in one
  human-readable file).

## Documentation

- [API reference](https://monflabs.github.io/Galta-Java/#/API) (`ui-swing-ide`)
