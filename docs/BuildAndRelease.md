# Building and Releasing

Galta is a Maven mono repo. Every module is published under the `org.monflabs.galta`
group id, and the dependencies are transitive, so a consumer normally
references only the JSON or the JavaScript library plus whatever optional
modules it needs.

## Requirements

- **Java 21.** The Java and Maven versions are both enforced by
  `maven-enforcer-plugin`, so a wrong JDK fails the build immediately rather
  than part way through.
- **Maven 3.8.1** or later, enforced by the same rule.

The Java level (`maven.compiler.release`) and the source encoding are declared
once, in `monflabs-parent` (see [Dependency and plugin versions](#dependency-and-plugin-versions));
no other pom redeclares them.

## Building

The repository root `pom.xml` aggregates `tools/` (`monflabs-parent`, the
parent pom shared with the other Monflabs repositories, and the Maven plugins
used by the build) and `galta/` (the libraries). The root pom inherits
`monflabs-parent` by relative path, so a fresh clone builds from the root with
nothing installed beforehand. A first build runs from the root, which installs `tools/`; after
that, library builds can run from `galta/`.

```sh
# First build, or after a change in tools/ - from the repository root
mvn clean install
```

The commands below run from `galta/`.

```sh
# Full build
mvn clean install

# One module and the modules it depends on
mvn clean install -pl parent-js/js --also-make

# Tests for a single module
mvn test -pl parent-js/js-test-rhino
```

A plain `mvn clean install` runs the unit suites but deliberately skips the
long compliance sweeps: the full TC39 test262 suite takes hours across its
three execution modes. See [Testing & Compliance](/GaltaJS/Architecture/Testing)
for what runs when, and for how to run a sweep on its own.

The `js-test-test262` and `js-test-rhino` modules each depend on an external
suite that is fetched at a pinned commit by a Maven profile rather than being
a git submodule. After a clean clone there is nothing to do - the first build
that touches either module fetches its suite automatically.

## Release builds

These profiles add what a published artifact needs:

| Profile | Adds | Default |
|---|---|---|
| `javadoc` | Attaches the javadoc jar | off |
| `sources` | Attaches the sources jar | on, unless `-DskipSources` |
| `codesigning` | Signs the jars with `jarsigner` (project keystore) | off |
| `central` | Sources, javadoc, GPG signatures and the Maven Central upload (see [Publishing](#publishing)) | off |

```sh
# Release build
mvn clean install -P javadoc,sources,codesigning

# Same, plus the full test262 suite (all three execution modes - hours)
mvn clean install -P javadoc,sources,codesigning,test262
```

Running the `test262` profile before publishing is the point at which a
compliance regression is meant to be caught, since the routine build does not
run it.

## Versioning

The version is a Maven CI-friendly `${revision}` property, resolved at build
time by `flatten-maven-plugin`: the flattened pom that gets installed and
deployed has the version resolved and keeps everything else - name,
description, licenses, developers, scm - as written. A version bump changes:

- `<revision>` in the root `pom.xml` and in `galta/galta-bom/pom.xml` (the BOM
  has no parent to inherit it from);
- the literal `<version>` and the `galta.version` property of
  `tools/monflabs-parent/pom.xml`, and the root pom's `<parent><version>`
  (a parent is located before properties are known, so it is not `${revision}`).

## Dependency and plugin versions

Every version is declared in exactly one place:

| What | Where |
|---|---|
| Java level, encodings | `monflabs-parent` properties |
| Third-party libraries | `monflabs-parent` `dependencyManagement` |
| Maven plugins (versions and default configuration) | `monflabs-parent` `pluginManagement` |
| Galta's own artifacts | `galta-bom`, imported by `galta/pom.xml` |

Module poms therefore declare dependencies and plugins **without a version**.
The enforcer rules in `monflabs-parent` fail the build on a plugin without a
version, on duplicated dependency declarations and on dynamic versions
(`LATEST`, ranges); dependency convergence is reported as a warning on every
build (it does not fail it yet). `mvn verify -P dependency-analyze -DskipTests`
reports, per module, the dependencies that are used but not declared, or
declared but not used.

To see what can be upgraded:

```sh
mvn versions:display-plugin-updates -f tools/monflabs-parent/pom.xml
mvn versions:display-dependency-updates -f tools/monflabs-parent/pom.xml
```

Dependabot (`.github/dependabot.yml`) opens pull requests for new versions weekly.

`monflabs-parent` is also the parent of the other Monflabs repositories
(Galta-Java-Private, UbiGen, RestQL, DraftDB, Salesforce, Commerce,
DeveloperToolbox). They are checked out next to Galta-Java, and point at its
parent POM on disk:

```xml
<parent>
  <groupId>org.monflabs.galta.tools</groupId>
  <artifactId>monflabs-parent</artifactId>
  <version>0.8.0</version>
  <relativePath>../../Galta-Java/tools/monflabs-parent/pom.xml</relativePath>
</parent>
```

Maven resolves a parent POM while it reads the projects, before building
anything, so a parent that is neither on disk nor in a Maven repository fails
the whole build, even when Galta-Java is part of the same reactor (the
`monflabs-projects` aggregator). With the relative path, a fresh machine works
from the sibling checkout; when the path does not exist, Maven falls back to
the local or remote repository as usual.

## Using Galta in another project

Import `galta-bom` and declare the Galta modules without a version. The BOM only
manages Galta's own artifacts (it has no parent, so it does not push the
Monflabs third-party versions onto your project):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.monflabs.galta</groupId>
      <artifactId>galta-bom</artifactId>
      <version>0.8.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>js</artifactId>
  </dependency>
</dependencies>
```

## Publishing

Galta is published to **Maven Central** under the `org.monflabs.galta` group
id (the build tooling, including `monflabs-parent`, under
`org.monflabs.galta.tools`), through the Sonatype Central Portal. The `central`
profile, defined in `monflabs-parent` and completed by the root `pom.xml` (the
list of artifacts not published), adds everything Central requires:

- a sources jar and a javadoc jar for every jar module,
- a GPG signature (`.asc`) for every file,
- the Central Portal publisher (`central-publishing-maven-plugin`).

The project metadata Central checks is declared once: license, developers and
organization in `monflabs-parent`, url and scm in the root `pom.xml`, inherited
by every module; each module adds its own name and description. `galta-bom`,
which has no parent, carries its own copy.

### One-time setup

1. A Central Portal account with the `org.monflabs` namespace verified
   (<https://central.sonatype.com>).
2. A Central Portal user token, stored in `~/.m2/settings.xml` under the
   server id `central`:

   ```xml
   <server>
     <id>central</id>
     <username>token-username</username>
     <password>token-password</password>
   </server>
   ```

3. A GPG key whose public part is published to a key server
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <key id>`). The
   passphrase comes from `gpg-agent`, or from `-Dgpg.passphrase=...`.

### Releasing

```sh
# From the repository root
# 1. Set the release version (see Versioning for the places to change)
# 2. Full build, including the long test262 compliance sweep
mvn clean install -P test262
# 3. Build, sign and upload the bundle
mvn clean deploy -P central
```

`autoPublish` is off: the upload is validated by the Portal and then waits
under *Deployments* at <https://central.sonatype.com/publishing>, where it
is released (or dropped) by hand. Once released, tag the commit
(`git tag v<version>`) and move `<revision>` to the next version.

### What is published

Library modules, their parent poms and the build tooling are published. Test
suites, benchmarks, sample data and demo builds are not: they set
`maven.deploy.skip` and are listed in the `excludeArtifacts` of the `central`
profile. The two lists must be kept in sync when a module is added.

| Not published | Why |
|---|---|
| `utilities-tests`, `json-tests`, `json-config-test` | test suites |
| `js-test-test262`, `js-test-rhino`, `js-test-suite`, `js-transpiler-maven-tests` | compliance and integration suites |
| `json-performance` | benchmarks |
| `js-library-v8` | test-only V8 (Javet) harness |
| `js-regexp-joni-custom` | experimental regular expression engine |
| `js-all` | fat jar, built locally (`target/galtajs-all.jar`) |
| `js-playground-cheerpj` | browser build of the playground |
| `js-precompiled-typescript` | not generated yet (its transpiler step is disabled) |
| `parent-demodata` and the `demo-*` modules | sample datasets |

### Other repositories

To publish to the project's own repository (`https://maven.monflabs.org/galta`)
instead, add a `distributionManagement` block to the root `pom.xml`, with
credentials for its server id in `settings.xml`, and run
`mvn clean deploy -P javadoc,sources,codesigning`; `codesigning` signs the jars
with `jarsigner`, which is separate from the GPG signatures Central requires.

## Module dependency order

```
utilities  →  json  →  ui  →  playground  →  js (playground)
                               demodata
```

The `js` engine depends on `json` and on `javacompiler` (from `utilities`);
`js-playground` ties everything together. Building with `--also-make` honours
this order automatically.
