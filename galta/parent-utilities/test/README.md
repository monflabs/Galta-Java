# Galta Test Support

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/test?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/test)

The test toolkit shared by the Galta modules: a JUnit 3 base test case,
golden-file ("template") assertions for text and JSON, reflective access to
private members, a resource-leak detector and a check that every test class is
registered in a suite. Unlike the other utility modules, it depends on the
GaltaJSON `json` and `json-config` modules and on Byte Buddy, so use it only as
a test dependency.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>test</artifactId>
  <scope>test</scope>
</dependency>
```

The version comes from the `galta-bom` (see the [root README](../../../README.md#modules)),
or declare `<version>` directly. JUnit is declared `provided`: the project
brings its own `junit:junit` (4.x).

```java
// Everything is relative to the working directory, i.e. the module being tested
File root = support.getProjectRoot();                  // new File(System.getProperty("user.dir"))
support.getTestJavaDirectory();                        // <root>/src/test/java
support.getTargetDirectory();                          // <root>/target
```

## Contents

Package `org.monflabs.tests`:

- `__BaseTestCase` - the JUnit 3 base class (Galta modules extend it through a
  one-line `ProjectTestCase`): sets the default time zone to `America/New_York`
  around each test and checks resource leaks when the leak agent is installed
- `UnitTestSupport` - the helper behind `__BaseTestCase.support`: project
  folders, golden-file templates stored under `tests/<class path>/<name>`
  (created with `-Dmonflabs.tests.saveTemplates=missing|all`, refused when `CI`
  is set), JSON comparisons
- `JavaAccessor` - reads fields and calls methods or constructors whatever their visibility
- `SuiteGuard` - fails when a test class of the module is missing from the
  `All*Tests` suites surefire runs (`@SuiteGuard.NotInSuite("reason")` excludes one)
- `leaks` - `ResourceLeakAgent` (Byte Buddy, attached at runtime),
  `ResourceTracker` and the JUnit 4 `ResourceLeakRule`; the design notes are in
  [`leaks/README.md`](src/main/java/org/monflabs/tests/leaks/README.md)

The leak detector is turned on for `__BaseTestCase` with
`-Dmonflabs.tests.trackLeaks=true` (the `monflabs.tests.trackLeaks` Maven
property of `monflabs-parent`); the test forks need
`-XX:+EnableDynamicAgentLoading`, which `monflabs-parent` passes to surefire.

## Documentation

- [Test Support](../../../docs/Utilities/Testing.md)
  ([online](https://monflabs.github.io/Galta-Java/#/Utilities/Testing))
- [Test suites, coverage and test options](../../../docs/BuildAndRelease.md#test-suites-coverage-and-test-options)
- [API reference](https://monflabs.github.io/Galta-Java/#/API)

The doc samples are tests in `src/test/java/doc_examples/testing`; run the
module's tests from `galta/` with `mvn test -pl parent-utilities/test --also-make`.
