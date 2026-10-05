# Generators, Profiling & Runtime

This page groups the runtime helpers of `org.monflabs.util`: generators (`generators`), a hierarchical profiler (`profiler`), console output (`Console`) and a base class for builders (`ObjectBuilder`).

## Generators

A generator runs a body that produces values one at a time with `yield`, like a Python or JavaScript generator, and the consumer pulls them as an `Iterator`. The body is ordinary Java code: loops, recursion, `try`/`finally`. GaltaJS implements JavaScript generators on top of it.

| Type | Role |
|---|---|
| `Generator<T,V>` | What the consumer holds: an `Iterator<T>` and an `AutoCloseable`, plus `next(value)`, `throwInto(t)`, `returnWith(v)` and `getReturnValue()` |
| `Yielder<T>` | What the body receives: `T yield(T value)` |
| `GeneratorImpl` | The implementation; `create(body)` or `create(executor, body)` |

The body is a `Function<Yielder<T>, V>`: it yields values of type `T` and returns a final value of type `V`, available from `getReturnValue()` once the generator has completed.

Sample: `doc_examples/util/RuntimeExamples.java` (`testGenerator`)

```java
try (Generator<Integer, String> g = GeneratorImpl.create(y -> {
    for (int i = 1; i <= 3; i++) {
        y.yield(i);
    }
    return "done";
})) {
    List<Integer> values = new ArrayList<>();
    g.forEachRemaining(values::add);          // a Generator is an Iterator
    assertEquals(List.of(1, 2, 3), values);
    assertEquals("done", g.getReturnValue());  // the body's return value
}
```

### How it runs

The body runs on its own thread and hands each value to the consumer through a rendezvous, so the body and the consumer never run at the same time. `GeneratorImpl.create(body)` uses a shared executor (`GeneratorScheduler.getExecutorService()`) that creates a virtual thread per generator. `create(executor, body)` uses the executor you pass. Each step is a thread hand-off: cheap with virtual threads, but still far more expensive than a hand-written iterator, so generators suit bodies whose control flow is hard to turn into a state machine.

Nothing runs at creation time: the body starts on the first `hasNext()`, `next()`, `next(value)`, `throwInto` or `returnWith`. Its thread is created then, so an `InheritableThreadLocal` is inherited from the thread that first resumes the generator, not from the one that created it; plain thread locals of the consumer are never visible to the body.

!> **The executor must be unbounded.** A body paused in `yield` keeps its thread until it completes or is closed. With a bounded pool (`Executors.newFixedThreadPool(n)`), once `n` generators are paused the next one never starts and its consumer waits forever; a warning is logged (`System.Logger`) when a body has not started after 10 seconds. Use the default executor, a virtual-thread-per-task executor, or `GeneratorScheduler.createPlatformExecutor()`.

!> **Don't yield inside `synchronized` code on JDK 21 to 23.** A virtual thread that parks while holding a monitor pins its carrier thread, and the default executor runs bodies on virtual threads. A body paused in `yield` inside a `synchronized` block or method keeps its carrier pinned until it is resumed; once every carrier (one per core by default) is pinned, no virtual thread can run any more and the application deadlocks. JDK 24 removes the limitation (JEP 491). Otherwise use a `ReentrantLock`, or run such bodies on platform threads with `GeneratorImpl.create(GeneratorScheduler.createPlatformExecutor(), body)`.

Sample: `doc_examples/util/RuntimeExamples.java` (`testGeneratorIsLazy`)

```java
List<String> log = new ArrayList<>();
try (Generator<String, Void> g = GeneratorImpl.create(y -> {
    log.add("started");
    y.yield("a");
    return null;
})) {
    assertTrue(log.isEmpty());      // nothing runs until the first hasNext()/next()
    assertEquals("a", g.next());
    assertEquals(List.of("started"), log);
}
```

### Sending values in

`next(value)` resumes the body with `value` as the result of the pending `yield`, then returns the next yielded value. The value given to the very first call is discarded, since no `yield` is waiting yet. `next()` and `hasNext()` resume with `null`. After the body has returned, `next(...)` throws `NoSuchElementException`.

Sample: `doc_examples/util/RuntimeExamples.java` (`testNextWithValue`)

```java
try (Generator<String, String> g = GeneratorImpl.create(y -> {
    String name = y.yield("Who are you?");     // yield() returns what next(value) sends
    String city = y.yield("Where do you live, " + name + "?");
    return name + " from " + city;
})) {
    assertEquals("Who are you?", g.next(null));      // the first value sent is discarded
    assertEquals("Where do you live, Ann?", g.next("Ann"));
    assertThrows(NoSuchElementException.class, () -> g.next("Paris"));  // body completed
    assertEquals("Ann from Paris", g.getReturnValue());
}
```

### throwInto and returnWith

