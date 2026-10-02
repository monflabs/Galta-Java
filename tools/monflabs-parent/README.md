# Monflabs Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta.tools/monflabs-parent?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta.tools/monflabs-parent)

The shared Maven parent of the Monflabs projects: Galta-Java itself (its root
pom references this file by relative path, so a fresh clone builds with nothing
installed) and the peer Monflabs repositories (Galta-Java-Private, UbiGen,
RestQL, DraftDB, Salesforce, Commerce, DeveloperToolbox). It holds, in one
place, everything the module poms would otherwise repeat, so they declare their
dependencies and plugins **without a version**.

Its version is a literal, not `${revision}` (it has no parent to inherit the
property from): keep it, and its `galta.version` property, equal to the Galta
version.

## Usage

```xml
<parent>
  <groupId>org.monflabs.galta.tools</groupId>
  <artifactId>monflabs-parent</artifactId>
  <version>0.8.0</version>
  <relativePath>../../Galta-Java/tools/monflabs-parent/pom.xml</relativePath>
</parent>
```

The peer repositories are checked out next to Galta-Java and point at this pom
on disk; when the relative path does not exist, Maven falls back to the local or
remote repository. To use the Galta libraries, import
`org.monflabs.galta:galta-bom:${galta.version}` (see the
[root README](../../README.md#modules)): this parent deliberately does not manage
Galta's own artifacts.

## Contents

- Properties: `maven.compiler.release` 21, UTF-8 encodings, `galta.version`,
  and the test options `monflabs.tests.trackLeaks` and `monflabs.tests.jvmArgs`
- `dependencyManagement`: the version of every third-party library used by the
  Monflabs projects
- `pluginManagement`: the version and default configuration of every Maven
  plugin, including Galta's own (`filesystem-resources-manifest`,
  `js-transpiler-maven`); surefire runs with `-XX:+EnableDynamicAgentLoading`
  and a 900 s fork timeout
- Build rules applied everywhere: `flatten-maven-plugin` (resolves `${revision}`
  in the installed poms), `maven-enforcer-plugin` (Java 21, Maven 3.8.1+, plugin
  versions required, no duplicate or dynamic versions; dependency convergence
  reported as a warning), JaCoCo, and the copy of the repository's `LICENSE`,
  `NOTICE` and `licenses/` into the `META-INF` of every jar
- Profiles: `javadoc`, `sources` (on unless `-DskipSources`), `codesigning`
  (`jarsigner`), `central` (sources, javadoc, GPG signatures and the Central
  Portal upload, `autoPublish` off), `dependency-analyze`
- Shared metadata: organization, license, developers

## Documentation

- [Building and Releasing](../../docs/BuildAndRelease.md), in particular
  [Dependency and plugin versions](../../docs/BuildAndRelease.md#dependency-and-plugin-versions)
  ([online](https://monflabs.github.io/Galta-Java/#/BuildAndRelease?id=dependency-and-plugin-versions))
