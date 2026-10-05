# GaltaJS Performance

> Not published to Maven Central: JMH benchmarks of GaltaJS internals.

The benchmarks are in `src/main/java/performance/jmh`:

| Class | Measures |
|---|---|
| `PropertyMapBenchmark` | The property map of the JavaScript objects (`StringPropertyMap`) compared with a `LinkedHashMap`: filling a map, reading and replacing every key, iterating the keys, for 10, 1000 and 100000 properties |

Cross-engine benchmarks (GaltaJS, Nashorn, Rhino, GraalJS) are in the separate
JavascriptPerformance project.

The `benchmarks` profile builds an executable jar with the maven-shade-plugin. Build and
run, from `galta/`:

```sh
mvn -o -f parent-js/js-performance/pom.xml clean package -Pbenchmarks
java -jar parent-js/js-performance/target/benchmarks.jar
java -jar parent-js/js-performance/target/benchmarks.jar -p size=1000 -p map=galta
```
