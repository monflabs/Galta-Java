# Galta Utilities Performance

> Not published to Maven Central: JMH benchmarks of the Galta utilities.

The benchmarks are in `src/main/java/performance/jmh`:

| Class | Measures |
|---|---|
| `GeneratorBenchmark` | A generator (its body on another thread, every value handed over through an `Exchanger`) compared with an iterator, over the same boxed integers |
| `DoubleToTextBenchmark` | `DtoA.toStandard()` (JavaScript `Number::toString`) compared with `Double.toString()`, `String.format()`, and the Rhino, Ryu and cookjson implementations |

The reference double-to-text implementations stay in the test sources of
[utilities-tests](../utilities-tests/README.md), which checks `DtoA` against them; this
module reads them from its test-jar.

The `benchmarks` profile builds an executable jar with the maven-shade-plugin. Build and
run, from `galta/`:

```sh
mvn -o install -pl parent-utilities/utilities-tests -am -DskipTests      # the test-jar
mvn -o -f parent-utilities/utilities-performance/pom.xml clean package -Pbenchmarks
java -jar parent-utilities/utilities-performance/target/benchmarks.jar
java -jar parent-utilities/utilities-performance/target/benchmarks.jar "DoubleToText" -p value=1234.5678 -prof gc
```

The generator benchmark is slow by nature (two thread handoffs per value): use the `count`
parameter to keep its runs short (`-p count=1000`).
