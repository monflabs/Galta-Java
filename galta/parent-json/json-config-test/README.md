# Galta JSON Config Tests

> Not published to Maven Central: the test suite of [json-config](../json-config/README.md).

The module has no main code. `AllJsonConfigTests` (the only class surefire
runs) groups:

- `tests.config` - `JsonFileConfigTest`, `CustomJsonConfigTest`, `KeyEncryptorTest`, plus regression and hardening tests
- `doc_examples.config.ConfigExamples` - the samples of the
  [Configuration](../../../docs/GaltaJSON/Modules/Config.md) page, one test per sample

A suite guard fails the run when a test class of the module is missing from
the suite. The input files are in `src/test/resources/config`, and the
expected results (golden-file templates, see
[Testing](../../../docs/Utilities/Testing.md)) in `tests/`.

Run it from `galta/`:

```sh
mvn test -pl parent-json/json-config-test -am
```

The `verify` phase writes the JaCoCo coverage of the tested modules to
`target/site/jacoco-aggregate`.
