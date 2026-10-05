# GaltaJS Precompiled js-beautify HTML

The HTML formatter of [js-beautify](https://github.com/beautify-web/js-beautify)
(1.15.4, the version `js/download.sh` fetches), transpiled to Java with GaltaJS at build time: the library runs
as compiled Java classes, with no JavaScript parsing at run time. A small facade,
`BeautifyHtml`, hides the engine.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-precompiled-beautify-html</artifactId>
</dependency>
```

Published to Maven Central: give it the Galta version (`<version>0.8.0</version>`),
or import `galta-bom` and leave the version out.

`BeautifyHtml.newBuilder().build()` creates the formatter, optionally with
`.environment(env)` to run it in a given `JSEnvironment` (by default a plain
JavaScript environment with the `global` alias), and `execute(source)` returns
the formatted text, using js-beautify's default options. The library is loaded
once, when the formatter is built.

## Contents

- `org.monflabs.galtajs.precompiled.BeautifyHtml` - the facade: builds the environment, runs the transpiled library once and calls its `html_beautify` function
- `js/beautify-html.js` - the js-beautify source, fetched by `js/download.sh`; the build transpiles it into the class `org.monflabs.galtajs.precompiled.beautifyhtml.Beautify_dhtml`
- `tests.HtmlFormatterTest` - formats `src/test/resources/source/sample.html` both transpiled and interpreted and compares with the expected output

## Documentation

- [Execution Modes](../../../../docs/GaltaJS/UserGuide/ExecutionModes.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes)) - the transpiled mode and the Maven plugin
- [Companion Modules](../../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