`throwInto(t)` raises `t` inside the body, at the paused `yield`. The body can catch it and go on; if it does not, `t` comes out of `throwInto`. Called before the body started or after it completed, the generator is (or stays) completed and `t` is thrown directly.

Sample: `doc_examples/util/RuntimeExamples.java` (`testThrowInto`)

```java
try (Generator<String, Void> g = GeneratorImpl.create(y -> {
    try {
        y.yield("waiting");
    } catch (IllegalStateException e) {
        y.yield("recovered from " + e.getMessage());
    }
    return null;
})) {
    assertEquals("waiting", g.next());
    // Raised at the paused yield(): the body can catch it
    assertEquals("recovered from boom", g.throwInto(new IllegalStateException("boom")));
}
```

`returnWith(v)` ends the body from the paused `yield` by throwing a `GeneratorReturnSignal` there: `finally` blocks run, and the generator completes with `v` as its return value. The signal is a `RuntimeException`, so a body that catches `RuntimeException`, `Exception` or `Throwable` around a `yield` must let `GeneratorReturnSignal` through. Like `next()`, it returns the next yielded value if a `finally` block yields one; otherwise the generator is done and it throws `NoSuchElementException`.

Sample: `doc_examples/util/RuntimeExamples.java` (`testReturnWith`)

```java
List<String> log = new ArrayList<>();
Generator<Integer, String> g = GeneratorImpl.create(y -> {
    try {
        for (int i = 0; ; i++) {
            y.yield(i);
        }
    } finally {
        log.add("cleanup");
    }
});
assertEquals(Integer.valueOf(0), g.next());
// Unwinds the body from its yield(): finally blocks run, catch blocks do not
assertThrows(NoSuchElementException.class, () -> g.returnWith("stopped"));
assertEquals("stopped", g.getReturnValue());
assertEquals(List.of("cleanup"), log);
assertFalse(g.hasNext());
```

An exception thrown by the body comes out of the `hasNext()`/`next()` call that resumed it, as is:

Sample: `doc_examples/util/RuntimeExamples.java` (`testBodyException`)

```java
try (Generator<Integer, Void> g = GeneratorImpl.create(y -> {
    y.yield(1);
    throw new IllegalArgumentException("bad input");
})) {
    assertEquals(Integer.valueOf(1), g.next());
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, g::hasNext);
    assertEquals("bad input", e.getMessage());   // the body's exception, unwrapped
}
```

### Closing

A body paused in `yield` keeps its thread parked. `close()` (or try-with-resources) resumes it with `returnWith(null)` so its `finally` blocks run and the thread ends. It is safe on a generator that never started or has already completed. Resuming a generator from inside its own body (calling its `next()` while it runs) throws `GeneratorExecutingException` instead of deadlocking.

If the body yields again while it unwinds (a `yield` in a `finally` block, or a `catch` that swallows the `GeneratorReturnSignal`), `close()` does not wait: the body is driven in the background. It is asked to return once more; if it yields yet again, the `yield` throws a `GeneratorAbandonedError` (an `Error`, so that a `catch (RuntimeException e)` lets it through); a body that still yields after that is left parked in that `yield`, rather than having both threads spin forever.

Interrupting the consumer while it waits for the body, or resuming the generator while the consumer's interrupt flag is set, abandons the generator the same way: the call throws a `java.util.concurrent.CancellationException`, the interrupt flag stays set, and the generator is then completed (`hasNext()` returns `false`).

Sample: `doc_examples/util/RuntimeExamples.java` (`testCloseAndExecutor`)

```java
List<String> log = new ArrayList<>();
ExecutorService executor = Executors.newCachedThreadPool();
try {
    Generator<Integer, Void> g = GeneratorImpl.create(executor, y -> {
        try {
            y.yield(1);
            y.yield(2);
        } finally {
            log.add("released");
        }
        return null;
    });
    assertEquals(Integer.valueOf(1), g.next());
    g.close();                 // resumes the parked body so its thread can finish
    assertEquals(List.of("released"), log);
    assertFalse(g.hasNext());
} finally {
    executor.shutdown();
}
```

## Profiler

`Profiler` measures named blocks of code, including nested ones, and aggregates the wall-clock and CPU times into a tree. It is a static facade over a `JavaProfiler` (`Profiler.get()`, replaceable with `Profiler.set(...)`).

| Method | Does |
|---|---|
| `start()`, `stop()`, `isStarted()` | turn measuring on and off (`start`/`stop` print a line with `Console.log`) |
| `profile(type[, param], callable)` | runs a `Callable` and returns its result |
| `profile(type[, param], runnable)` | runs a `ProfileRunnable`, which may throw checked exceptions |
| `createSnapshot(notes)` | freezes the current tree into a `ProfilerSnapshot` |
| `reset()` | clears everything measured |

