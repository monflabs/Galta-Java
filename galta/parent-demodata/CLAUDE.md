# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-demodata` packages sample datasets for demos and tests. Each sub-module bundles its data as classpath resources, plus (for some) a small Java loader.

## Sub-modules

| Module | Content | Loader | Tests |
|---|---|---|---|
| `demo-airline` | Schema description only (`Schema`, `README.md`, `doc/` data model) - no data | - | empty suite |
| `demo-northwind` | Northwind trading company: `northwind/csv/*.csv`, PostgreSQL scripts | `pojo.NorthwindTables` (CSV -> typed records) | `NorthWindImportTest` |
| `demo-dvdrental` | DVD rental mini dataset: `mini-dataset/dvdrental/*.json` | `MiniJsonDataSet` (one `JsonArray` per collection) | `MiniJsonDataSetTest` |
| `demo-retail` | Amazon UK e-commerce sample: `amazon-co/*.csv` | - (`Schema` only) | empty suite |
| `demo-tour-de-france` | Tours, stages, winners, finishers: `tourdefrance/*.csv` | `TDFDataLoader.loadTours()` (CSV -> `Tour` POJOs) | `TDFDataLoaderTest` |

## Usage

The data are plain classpath resources (CSV or JSON depending on the module), read through the module's loader when it has one:

```java
JsonArray films = new MiniJsonDataSet(JsonFactory.get()).getFilms();   // demo-dvdrental
List<Tour> tours = TDFDataLoader.loadTours();                          // demo-tour-de-france
NorthwindTables tables = new NorthwindTables();                        // demo-northwind
```

No other module depends on these datasets, and they are not published to Maven Central (their data licenses have not been reviewed).
