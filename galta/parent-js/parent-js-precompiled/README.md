# GaltaJS Precompiled Libraries Parent

> Published to Maven Central, and managed by `galta-bom`.

The parent of third-party JavaScript libraries shipped as Java classes: each
module keeps the library's JavaScript source in its `js/` folder (with the
`download.sh` script that fetched it), transpiles it to Java at build time with
the [js-transpiler-maven](../js-transpiler-maven/README.md) plugin, and adds a
thin Java facade that runs the transpiled code in a GaltaJS environment. They
are both useful formatters and real-world tests of the transpiler.

Each module transpiles its library into its own package under
`org.monflabs.galtajs.precompiled` (`beautifyjs`, `beautifycss`, `beautifyhtml`,
`typescript`), next to the facade. The javadoc covers the facade only
(`sourceFileExcludes` in this pom): the generated code has no Java API of its own.

## Modules

- [js-precompiled-beautify-js](js-precompiled-beautify-js/README.md) - the js-beautify JavaScript formatter (`BeautifyJs`)
- [js-precompiled-beautify-css](js-precompiled-beautify-css/README.md) - the js-beautify CSS formatter (`BeautifyCss`)
- [js-precompiled-beautify-html](js-precompiled-beautify-html/README.md) - the js-beautify HTML formatter (`BeautifyHtml`)
- [js-precompiled-typescript](js-precompiled-typescript/README.md) - the TypeScript compiler (`Typescript`)

## Documentation

- [Execution Modes](../../../docs/GaltaJS/UserGuide/ExecutionModes.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes)) - the transpiled mode and the Maven plugin
- [Companion Modules](../../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
