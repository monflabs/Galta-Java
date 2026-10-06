# Getting Started

GaltaJS is a JavaScript engine written in Java. This page shows how to depend on it, create an environment, and evaluate the first expressions and scripts. The engine works with plain Java values (there is no `JSNumber` or `JSString` wrapper), which is what makes it easy to embed.

?> To experiment before writing any Java, open the [GaltaJS playground](playground/index.html ':ignore'): it runs in the browser (through CheerpJ) and evaluates scripts with the engine and its extensions.

## Maven dependency

The engine is published as Maven artifacts under the `org.monflabs.galta` group. The core engine is the `js` artifact; its own dependencies (`json`, `javacompiler`, `utilities`) are transitive, so a standard application only needs:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js</artifactId>
  <version>0.8.0</version>
</dependency>
```

For a single-jar deployment, the `js-all` module builds the engine and its dependencies into one shaded jar; it is not published to Maven Central, so build it from source (see [Companion Modules](/GaltaJS/UserGuide/CompanionModules)). Java 21 or later is required.

## Creating an environment

Everything starts with a `JSEnvironment`. It is a *realm*: it holds the standard objects (`Object`, `Array`, `Math`, `JSON`, ...), the configuration flags, the registered libraries and the caches. Two prebuilt configurations exist in the `environments` package:

- `JavaScriptEnvironment.create()` — plain ECMAScript with the standard library, no GaltaJS extension.
- `GaltaJSEnvironment.create()` — all GaltaJS extensions enabled, plus the standard library and the Java interop library.

Sample: `doc_examples/CreateEnvironment.java` (`testJavaScriptExpression`, `testGaltaJSExpression`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
Object o = env.evaluateExpression("1+Math.abs(-2)");
// The result is a plain java.lang.Integer - no wrapper type
assertEquals(3, o);
assertEquals(Integer.class, o.getClass());
```

```java
JSEnvironment env = GaltaJSEnvironment.create();
Object o = env.evaluateExpression("1m / 3m");
// 'm' literals are java.math.BigDecimal values, computed with MathContext.DECIMAL128 by default
assertEquals(BigDecimal.class, o.getClass());
assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), o);
```

The `m` suffix is one of the [number extensions](/GaltaJS/Extensions/Numbers): the literal is a `java.math.BigDecimal` and the division is exact to the configured `MathContext` (34 digits by default).

### Using the builder

The prebuilt environments are shortcuts over `JSEnvironment.newBuilder()`. The builder exposes every option individually; `enableGaltaJSExtensions()` switches on the GaltaJS flags at once. Note that flags are all it does: the standard objects come from `StandardLibrary`, which must be registered explicitly, otherwise `Math`, `JSON`, `console` and friends do not exist.

Sample: `doc_examples/CreateEnvironment.java` (`testGaltaJSWithBuilder`)

```java
JSEnvironment env = JSEnvironment.newBuilder()
        .enableGaltaJSExtensions()
        .registerLibrary(new StandardLibrary())
        .build();
Object o = env.evaluateExpression("1m / 4m + Math.abs(-1)");
assertEquals(new BigDecimal("1.25"), o);
```

The full list of options is in [Environments & Configuration](/GaltaJS/UserGuide/Configuration).

## Expressions versus scripts

`JSEnvironment` offers two one-line evaluation methods:

- `evaluateExpression(String)` runs the code synchronously, with no event loop. It can use functions, classes, libraries and modules, but nothing asynchronous: creating a promise reaction, a generator or an `await` throws.
- `evaluateScript(String)` runs the code with a full event loop (`JSAsyncExecutor`). Promises, `async` functions, top-level `await` and timers all work, and the method returns once every pending task has run.

Sample: `doc_examples/GettingStartedExamples.java` (`testExpressionVersusScript`)

```java
JSEnvironment env = JavaScriptEnvironment.create();

// evaluateExpression: synchronous, no event loop
assertEquals(6, env.evaluateExpression("[1,2,3].reduce((a,b) => a+b, 0)"));

// evaluateScript: runs with an event loop, so promises settle before it returns
assertEquals(6, env.evaluateScript("await Promise.resolve([1,2,3]).then(a => a.reduce((x,y) => x+y, 0))"));

// Anything asynchronous inside evaluateExpression is an error
try {
    env.evaluateExpression("Promise.resolve(1).then(x => x)");
    fail("expected an error");
} catch(JSRuntimeException e) {
    assertTrue(e.getMessage().contains("This executor does not support micro-tasks"));
}
```

