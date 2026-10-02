# Galta Demo Data - Tour de France

> Not published to Maven Central: the source and license of the dataset are still to be confirmed.

Every edition of the Tour de France from 1903 to 2022, as CSV files in
`tourdefrance/` (UTF-8):

| File | Content |
|---|---|
| `tdf_tours.csv` | one row per edition: year, dates, stages, distance, starters, finishers |
| `tdf_stages.csv` | one row per stage: date, course, distance, type, winner |
| `tdf_winners.csv` | the overall winner of each edition |
| `tdf_finishers.csv` | the final ranking of each edition |
| `data_dictionary.csv` | the description of every column |

## Usage

Within the repository, or after a local `mvn install`:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>demo-tour-de-france</artifactId>
  <version>${project.version}</version>
</dependency>
```

```java
List<Tour> tours = TDFDataLoader.loadTours();
```

## Contents

Package: `org.monflabs.demodata.tourdefrance`.

- `TDFDataLoader.loadTours()` - reads `tdf_tours.csv` into `Tour` objects
  (`getDistanceKm()` gives the distance as a number; the editions before 1910,
  `Tour.ARCHIVE_YEAR`, are flagged as archived).
- `TDFDataLoader.openResource(name)` - opens any of the files.

## Source and license

| | |
|---|---|
| Source | to be confirmed - the file set and column names match the "Tour de France" data set of the Maven Analytics Data Playground, compiled from Wikipedia |
| License | to be confirmed |

The module is not published to Maven Central until the license is reviewed.