When the profiler is stopped, `profile` just runs the block. Checked exceptions come out wrapped in a `ProfilerException`, runtime exceptions unchanged. The nesting is tracked per thread, so a block opened in one thread is not the parent of blocks run in another.

Blocks are identified by `type` and `param` within their parent. In a snapshot, when a block has children of the same type with several params, they are grouped under one aggregator for the type, which holds the merged totals and has one child per param. Each `Aggregator` reports a count and, for wall and CPU time in nanoseconds, the total, average, minimum, maximum, the time spent in children and the time specific to the block. `ProfilerSnapshot.dump([printStream])` prints the tree in milliseconds.

Sample: `doc_examples/util/RuntimeExamples.java` (`testProfiler`)

```java
Profiler.reset();
Profiler.start();
try {
    for (String table : new String[] {"users", "orders", "users"}) {
        Profiler.profile("query", table, () -> {
            Profiler.profile("parse", () -> Thread.sleep(1));   // nested block
        });
    }
    ProfilerSnapshot snapshot = Profiler.createSnapshot("after load");
    Aggregator query = snapshot.getMainAggregator().getChildren().get(0);
    assertEquals("query", query.getType());
    assertEquals(3, query.getCount());                  // grouped over both parameters
    assertEquals(2, query.getChildren().size());         // query[users], query[orders]
    assertTrue(query.getTotalWallTime() >= 3_000_000);   // nanoseconds
} finally {
    Profiler.stop();
    Profiler.reset();
}
// When stopped, profile() simply runs the block
assertEquals("x", Profiler.profile("idle", () -> "x"));
```

## Benchmarks

To compare implementations, use JMH: the unpublished `utilities-performance`, `json-performance` and `js-performance` modules hold the Galta benchmarks (`mvn package -Pbenchmarks`, then `java -jar target/benchmarks.jar`). The profiler above measures the operations of a running application.

## Console

`Console` writes to `System.out`/`System.err` with `StringFormat` placeholders. `log(msg, args...)` and `err(msg, args...)` format the message (the single-argument `log(msg)` prints it as is), `exception(t[, msg, args...])` prints a message and the stack trace to `System.err`, and `nolog`/`noerr` are no-ops with the same signatures, handy for switching a trace off by renaming the call. `getMessage(t)` joins the messages of an exception and its causes, one per line. `ConsoleColors` has the ANSI escape sequences for colored terminal output (`RED`, `GREEN_BOLD`, ..., `RESET`).

Sample: `doc_examples/util/RuntimeExamples.java` (`testConsole`)

```java
PrintStream saved = System.out;
ByteArrayOutputStream out = new ByteArrayOutputStream();
System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
try {
    Console.log("{0} files in {1}", 3, "/tmp");     // StringFormat placeholders
} finally {
    System.setOut(saved);
}
assertEquals("3 files in /tmp" + System.lineSeparator(), out.toString(StandardCharsets.UTF_8));

Exception e = new RuntimeException("outer", new IllegalStateException("inner"));
assertEquals("outer\ninner", Console.getMessage(e));   // the whole cause chain
```

## ObjectBuilder

`ObjectBuilder<T>` is a base class for fluent builders. A subclass holds the settings, implements `_build()`, and may override `validate()`; `build()` checks the `@Required` fields, then calls `validate()` and `_build()`. `exception(msg, args...)` and `assertNotNull(value, name)` create or throw an `ObjectBuilderException` with a `StringFormat` message.

Sample: `doc_examples/util/RuntimeExamples.java` (`Connection`, `ConnectionBuilder`, `testObjectBuilder`)

```java
public static class Connection {
    final String host;
    final int port;
    Connection(String host, int port) {
        this.host = host;
        this.port = port;
    }
}

public static class ConnectionBuilder extends ObjectBuilder<Connection> {
    @Required
    private String host;
    private int port = 80;

    public ConnectionBuilder host(String host) {
        this.host = host;
        return this;
    }
    public ConnectionBuilder port(int port) {
        this.port = port;
        return this;
    }
    @Override
    protected void validate() {
        if (port <= 0) {
            throw exception("Invalid port {0}", port);
        }
    }
    @Override
    protected Connection _build() {
        return new Connection(host, port);
    }
}

Connection c = new ConnectionBuilder().host("example.com").port(8080).build();
assertEquals(8080, c.port);

ObjectBuilderException e = assertThrows(ObjectBuilderException.class, () -> new ConnectionBuilder().build());
assertEquals("Field host is required", e.getMessage());   // @Required
e = assertThrows(ObjectBuilderException.class, () -> new ConnectionBuilder().host("h").port(0).build());
assertEquals("Invalid port 0", e.getMessage());
```

Fields annotated with `@Required` (`org.monflabs.util.builder`), including the inherited ones, are checked for `null` by every `build()`: a `null` one throws an `ObjectBuilderException` "Field name is required". (It used to run only with a debugger attached.) Other rules belong in `validate()`, as above.
