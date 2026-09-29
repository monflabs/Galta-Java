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

`buildtools/release.sh` refuses to run when these disagree. The default
`galta.version` of `smoke-test/pom.xml` can follow too (the release script
passes the version explicitly), and `CHANGELOG.md` gets a section per version.

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
   server id `central` (encrypt it with `mvn --encrypt-password` and a master
   password in `settings-security.xml` so it is not on disk in the clear):

   ```xml
   <server>
     <id>central</id>
     <username>token-username</username>
     <password>token-password</password>
   </server>
   ```

3. A GPG key whose public part is published to a key server
   (`gpg --keyserver keyserver.ubuntu.com --send-keys <key id>`). The
   passphrase is asked by `gpg-agent` when the artifacts are signed; it is
   never passed on the command line. If signing fails with *Inappropriate ioctl
   for device*, configure a GUI pinentry (`pinentry-mac`, `pinentry-gtk-2`) in
   `~/.gnupg/gpg-agent.conf`.
4. The GitHub CLI, authenticated for the repository: `gh auth login`.
5. For the documentation site: *Settings → Pages → Source* set to
   **GitHub Actions** (see [Documentation site](#documentation-site)).

### Releasing

`buildtools/release.sh` cuts a release. Rehearse it first - a dry run builds
everything, installs the jars, runs the smoke test and shows what would be
tagged and uploaded, but publishes, pushes and tags nothing:

```sh
RELEASE_DRY_RUN=1 buildtools/release.sh
```

Then, for the real release:

1. Set the release version everywhere it is declared (see [Versioning](#versioning))
   and write its section in `CHANGELOG.md` (`## <version> (<date>)`); commit and
   push to `master`.
2. Optionally run the full test262 suite first (hours):
   `RELEASE_TEST262=1 buildtools/release.sh`, or run it separately with
   `mvn clean install -P test262`.
3. Run `buildtools/release.sh` from a clean `master` in sync with `origin`.

The script:

1. checks the environment (tools, `gh` login, GPG key and a test signature,
   the Central token), a clean `master` in sync with `origin`, a free tag, a
   version without `SNAPSHOT`, and that every place declaring the version agrees;
2. builds, tests, signs and **stages** the artifacts on the Central Portal
   (`mvn -P central clean deploy`, `autoPublish` off), and checks the upload
   really happened;
3. installs the jars locally and runs the [smoke test](#smoke-test) against
   them;
4. waits while you review and **Publish** the deployment at
   <https://central.sonatype.com/publishing/deployments> (or drop it if
   something is wrong - nothing is public before that);
5. tags `master` as `v<version>` and pushes the tag;
6. creates the GitHub release, with the version's `CHANGELOG.md` section as
   notes and the runnable jars attached: `galtajs-playground-<version>.jar`
   (the desktop playground, `java -jar`) and `galtajs-all-<version>.jar` (the
   engine and its dependencies in one jar).

It holds no secrets: the Central token is read by Maven from `settings.xml`,
the GPG passphrase comes from `gpg-agent`, GitHub access from the `gh` keyring.
Other knobs (`RELEASE_YES`, `RELEASE_SKIP_CENTRAL`, `RELEASE_SKIP_SMOKE`,
`RELEASE_SKIP_GHRELEASE`, `MVN`) are described in the script's header.

The documentation is **not** published by the release script: the site is
deployed on every push to `master` (see below), so the commit being released is
already online. After the release, move the version to the next one.

### Smoke test

`smoke-test/` is a standalone Maven project - not a module of the build, never
published - that uses Galta the way an application does: it imports
`galta-bom`, depends on `json` and `js` without versions, and checks JSON
parsing/stringifying and GaltaJS script evaluation. It runs against whatever is
in `~/.m2`:

```sh
mvn install -DskipTests
mvn -f smoke-test/pom.xml test -Dgalta.version=0.8.0
```

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

## Documentation site

The documentation is published to GitHub Pages at
<https://monflabs.github.io/Galta-Java/>: the guides (the docsify site in
`docs/`), the [API reference](/API) with the javadoc of every published module,
and the GaltaJS [playground](playground/ ':ignore') running in the browser.

`.github/workflows/publish-docs.yml` publishes it on every push to `master`
(and on demand from the *Actions* tab): it runs `buildtools/build-site.sh`,
which builds the published modules and generates into `docs/`

- `docs/api/<artifactId>/`: the javadoc of every module managed by `galta-bom`,
- `docs/playground/jsplayground-cheerpj.jar`: the CheerpJ build of the
  playground, loaded by `docs/playground/index.html`,

then uploads `docs/` as the site. Both generated parts are gitignored, so the
site always documents the revision it was published from.

GitHub Pages must use **GitHub Actions** as its source (*Settings → Pages →
Source*). Pages publishes from a **public** repository, or from a private one
only on a paid plan (GitHub Pro, Team or Enterprise), and the site is public
either way. While `monflabs/Galta-Java` is private on a free plan, the workflow
fails at its deploy step.

See [Generating the Documentation](/Documentation) to preview the site locally.

## Module dependency order

```
utilities  →  json  →  ui  →  playground  →  js (playground)
                               demodata
```

The `js` engine depends on `json` and on `javacompiler` (from `utilities`);
`js-playground` ties everything together. Building with `--also-make` honours
this order automatically.
