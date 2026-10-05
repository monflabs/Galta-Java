# Galta Utilities Tests

> Not published to Maven Central: it is the test suite of the `utilities` module, with no code of its own.

The tests of [`utilities`](../utilities/README.md) live in this separate module
(`src/test/java` only). Surefire runs two suites:

- `tests.AllUtilTests` - the unit tests, one package per area (`util`,
  `collections`, `config`, `datetime`, `dependencies`, `generators`, `io`,
  `iterators`, `model`, `path`, `profiler`), closed by a `SuiteGuard` that fails
  when a test class is not registered
- `doc_examples.AllUtilDocExamplesTests` - the samples of the
  [Utilities guide](../../../docs/Utilities/README.md)
  ([online](https://monflabs.github.io/Galta-Java/#/Utilities/)), one class per
  page in `doc_examples/util`

The `org.mozilla.javascript`, `info.adams.ryu`, `org.yuanheng.cookjson` and
`org.apache.harmony` packages hold third-party double-to-string
implementations: `DtoA` is checked against them, and the
[utilities-performance](../utilities-performance/README.md) benchmarks compare
their speed (through the test-jar of this module).

`utilities` is a compile dependency here, so the JaCoCo aggregate report of
this module covers it. The resource-leak detector of the `test` module is on
(`monflabs.tests.trackLeaks=true`, from the parent pom).

## Running

From `galta/`:

```sh
# The tests
mvn test -pl parent-utilities/utilities-tests --also-make

# The tests and the coverage report of utilities (target/site/jacoco-aggregate)
mvn verify -pl parent-utilities/utilities-tests --also-make
```
