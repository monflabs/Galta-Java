# Galta JSON Performance

Benchmarks of the Galta JSON library, compared with Jackson (tree model) and Gson
(`JsonElement`). This module is not published.

## JMH benchmarks

The benchmarks are in `src/main/java/performance/jmh`:

| Class | Measures |
|---|---|
| `ParseBenchmark` | Parsing to a tree, from a `String` and from UTF-8 bytes (`InputStream`) |
| `StringifyBenchmark` | Writing a tree, compact and pretty printed, and a parse + stringify round trip |
| `ReadBenchmark` | Reading values from a parsed tree (JSONPath query and a hand-written loop) |

The datasets (`Datasets`) are generated deterministically: `small` (~200 B), `medium`
(~50 KB of API-like records), `large` (~5 MB), `numbers` (ints, longs, decimals, exponents),
`strings` (long strings, escapes, `\u` escapes, non-ASCII and surrogate pairs), `deep`
(500 nested levels), `pretty` (the medium dataset, indented) and `worldcup` (a real document).

Build and run, from `galta/`:

```sh
mvn -o -f parent-json/json-performance/pom.xml clean package -Pbenchmarks
java -jar parent-json/json-performance/target/benchmarks.jar                      # everything (~35 minutes)
java -jar parent-json/json-performance/target/benchmarks.jar "galta" -f 1          # Galta only, one fork
java -jar parent-json/json-performance/target/benchmarks.jar "ParseBenchmark" -p dataset=numbers -prof gc
```

`-prof gc` adds the allocation per operation, `-prof jfr` records a flight recording to
find the hot methods (`jfr view hot-methods <file>`).

The results of a run before and after the parser/stringifier optimizations are kept in
`benchmarks/results` (`baseline.csv` and `after.csv`: score, error and allocated bytes per
operation for each benchmark; run with `-rf json` to get the full JMH output).

## Legacy benchmarks

`performance/parsing` and `performance/accessors` hold the older, JUnit based,
micro-benchmarks (`AllJsonPerformanceTests`).
