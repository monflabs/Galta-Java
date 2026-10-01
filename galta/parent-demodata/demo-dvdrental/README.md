# DVD Rental Demo Database

A small extract of the PostgreSQL "dvdrental" sample database (a DVD rental
store: films, actors, categories, languages), bundled as JSON collections in
`mini-dataset/dvdrental/*.json`, plus a sample SQL query.

```java
MiniJsonDataSet data = new MiniJsonDataSet(JsonFactory.get());
JsonArray films = data.getFilms();
```

## Source and license

| | |
|---|---|
| Source | PostgreSQL Tutorial sample database, https://www.postgresqltutorial.com/postgresql-sample-database/ |
| Origin | to be confirmed - the dvdrental schema is a PostgreSQL port of MySQL's "Sakila" sample database |
| License | to be confirmed (not stated by the source page) |

The module is not published to Maven Central until the license is reviewed.
