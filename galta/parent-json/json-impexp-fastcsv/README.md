# Galta JSON Import/Export - CSV

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-impexp-fastcsv?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-impexp-fastcsv)

CSV source and target for [json-impexp](../json-impexp/README.md), based on
[FastCSV](https://github.com/osiegmar/FastCSV) (`de.siegmar:fastcsv`). A CSV
row is a flat `JsonObject`, one property per column, so CSV files can be
exported to or imported from any other source or target of `json-impexp`.

The source reads the columns from a header row or from declared columns, with
optional cell readers to convert the text, and computes the document keys,
collections and timestamps with functions. The target infers the columns from
the first document or uses declared ones, and can escape the cells a
spreadsheet would run as formulas (CSV injection).

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-impexp-fastcsv</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
CsvSource source = CsvSource.newBuilder()
        .reader(() -> new StringReader(csv))                // called for each import
        .column("price", s -> new BigDecimal(s))            // cell readers for some columns
        .keyFunction(row -> (String)row.get("id"))
        .collectionFunction(row -> "products")
        .build();
// products/p1 -> {"id":"p1","name":"Pen","price":1.20,"since":"2024-01-15"}
```

## Contents

Under `org.monflabs.json.impexp.csv`:

- `CsvSource` - reads CSV rows as documents (header or positional columns, separators, empty cells, row count estimate)
- `CsvTarget` - writes documents as CSV rows; deletions are ignored
- `CsvBase` - a marker interface implemented by both

## Documentation

- [Import & Export, CSV](../../../docs/GaltaJSON/Modules/ImportExport.md#csv) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/ImportExport?id=csv>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
