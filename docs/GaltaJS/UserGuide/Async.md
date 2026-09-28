# Async & Event Loop

Java is multi-threaded and synchronous; JavaScript is single-threaded with an event loop. GaltaJS bridges the two with *executors*: a synchronous one for expressions and a full event loop for programs. This page shows how promises, `async`/`await`, timers and blocking Java work fit together from the embedder's point of view. The internals are described in [Async Runtime](/GaltaJS/Architecture/AsyncRuntime).

## Two executors

| Executor | Created by | Used by | Behaviour |
|---|---|---|---|
| `JSExpressionExecutor` | `env.createExpressionExecutor()` (shared singleton) | `evaluateExpression()` | Runs the code and returns. Any microtask, generator, `async` function or `await` throws. |
| `JSAsyncExecutor` | `env.createProgramExecutor()` (new instance) | `evaluateScript()`, `unit.execute()`, `JSScriptExecutor` | Runs the code, then drains the microtask, macrotask and timer queues until nothing is pending. |

Sample: `doc_examples/AsyncExamples.java` (`testPromisesNeedTheProgramExecutor`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
// evaluateScript() runs an event loop until every pending task is done
assertEquals(2, env.evaluateScript("await Promise.resolve(1).then(x => x + 1)"));
// evaluateExpression() uses the synchronous executor
try {
    env.evaluateExpression("Promise.resolve(1).then(x => x + 1)");
    fail();
} catch(JSRuntimeException e) {
    assertTrue(e.getMessage().contains("This executor does not support micro-tasks"));
}
```

When you build a global context yourself, you choose: `new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor())` or `env.createExpressionExecutor()`. A `JSAsyncExecutor` allocates its queues lazily, so a purely synchronous script does not pay for the loop.

## Reading a promise from Java

`evaluateScript()` returns the completion value after the loop has drained, so a promise it returns is already settled. `BuiltinPromise.getState()` (`PENDING`, `FULFILLED`, `REJECTED`) and `getResult()` expose it; top-level `await` is usually simpler.

Sample: `doc_examples/AsyncExamples.java` (`testReadingAPromiseFromJava`)

```java
Object result = env.evaluateScript("Promise.resolve(20).then(x => x * 2)");
// The loop has been drained, so the promise is already settled
BuiltinPromise promise = (BuiltinPromise)result;
assertEquals("FULFILLED", String.valueOf(promise.getState()));
assertEquals(40, promise.getResult());
```

## async functions and top-level await

Scripts support `async` functions, `await`, top-level `await`, async iteration and async generators.

Sample: `doc_examples/AsyncExamples.java` (`testAsyncFunctionsAndTopLevelAwait`)

```js
const sleep = (ms, v) => new Promise(resolve => setTimeout(() => resolve(v), ms));
async function fetchAll() {
    const [a, b] = await Promise.all([sleep(5, 'a'), sleep(1, 'b')]);
    return a + b;
}
await fetchAll()
// -> "ab"
```

## Timers need HostLibrary

`setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `queueMicrotask`, `atob` and `btoa` are host functions, not part of ECMAScript. They come from `library/platform/HostLibrary`, which neither prebuilt environment registers.

Sample: `doc_examples/AsyncExamples.java` (`testTimersRequireHostLibrary`)

```java
try {
    JavaScriptEnvironment.create().evaluateScript("setTimeout(() => 1, 1)");
    fail();
} catch(JSRuntimeException e) {
    assertTrue(e.getMessage().contains("Unknown identifier setTimeout"));
}
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
assertEquals(3, env.evaluateScript("""
    let n = 0;
    const id = setInterval(() => { if (++n === 3) clearInterval(id) }, 1);
    await new Promise(r => setTimeout(r, 30));
    n
    """));
```

## Ordering

Microtasks (promise reactions, `queueMicrotask`) run before macrotasks; timers go to a timed queue and are promoted when due.

Sample: `doc_examples/AsyncExamples.java` (`testEventLoopOrdering`)

```js
setTimeout(() => console.log('timer'), 5);
Promise.resolve().then(() => console.log('microtask'));
queueMicrotask(() => console.log('queueMicrotask'));
console.log('sync');
// prints: sync, microtask, queueMicrotask, timer
```

One deviation to know about: a timer that is *already due* when the loop starts draining (typically `setTimeout(fn, 0)` queued during the synchronous part of the script) is promoted before the pending microtasks and therefore runs first. Use a non-zero delay when the order matters.

## Blocking Java work as a promise

Java code called from a script must not block the event loop. `JSExecutor.asyncFunction(Callable)` runs the body on a worker thread and returns a `BuiltinPromise` that settles on the loop; the current executor is reachable from a `BaseMethod` through `JSRuntimeContext.get().getGlobalContext().getExecutor()`.

Sample: `doc_examples/AsyncExamples.java` (`testBlockingJavaWorkAsAPromise`)

```java
globals.setOwnProperty("slowLookup", new BaseMethod(env, "slowLookup", 1) {
    @Override
    public Object call(Object thisValue, Object[] args) {
        JSExecutor executor = JSRuntimeContext.get().getGlobalContext().getExecutor();
        // Runs on a worker thread; the returned promise settles on the JS event loop
        return executor.asyncFunction(() -> {
            Thread.sleep(20);
            return "value for " + args[0];
        });
    }
});
```

```js
const results = await Promise.all([slowLookup('a'), slowLookup('b')]);
results
// -> [ "value for a", "value for b" ]
```

`asyncFunction` is meant for blocking Java work; it does not follow the "synchronous until the first await" rule of JavaScript async functions (that is `runAsyncBody`, used by the engine itself).

## Driving the loop from Java

`JSExecutor` also exposes the loop for advanced hosts: `queueMicrotask(task[, atTimeMs])`, `queueMacrotask(task)`, `performMicrotaskCheckpoint()`, `drainAndShutdownIfOutermost()` (run everything pending, then shut the executor down when called from the outermost level), `drainUntil(BooleanSupplier)` (run until a condition holds) and `runWithDrainSuppressed(Supplier)` (run a body without draining, for re-entrant calls). A `JSAsyncExecutor` can be interrupted with `stop()` and released with `shutdown()`; `execute()` shuts it down automatically when the outermost execution completes.

## Threading model

- Only one thread runs JavaScript in a context at a time. The event loop thread is the caller of `execute()`.
- `await` inside an `async` function suspends a coroutine; coroutines run on virtual threads (JDK 21+) or a cached thread pool, and control is handed over synchronously, never in parallel.
- Worker tasks from `asyncFunction()` run in parallel with the loop but only touch JavaScript state when their promise settles on the loop.
- Generators are implemented with the same coroutine primitive, so `function*` bodies also run on their own thread.

## Gotchas

- `evaluateExpression()` and `createExpressionExecutor()` reject promises entirely; the error says "This executor does not support micro-tasks".
- No timers without `HostLibrary`.
- A zero-delay timer can run before pending microtasks.
- A script whose promises never settle keeps `evaluateScript()` waiting; `JSAsyncExecutor.stop()` from another thread interrupts it.
- Do not call blocking Java code directly from a script on the loop thread if other tasks are pending; use `asyncFunction()`.

## Source

`rt/executors/JSExecutor.java`, `rt/executors/JSExpressionExecutor.java`, `rt/executors/JSAsyncExecutor.java`, `rt/builtins/standard/promise/BuiltinPromise.java`, `library/platform/HostLibrary.java`