Both methods return the completion value of the last statement. See [Async & Event Loop](/GaltaJS/UserGuide/Async) for the details of the two executors.

## A first program

`console.log()` writes to the output stream of the global context. The `captureOutput()` helper used in the samples creates a context, redirects its output stream and runs the script; the same code is shown in [Executing Code](/GaltaJS/UserGuide/ExecutingCode).

Sample: `doc_examples/GettingStartedExamples.java` (`testFirstProgram`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
Captured run = captureOutput(env, """
    const names = ['Ada', 'Linus'];
    for (const n of names) {
        console.log(`Hello ${n}`);
    }
    names.length
    """);
assertEquals(2L, run.value());   // array lengths are java.lang.Long values
assertEquals(lines("Hello Ada", "Hello Linus"), run.output());
```

## Values are Java objects

The engine never wraps values. A JavaScript number is an `Integer`, `Long`, `Double` (or `BigInteger`, `BigDecimal` with the extensions), a string is a `String`, a boolean is a `Boolean`, a `Date` is a `java.util.Date`. Objects are `JSObject` instances, which also implement the `JsonObject` interface of the Galta JSON library; arrays are `JSArray` instances, which also implement `java.util.List`.

Sample: `doc_examples/GettingStartedExamples.java` (`testValuesAreJavaObjects`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
assertEquals(Integer.class, env.evaluateExpression("40 + 2").getClass());
assertEquals(Double.class,  env.evaluateExpression("0.5 * 3").getClass());
assertEquals(String.class,  env.evaluateExpression("'a' + 'b'").getClass());
assertEquals(Boolean.class, env.evaluateExpression("1 < 2").getClass());
assertTrue(env.evaluateExpression("new Date(0)") instanceof Date);

// Objects are JSObject instances, which also implement the JSON library's JsonObject
Object obj = env.evaluateExpression("({name: 'galta', tags: ['a', 'b']})");
assertTrue(obj instanceof JSObject);
assertTrue(obj instanceof JsonObject);
assertEquals("galta", ((JsonObject)obj).get("name"));

// Arrays are JSArray instances, which also implement java.util.List
Object arr = env.evaluateExpression("[1, 'two', true]");
assertTrue(arr instanceof JSArray);
assertTrue(arr instanceof List);
assertEquals(List.of(1, "two", true), list(arr));
```

[Values & Java API](/GaltaJS/UserGuide/Values) covers the complete mapping and the helper classes for navigating values and calling functions.

## Transpiling at build time

Besides interpreting scripts at runtime, GaltaJS can translate JavaScript into Java source that is compiled with the rest of a project. The easiest way is the Maven plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.monflabs.galta</groupId>
      <artifactId>js-transpiler-maven</artifactId>
      <version>0.8.0</version>
      <executions>
        <execution>
          <id>generate</id>
          <goals>
            <goal>generate-sources</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <sourceDirectory>${basedir}/js</sourceDirectory>
        <includes>
          <include>**/*.js</include>
        </includes>
        <!-- <sourceMap>true</sourceMap> -->
      </configuration>
    </plugin>
  </plugins>
</build>
```

See [Execution Modes](/GaltaJS/UserGuide/ExecutionModes) for the plugin parameters and the programmatic transpiler API.

## Gotchas

- `JSEnvironment.newBuilder().enableGaltaJSExtensions().build()` has no `Math`, `JSON` or `console`: register `StandardLibrary` (or start from `JavaScriptEnvironment.newBuilder()` / `GaltaJSEnvironment.newBuilder()`).
- `evaluateExpression()` cannot run anything asynchronous; use `evaluateScript()`.
- An array's `length` is a `java.lang.Long`, not an `Integer`.
- Neither `evaluateExpression()` nor `evaluateScript()` uses the script cache; they parse on every call. Use `createScript()` to parse once and run many times.

## Source

`JSEnvironment.java`, `environments/JavaScriptEnvironment.java`, `environments/GaltaJSEnvironment.java`, `rt/builtins/standard/StandardLibrary.java`
