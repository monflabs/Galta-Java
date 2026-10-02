# Galta Demo Data - DVD Rental

> Not published to Maven Central: the license of the dataset is still to be confirmed.

A small extract of the PostgreSQL "dvdrental" sample database (a DVD rental
store: films, actors, categories, languages), bundled as JSON collections in
`mini-dataset/dvdrental/*.json`, plus a sample SQL query
(`sample-query.sql`).

## Usage

Within the repository, or after a local `mvn install`:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>demo-dvdrental</artifactId>
  <version>${project.version}</version>
</dependency>
```

```java
MiniJsonDataSet data = new MiniJsonDataSet(JsonFactory.get());
JsonArray films = data.getFilms();
```

## Contents

Package: `org.monflabs.demodata.dvdrental`.

- `MiniJsonDataSet` - loads every collection as a `JsonArray` with the given
  `JsonFactory`: `getActors()`, `getCategories()`, `getFilms()`,
  `getLanguages()`, `getFilms_actors()`, `getFilms_actors_direct()`,
  `getFilms_categories()`.
- `Schema` - the `COLLECTIONS` enum, with each collection's resource name.

## Source and license

| | |
|---|---|
| Source | PostgreSQL Tutorial sample database, https://www.postgresqltutorial.com/postgresql-sample-database/ |
| Origin | to be confirmed - the dvdrental schema is a PostgreSQL port of MySQL's "Sakila" sample database |
| License | to be confirmed (not stated by the source page) |

The module is not published to Maven Central until the license is reviewed.
