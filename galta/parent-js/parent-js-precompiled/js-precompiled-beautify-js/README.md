# GaltaJS Precompiled js-beautify

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-precompiled-beautify-js?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-precompiled-beautify-js)

The JavaScript formatter of [js-beautify](https://github.com/beautify-web/js-beautify)
(1.15.4, the version `js/download.sh` fetches), transpiled to Java with GaltaJS at build time: the library runs
as compiled Java classes, with no JavaScript parsing at run time. A small facade,
`BeautifyJs`, hides the engine.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-precompiled-beautify-js</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../../README.md#modules)),
or declare `<version>` directly.

`BeautifyJs.newBuilder().build()` creates the formatter, optionally with
`.environment(env)` to run it in a given `JSEnvironment` (by default a plain
JavaScript environment with the `global` alias), and `execute(source)` returns
the formatted text, using js-beautify's default options. The library is loaded
once, when the formatter is built.

## Contents

- `org.monflabs.galtajs.precompiled.BeautifyJs` - the facade: builds the environment, runs the transpiled library once and calls its `js_beautify` function
- `js/beautify.js` - the js-beautify source, fetched by `js/download.sh`; the build transpiles it into the class `js.Beautify`
- `tests.JsFormatterTest` - formats `src/test/resources/source/sample.js` both transpiled and interpreted and compares with the expected output

## Documentation

- [Execution Modes](../../../../docs/GaltaJS/UserGuide/ExecutionModes.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes)) - the transpiled mode and the Maven plugin
- [Companion Modules](../../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
