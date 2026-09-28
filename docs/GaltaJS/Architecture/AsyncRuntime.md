# Async runtime

JavaScript is single-threaded with an event loop; Java is multi-threaded and synchronous. GaltaJS reconciles the two with an *executor* attached to every global context: a synchronous one for expressions and `JSAsyncExecutor`, a real event loop, for programs. Generators and `async` functions are coroutines built on threads that hand control back and forth, so only one side ever runs JavaScript at a time. Usage is in [Async and the event loop](/GaltaJS/UserGuide/Async).

## The executor contract

`rt/executors/JSExecutor`:

| Method | Purpose |
|---|---|
| `queueMicrotask(MicroTask)` / `queueMicrotask(MicroTask, long atTimeMs)` | Enqueue a microtask, optionally delayed until an absolute time (timers use this). |
| `queueMacrotask(MacroTask)` | Enqueue a macrotask. |
| `performMicrotaskCheckpoint()` | Drain pending tasks unless a drain is already in progress. |
| `execute(Supplier)` / `execute(Supplier, boolean async)` / `execute(Supplier, boolean, BiConsumer onGenuineCompletion)` | Run a body, then drain and shut down when outermost. The three-argument form exists because the return value of a body with top-level `await` is a placeholder. |
| `getCurrentAsyncTask()` | The `AsyncTask` being run. |
| `generator(Function<Yielder,Object>)` | Create a generator coroutine. |
| `asyncFunction(Callable)` | Run a whole body on a worker thread; returns a promise. Not spec-correct for JS async bodies; meant for blocking Java work. |
| `runAsyncBody(Callable)` | Spec AsyncFunctionStart: synchronous until the first `await`. |
| `await(Object)` | Suspend until a value settles. |
| `runWithDrainSuppressed(Supplier)`, `drainAndShutdownIfOutermost()`, `drainUntil(BooleanSupplier)` | Host-side control of the loop. |

`JSExpressionExecutor` is a stateless singleton (`JSExpressionExecutor.get()`, returned by `JSEnvironment.createExpressionExecutor()`): `execute` just calls the body and every asynchronous method throws `This executor does not support ...`. `JSAsyncExecutor` (one per program run, `createProgramExecutor()`) implements the loop.

## `JSAsyncExecutor`

The state is created lazily in a private `AsyncData`, so a purely synchronous script never pays for the loop:

```
AsyncData
  executor   : ExecutorService from GeneratorScheduler.createExecutor()  (virtual threads when available)
  lock       : ReentrantLock            wakeups : Semaphore
  microQ     : ArrayDeque<AsyncTask>    macroQ  : ArrayDeque<AsyncTask>
  timedQ     : PriorityQueue<TimedTask> ordered by readyAt, ties by timedSeq
  pendingAsync : AtomicInteger          (work in flight on worker threads)
```

The loop, `drainPendingTasks()`:

```
   loop while !STOP && hasPending():
     promote due TimedTasks -> microQ
     if microQ not empty:   run ONE microtask
     else if macroQ not empty: run ONE macrotask
     else block on wakeups.acquire()   (tryAcquire(deadline) when a timer is pending)

   hasPending() = pendingAsync > 0 || !microQ.isEmpty() || !macroQ.isEmpty() || !timedQ.isEmpty()
```

When the outermost `execute()` finishes draining it calls `shutdown()`, which clears the queues and stops the executor service. `stop()` requests termination.

Re-entrancy is governed by `private volatile boolean draining`. A nested `execute()` reached from inside a task (for example an `await import(...)` whose microtask synchronously loads and runs a module on the loop thread) must not start a second drain: doing so left `(async()=>{ await import('a.js'); })()` deadlocked, as the class comment records. The host-facing variants build on the same flag: `performMicrotaskCheckpoint()` drains only when not already draining, `drainAndShutdownIfOutermost()` is the Java-side "wait for everything", `drainUntil(condition)` is a separate loop used to settle module strongly connected components, and `runWithDrainSuppressed(...)` wraps `JSInterpretedUnit.linkModule()`'s dependency walk.

Timers (`setTimeout`/`setInterval`) are a host library, not part of the executor: `library/platform/HostLibrary` schedules them with `queueMicrotask(task, atTimeMs)` into `timedQ`. Because a due timed task is promoted to the front of the microtask run, a zero-delay timer scheduled before a promise reaction can run *before* that reaction; with any positive delay the usual `sync -> microtasks -> timer` order holds. This is a known deviation from the HTML task model.

## Async functions and `await`

`runAsyncBody(body)` is the spec-shaped driver, built on the generator primitive:

