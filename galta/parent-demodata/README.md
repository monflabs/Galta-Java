# Galta Demo Data Parent

> Not published to Maven Central: the licenses of the bundled datasets are still to be confirmed.

Sample datasets for demos and tests. Each module bundles its data as classpath
resources, plus a small Java loader that reads them:

| Module | Dataset | Loader |
|---|---|---|
| [demo-northwind](demo-northwind/README.md) | Northwind trading company (CSV + PostgreSQL scripts) | `NorthwindTables` |
| [demo-dvdrental](demo-dvdrental/README.md) | DVD rental store extract (JSON) | `MiniJsonDataSet` |
| [demo-tour-de-france](demo-tour-de-france/README.md) | Tour de France editions, 1903 to 2022 (CSV) | `TDFDataLoader` |

Each module's README records the data source and license status. No Galta
library depends on these datasets. Within the repository (or after a local
`mvn install`), declare a module with the `org.monflabs.galta` group id and the
project version; they are not in the `galta-bom`.
