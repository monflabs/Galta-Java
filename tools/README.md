# Galta Tools

Build tooling shared by the Monflabs projects (group id `org.monflabs.galta.tools`):

- **`monflabs-parent`** - the parent pom of Galta-Java and of the other Monflabs
  repositories. It holds the Java level and encodings, the version of every
  third-party library (`dependencyManagement`) and of every Maven plugin
  (`pluginManagement`), the build rules (enforcer, flatten, jacoco) and the
  release profiles (`javadoc`, `sources`, `codesigning`, `central`,
  `dependency-analyze`). Declare dependencies and plugins without a version.
- **`parent-tools-maven`** - the Galta Maven plugins, currently
  `filesystem-resources-manifest` (the resource manifest used by the classpath
  `ResourceFileSystem` to list directories).

Galta's own artifacts are managed by `galta/galta-bom`, not here. See
[Building and Releasing](../docs/BuildAndRelease.md).
