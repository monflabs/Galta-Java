# Galta UI Commons

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/ui-commons?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/ui-commons)

Toolkit-independent UI building blocks: value converters for input fields,
lookups (value lists with display labels) and the application singleton. The
module has no dependency on Swing; [ui-swing](../../parent-ui-swing/ui-swing/README.md)
builds on it.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>ui-commons</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../../README.md#modules)),
or declare a `<version>` directly.

## Contents

Package root: `org.monflabs.ui`.

- `UIApplication` - the application singleton (`UIApplication.get()`), holding
  its `Config`; `UiException` is the module's exception.
- `converters.TextConverter` - value to text and back for input fields:
  `stringConverter`, `booleanConverter`, `intConverter`, `longConverter`,
  `doubleConverter`. null maps to null, the text is trimmed, blank text is
  null, booleans are case-insensitive and invalid text throws an
  `IllegalArgumentException` (`NumberFormatException` for numbers).
- `converters.BooleanConverter` (with `BooleanToValueConverter` and
  `ValueToBooleanConverter`) - a boolean to a value and back, with a default
  for unknown values; `ValueToStringConverter` - a value to its text.
- `lookup.ILookup`, `AbstractLookup`, `StringArrayLookup`, `Lookups.empty()` -
  value lists with display labels and change listeners
  (`ILookupChangeListener`). Index `-1` means "no selection" (`getValue(-1)` is
  null, `getDisplayLabel(-1)` is `""`); any other out-of-range index throws
  `IndexOutOfBoundsException`.

## Documentation

- [API reference](https://monflabs.github.io/Galta-Java/#/API) (`ui-commons`)
