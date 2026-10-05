# Galta JSON Performance

> Not published to Maven Central: benchmarks of the Galta JSON library, compared with Jackson (tree model) and Gson (`JsonElement`).

## JMH benchmarks

The benchmarks are in `src/main/java/performance/jmh`:

| Class | Measures |
|---|---|
| `ParseBenchmark` | Parsing to a tree, from a `String` and from UTF-8 bytes (`InputStream`) |
| `StringifyBenchmark` | Writing a tree, compact and pretty printed, and a parse + stringify round trip |
| `ReadBenchmark` | Reading values from a parsed tree (JSONPath query and a hand-written loop) |
| `AccessBenchmark` | Typed access to a parsed tree with Galta, Jackson, Gson and org.json: a loop over the World Cup matches and a short path |

The datasets (`Datasets`) are generated deterministically: `small` (~200 B), `medium`
(~50 KB of API-like records), `large` (~5 MB), `numbers` (ints, longs, decimals, exponents),
`strings` (long strings, escapes, `\u` escapes, non-ASCII and surrogate pairs), `deep`
(500 nested levels), `pretty` (the medium dataset, indented) and `worldcup` (a real document).

The `benchmarks` profile builds an executable jar with the maven-shade-plugin. Build and
run, from `galta/`:

```sh
mvn -o -f parent-json/json-performance/pom.xml clean package -Pbenchmarks
java -jar parent-json/json-performance/target/benchmarks.jar                      # everything (~35 minutes)
java -jar parent-json/json-performance/target/benchmarks.jar "galta" -f 1          # Galta only, one fork
java -jar parent-json/json-performance/target/benchmarks.jar "ParseBenchmark" -p dataset=numbers -prof gc
```

`-o` builds offline, against the Galta modules already installed in the local repository.
`-prof gc` adds the allocation per operation, `-prof jfr` records a flight recording to
find the hot methods (`jfr view hot-methods <file>`).

The results of a run before and after the parser/stringifier optimizations are kept in
`benchmarks/results` (`baseline.csv` and `after.csv`: score, error and allocated bytes per
operation for each benchmark; run with `-rf json` to get the full JMH output).

`benchmarks/results/2026-10-04.csv` holds the run of the second optimization pass (the
Eisel-Lemire number parsing, the shortest double digits written straight into the buffer,
strings copied then scanned), in microseconds per operation: the Galta parse and stringify
benchmarks before and after, and Jackson on the same machine. The `deep` stringify
benchmarks are missing from it: their setup fails with recent Gson, which limits the nesting
to 255 levels. The comparison in the [GaltaJSON overview](../../../docs/GaltaJSON/README.md)
comes from this run, but for its stringify rows.

`benchmarks/results/2026-10-05-stringify.csv` holds the stringify benchmarks of the third
pass (a lookup table to scan the strings, escapes written straight into the buffer, valid
surrogate pairs on the fast path, the digits of the longs written in place, the Java text of
a double kept when it is already the JavaScript one, the text of a large output built once
from segments, and the growable buffers reused by the next serializers): before, after, and
Jackson in the same run. The stringify rows of the GaltaJSON overview come from it.
