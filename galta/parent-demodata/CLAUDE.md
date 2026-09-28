# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-demodata` packages sample datasets used by the playground, demos, and integration tests. Each sub-module bundles its data as classpath resources.

## Sub-modules

| Module | Dataset |
|---|---|
| `demo-airline` | Airline flights, airports, routes |
| `demo-northwind` | Classic Northwind trading company (customers, orders, products) |
| `demo-dvdrental` | DVD rental store (inventory, rentals, payments) |
| `demo-retail` | Retail business transactions |
| `demo-tour-de-france` | Tour de France race stages and results |

## Usage

Modules expose their data as JSON files on the classpath. Consumers load them via `IOStreamUtil` or the `filesystem` abstraction:

```java
InputStream is = getClass().getClassLoader().getResourceAsStream("airline/airports.json");
JsonArray airports = JsonFactory.get().parseArray(is);
```

Only `demo-northwind` has a real test (`NorthWindImportTest`); the other suites are empty. No other module depends on these datasets, and they are not published to Maven Central (their data licenses have not been reviewed).
