# Galta JSON Tests

> Not published to Maven Central: the test suite of the core [json](../json/README.md) library.

The module has no main code. The tested libraries (`json`, and the
`json-jsonpath-jayway`, `json-yaml-snakeyaml` and
`json-jsonschema-jsonschemafriend` add-ons, which have tests here too) are
compile dependencies, so the JaCoCo aggregate report covers them. Surefire runs
two suites:

- `tests.AllJsonTests` - every test class under `tests.json` (arrays, objects,
  parser and stringifier, JSON Path, JSON Pointer, `$ref`, schema metadata,
  streams, wrappers, regressions...), run twice: with the default factory and
  with the checked factory (`JavaJsonFactoryChecked`)
- `doc_examples.AllJsonDocExamplesTests` - the samples of the
  [GaltaJSON](../../../docs/GaltaJSON/README.md) pages (`ValuesExamples`,
  `ParsingExamples`, `JsonPathExamples`, `PointersExamples`,
  `CollectionsExamples`, `SchemaExamples`), one test per sample

The resources include the [JSONTestSuite](https://github.com/nst/JSONTestSuite)
parsing files (run against the strict parser by `JSONTestSuiteTest`) and the
JSON Path queries of the
[json-path-comparison](https://github.com/cburgmer/json-path-comparison)
project. Expected results (golden-file templates, see
[Testing](../../../docs/Utilities/Testing.md)) are kept in `tests/`. A suite
guard fails the run when a test class of the module is missing from the
suites.

Run it from `galta/`:

```sh
mvn test -pl parent-json/json-tests -am
```

The `verify` phase writes the JaCoCo coverage of the tested modules to
`target/site/jacoco-aggregate`.
