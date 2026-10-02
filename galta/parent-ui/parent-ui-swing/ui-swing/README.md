# Galta UI Swing

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/ui-swing?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/ui-swing)

Swing components, layouts, themes and utilities, built on
[ui-commons](../../parent-ui-commons/ui-commons/README.md). The look and feel is
[FlatLaf](https://www.formdev.com/flatlaf/), light or dark, following the OS
appearance unless the configuration forces it.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>ui-swing</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../../README.md#modules)),
or declare a `<version>` directly.

## Contents

Package root: `org.monflabs.ui.swing`.

- `UISwingApplication` - a `UIApplication` with a `SwingTheme`.
- `theme.SwingTheme` - the FlatLaf light/dark setup, chosen once at startup.
  Configuration keys: `ui/dark` (force the dark or light theme),
  `ui/applicationName` (the macOS menu bar name) and `ui/hideFocusBorder`
  (opt-in).
- `components.MultiSplitPane` / `MultiSplitLayout` - split trees described with
  `row(...)`, `column(...)` and `pane(name, weight)`; components are added with
  their pane name as the constraint.
- `components.JMultiLineLabel`, `components.image.JMImagePanel` and `ImageUtil`,
  `components.models.LookupListModel` (a `ListModel` over an `ILookup`).
- `layouts.VerticalFlowLayout`, `dialogs.JDialogEx`, `dialogs.JTreeUtil`
  (`expandAllNodes()`).
- `frame.JFrameCloseable` - a frame that asks `canClose()`; closing the last one
  exits the JVM unless `setExitOnLastFrameClosed(false)`.
- `util.SwingUtil` - `centerWindow()`, `findParentOfType()`,
  `findFirstChildOfType()`, `scale()` (HiDPI scaling through FlatLaf);
  `util.FontUtil.boldify()`.
- `util.SwingIdleUpdater` / `UIUpdater` - refresh component states when the
  event queue goes idle (components are held weakly).

Swing components are touched on the event dispatch thread only.

## Documentation

- [API reference](https://monflabs.github.io/Galta-Java/#/API) (`ui-swing`)
