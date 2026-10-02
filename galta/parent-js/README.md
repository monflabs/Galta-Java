# GaltaJS Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/parent-js?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/parent-js)

The parent of the GaltaJS modules: **GaltaJS**, a JavaScript engine for the JVM
with an interpreter and a transpiler to Java, and the modules built around it
(tooling, small layers on top of the engine, packaging variants and the
compliance test suites). This pom only aggregates them; applications depend on
the modules themselves, usually just [`js`](js/README.md).

## Modules

Published to Maven Central:

- [js](js/README.md) - the engine: parser, interpreter, transpiler, runtime, standard library, Java interop, debugger API and CDP server
- [js-transpiler-maven](js-transpiler-maven/README.md) - Maven plugin transpiling `.js` files to Java sources at build time
- [js-template](js-template/README.md) - JSP-like text templates evaluated with GaltaJS
- [js-vb](js-vb/README.md) - `${...}` expression-language value bindings evaluated with GaltaJS
- [js-debugger](js-debugger/README.md) - Swing debugger speaking the Chrome DevTools Protocol
- [js-playground](js-playground/README.md) - the interactive GaltaJS playground (Swing application)

Built from source only (not published):

- [parent-js-precompiled](parent-js-precompiled/README.md) - JavaScript libraries transpiled to Java at build time (js-beautify)
- [js-all](js-all/README.md) - a single fat jar of the engine and its dependencies
- [js-playground-cheerpj](js-playground-cheerpj/README.md) - the browser (CheerpJ) build of the playground
- [js-library-v8](js-library-v8/README.md) - pulls V8 (Javet) for cross-engine checks in tests
- [js-test-suite](js-test-suite/README.md) - shared runner of the directory-based compliance suites
- [js-test-test262](js-test-test262/README.md) - runs the TC39 test262 suite
- [js-test-rhino](js-test-rhino/README.md) - runs Mozilla Rhino's ECMA test suite
- [js-transpiler-maven-tests](js-transpiler-maven-tests/README.md) - build-level check of the transpiler Maven plugin

## External test suites

`js-test-test262` and `js-test-rhino` run external suites (TC39 test262,
Mozilla's Rhino ECMA suite) that are not git submodules: each is fetched at a
commit pinned in the module's `pom.xml` (`galtajs.test262.commit`,
`galtajs.rhino.commit`) by a `fetch-externals` profile. The profile activates
on its own when the checkout is missing, so the first build after a clean clone
fetches the suite and later builds stay offline. To fetch ahead of time, or to
re-fetch after bumping the pinned commit (from `galta/`):

```sh
mvn -pl parent-js/js-test-test262 -Pfetch-externals generate-test-resources
mvn -pl parent-js/js-test-rhino -Pfetch-externals generate-test-resources
```

## Documentation

- [GaltaJS documentation](../../docs/GaltaJS/README.md), online at <https://monflabs.github.io/Galta-Java/#/GaltaJS/>
- [Companion Modules](../../docs/GaltaJS/UserGuide/CompanionModules.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/CompanionModules))
- [Testing and Compliance](../../docs/GaltaJS/Architecture/Testing.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Testing))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
