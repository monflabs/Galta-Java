# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-demodata` packages sample datasets for demos and tests. Each sub-module bundles its data as classpath resources, plus a small Java loader that reads them through the module's own class loader.

## Sub-modules

| Module | Content | Loader | Tests |
|---|---|---|---|
| `demo-northwind` | Northwind trading company: `northwind/csv/*.csv`, PostgreSQL scripts | `pojo.NorthwindTables` (CSV -> typed records; empty field -> `null`, escaped `\n` -> line break), `NorthwindTables.openResource()` | `NorthWindImportTest` |
| `demo-dvdrental` | DVD rental mini dataset: `mini-dataset/dvdrental/*.json` | `MiniJsonDataSet` (one `JsonArray` per collection) | `MiniJsonDataSetTest` |
| `demo-tour-de-france` | Tours, stages, winners, finishers: `tourdefrance/*.csv` | `TDFDataLoader.loadTours()` (CSV -> `Tour` POJOs, `getDistanceKm()`), `TDFDataLoader.openResource()` | `TDFDataLoaderTest` |

`demo-airline` (a schema description without data) and `demo-retail` (an Amazon UK sample with no loader) were removed: nothing used them.

## Usage

```java
JsonArray films = new MiniJsonDataSet(JsonFactory.get()).getFilms();   // demo-dvdrental
List<Tour> tours = TDFDataLoader.loadTours();                          // demo-tour-de-france
NorthwindTables tables = new NorthwindTables();                        // demo-northwind
```

No Galta module depends on these datasets, but peer repositories do: `demo-northwind` is used by DraftDB and by the Galta-Java-Private incubator (`demo-northwind-jdbc-incubator`, which keeps its own copy of the `pojo` classes in the same package - so the record field types must not change), and `demo-dvdrental` by the Galta-Java-Private JSON queries incubator (`MiniJsonDataSet`).

The modules are not published to Maven Central: each README records the data source, and the licenses are still to be confirmed.
