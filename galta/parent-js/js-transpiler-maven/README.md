# GaltaJS Transpiler Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js-transpiler-maven?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js-transpiler-maven)

A Maven plugin that transpiles JavaScript files to Java sources with GaltaJS at
build time, so a JavaScript library ships as compiled Java classes and runs in
the engine's transpiled mode. Its single goal, `generate-sources`, is bound to
the `generate-sources` phase and adds its output directory to the project's
compile source roots: the generated classes are compiled with the project's own
sources. The project also needs a dependency on [`js`](../js/README.md) to run them.

The build is incremental: a file is transpiled again only when one of its
outputs is missing or older than the file, or when the configuration, the plugin
or the engine changed (recorded in a stamp file next to the output directory).
A file transpiled again to the same content is not rewritten: it keeps its
modification time, so the Java compiler does not compile it again.
Generated files whose JavaScript source is gone are deleted when the output
directory is inside the build directory.

## Usage

```xml
<plugin>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-transpiler-maven</artifactId>
  <executions>
    <execution>
      <id>generate</id>
      <goals><goal>generate-sources</goal></goals>
    </execution>
  </executions>
  <configuration>
    <sourceDirectory>${basedir}/js</sourceDirectory>
    <includes><include>**/*.js</include></includes>
  </configuration>
</plugin>
```

The `galta-bom` (see [Modules](../../../README.md#modules)) manages dependency
versions, not plugin versions: declare `<version>` on the plugin, or in your
`pluginManagement`. A file `a/b/calc.js` becomes the class `js.a.b.Calc`
(the package prefix is `jsPackage`).

## Parameters

| Parameter | Default | Purpose |
|---|---|---|
| `sourceDirectory` | `${basedir}/js` | Root of the JavaScript sources |
| `outputDirectory` | `${project.build.directory}/generated-sources/js` | Where the Java sources are written |
| `jsPackage` | `js` | Java package of the generated classes |
| `includes` / `excludes` | `**/*.js` / none | Ant-style file filters |
| `sourceMap` | `false` | Embed a GaltaJS line map in the class |
| `sourceCode` | `false` | Embed the original JavaScript in the class |
| `mapFile` | `true` | Also write the map to a `.jsmap` file |
| `sourceFile` | `true` | Also copy the JavaScript source next to the output |
| `sourceInComments` / `maxSourceInComments` | `false` / `64` | Emit the source as a numbered comment header |
| `splitCode` | `false` | Split large functions and literals to stay under class-file limits |
| `commonJS` | `false` | Compile the files as CommonJS modules (`require`, `exports`, `module`) |
| `galtaJs` | `false` | Enable the GaltaJS extensions while parsing |
| `encoding` | `${project.build.sourceEncoding}` (UTF-8) | Source encoding |
| `failOnError` | `true` | Fail the build on a transpilation error (otherwise only logged) |
| `followSymlinks` | `true` | Follow symbolic links when scanning |
| `verbose` | `false` | Log every file |
| `skip` | `false` | Skip the transpilation (property `galtajs.transpiler.skip`) |

## Contents

- `org.monflabs.galtajs.maven.JSTranspilerMojo` - the `generate-sources` goal, driving the engine's `PathTranspiler`
- `META-INF/m2e/lifecycle-mapping-metadata.xml` - tells Eclipse (m2e) to execute the goal, on full builds only (not on incremental builds)

The [parent-js-precompiled](../parent-js-precompiled/README.md) modules and [js-transpiler-maven-tests](../js-transpiler-maven-tests/README.md)
use the plugin.

## Documentation

- [Execution Modes - Maven plugin](../../../docs/GaltaJS/UserGuide/ExecutionModes.md#maven-plugin) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/ExecutionModes?id=maven-plugin))
- [Transpiler architecture](../../../docs/GaltaJS/Architecture/Transpiler.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/Transpiler))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