1. `makeCoroutine(body)` creates a `GeneratorImpl` whose body stores its `Yielder` in the `ThreadLocal currentDriverYielder`, runs the JavaScript body, and converts any throw into a returned `AsyncBodyError` record. Letting the throw escape into `GeneratorImpl` permanently parked worker threads (two full test262 sweeps blew the surefire shutdown timeout), hence the wrapper.
2. `startCoroutine(coro, onComplete, onError)` claims `draining` for the synchronous first step and calls `driveCoroutine`.
3. `driveCoroutine(coro, resumeArg, isThrow, ...)` calls `coro.next(v)` or `coro.throwInto(t)`. `NoSuchElementException` means the body finished; otherwise the yielded value is always an already-normalized `BuiltinPromise`, and `performPromiseThen_` re-enters `driveCoroutine` from an ordinary microtask.

`await(value)` has two paths. On a coroutine thread (`currentDriverYielder.get() != null`) it yields `BuiltinPromiseConstructor.resolve(env, value)` to the driver: cooperative suspension, no thread blocked in the loop. Otherwise (legacy path, e.g. `await` reached from `asyncFunction`) it `LockSupport.park()`s the calling thread until the promise reaction unparks it. Thenables are wrapped in a `BuiltinPromise` and re-awaited.

`asyncFunction(body)` submits the whole body to the executor service, bumps `pendingAsync`, and returns a promise that settles on the loop. It is the tool for wrapping blocking Java calls (see the `slowLookup` sample in `doc_examples/AsyncExamples.java`).

This design is the fifth attempt at async scheduling; the four previous latch-based designs were reverted. See [Internal notes](/GaltaJS/Architecture/Notes).

## Generators

`org.monflabs.util.generators.GeneratorImpl` (`parent-utilities`) runs each generator body on its own thread from `GeneratorScheduler` (virtual-thread-per-task, `USE_VIRTUAL = true`, falling back to a cached pool). Two `Exchanger<Object>` channels, `toConsumer` and `toGenerator`, decouple handing out a yielded value from delivering the resume value (one exchanger cannot express the gap before the first `next()`). `throwInto`/`returnWith` travel as signal objects; the body starts lazily so a generator call returns suspended; an `AtomicBoolean executing` guard throws `GeneratorExecutingException` on re-entrant `next()`.

Transpiled generators use the same machinery: `BuiltinFunctionTranspiler` calls `ctx.getGlobalContext().getExecutor().generator(yielder -> doExecuteGeneratorBody(...))`, and `ASTFunction.transpileFunctionBody` splits the parameter prologue into an eagerly run `initGeneratorParams(...)` separate from the lazily run body (`splitForGenerator`).

## Async generators and `for await`

`rt/builtins/standard/generator/BuiltinAsyncGeneratorPrototype` drives async generators. An `await` inside one cannot use the parking path (it would deadlock the exchanger pair), so `RuntimeUtil.awaitInGenerator_()` yields an `rt/AwaitYieldSignal` marker through the generator's own yielder; the prototype's drive loop settles it with `BuiltinPromiseConstructor.resolve(...).then_(...)` and resumes with `gen.next(v)`/`gen.throwInto(reason)` from a microtask. Sync-to-async adaptation lives in `rt/protocols/iterator/` (`AsyncFromSyncJavaIterator`, `AsyncJavaIterator`, `JavaIterator`); `rt/YieldStarDelegateResult` handles `yield*`.

## Promises

`rt/builtins/standard/promise/`: `BuiltinPromise` (`getState()`, `getResult()`, `isHandled()`, `then_`, `performPromiseThen_`, `resolvePromise`), `BuiltinPromiseConstructor`, `BuiltinPromisePrototype`, `PromiseCapability`, `PromiseReaction`. Reactions are queued as `MicroTask`s on the current executor. Once `evaluateScript()` returns, the loop has been drained, so a returned promise is already settled.

## Threading model

- `JSEnvironment` is the shared, long-lived object (synchronized accessor cache, LRU caches, prototype registry). ASTs are shared across threads, which is why node inline caches are `volatile` and guard-validated.
- `JSRuntimeContext` chains are per execution and thread-bound (`JSContext.get()`).
- JavaScript semantics stay single-threaded: the loop thread and a coroutine's thread never run JavaScript concurrently; control passes through the exchanger rendezvous. `AsyncTask` captures its parent task and context lazily so `Callstack` is only walked when an error is formatted.

## Source

`rt/executors/JSExecutor.java`, `rt/executors/JSAsyncExecutor.java`, `rt/executors/JSExpressionExecutor.java`, `rt/executors/AsyncTask.java`, `rt/executors/MicroTask.java`, `rt/executors/MacroTask.java`, `rt/AwaitYieldSignal.java`, `rt/YieldStarDelegateResult.java`, `rt/protocols/iterator/`, `rt/builtins/standard/promise/`, `rt/builtins/standard/generator/`, `rt/builtins/standard/function/BuiltinFunctionTranspiler.java`, `library/platform/HostLibrary.java`, `galta/parent-utilities/utilities/src/main/java/org/monflabs/util/generators/GeneratorImpl.java`, `.../GeneratorScheduler.java`.
