# Debugging

GaltaJS has a debugger API (`debug/api`) that supports breakpoints, the `debugger` statement, stepping, pause on exceptions, scope inspection and expression evaluation, plus a Chrome DevTools Protocol (CDP) server that exposes it to Chrome DevTools, VS Code, or the bundled Swing debugger. Debugging works on interpreted code, and on transpiled code compiled with the `debuggable` option.

## Enabling debug hooks

Build the environment with `debug(true)`. The parser then wraps statements in debug hooks (`node/debug/ASTDebugHook`). The hooks are stateless, so debug-instrumented programs can still be cached, and they cost one volatile read per statement when no session is active.

## The Debugger API

`debug/api/impl/DebuggerImpl(JSScriptUnit root, Supplier<? extends JSGlobalContext> contextFactory)` drives one script:

1. `addListener(DebugListener)` — receive `paused`, `resumed`, `scriptParsed`, `breakpointResolved`, `exceptionThrown`, `consoleCalled`, `executionFinished`.
2. `setBreakpoint(BreakpointRequest.at(url, line))`, `pauseOnStart()`, `setPauseOnExceptions(PauseOnExceptions.NONE | UNCAUGHT | CAUGHT | ALL)`.
3. `start()` — creates the context with the factory and runs the script on a new thread named `GaltaJS-Debug-<name>`.
4. On each `PausedEvent`: `reason()` (`BREAKPOINT`, `STEP`, `DEBUG_COMMAND`, `EXCEPTION`, `DEBUGGER_STATEMENT`, ...), `frames()` (each with `functionName()`, `location()`, `scopes()`, `thisValue()`), `context()`; then `resume()`, `stepInto()`, `stepOver()`, `stepOut()`.
5. `evaluate(ExecutionContext, expression)` evaluates in the paused frame; `values()` gives access to remote object handles.
6. `close()` ends the session.

Pause events are delivered on the script thread, which is blocked until the event is resumed. Hand them to the controlling thread (a queue, as below) rather than calling `resume()` from inside the listener.

Sample: `doc_examples/DebuggerExamples.java` (`testBreakpointsAndTheDebuggerStatement`)

```java
private static final String SCRIPT = """
    let total = 0;
    for (let i = 1; i <= 3; i++) {
      total += i;
    }
    debugger;
    total;
    """;
```

```java
// debug(true) instruments the parsed scripts with debug hooks
JSEnvironment env = JavaScriptEnvironment.newBuilder().debug(true).build();
JSInterpretedUnit unit = env.createScript(SCRIPT, "sum.js");

DebuggerImpl debugger = new DebuggerImpl(unit,
        () -> new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor()));

// Pauses are delivered on the script thread; hand them to the controlling thread
LinkedBlockingQueue<PausedEvent> pauses = new LinkedBlockingQueue<>();
CountDownLatch finished = new CountDownLatch(1);
debugger.addListener(new DebugListener() {
    @Override public void paused(PausedEvent event) { pauses.add(event); }
    @Override public void executionFinished() { finished.countDown(); }
});

debugger.setBreakpoint(BreakpointRequest.at("sum.js", 3));   // inside the loop
debugger.start();

List<String> seen = new ArrayList<>();
for(int i = 0; i < 4; i++) {
    PausedEvent pause = pauses.poll(10, TimeUnit.SECONDS);
    Object total = debugger.evaluate(pause.context(), "total");
    seen.add(pause.reason() + "@" + pause.frames().get(0).location().line() + "=" + total);
    pause.resume();
}
assertTrue(finished.await(10, TimeUnit.SECONDS));
debugger.close();

assertEquals(List.of("BREAKPOINT@3=0", "BREAKPOINT@3=1", "BREAKPOINT@3=3", "DEBUGGER_STATEMENT@5=6"), seen);
```

## Chrome DevTools Protocol

`cdp/CdpServer.open(debugger, DebugOptions.parse("9229", false))` starts an HTTP/WebSocket server speaking the Node-inspector flavour of CDP (`Debugger` and `Runtime` domains: breakpoints by URL, stepping, `evaluateOnCallFrame`, `setVariableValue`, `setPauseOnExceptions`, console events). `DebugOptions.parse(spec, waitForDebugger)` accepts `"port"` or `"host:port"` (default `127.0.0.1:9229`); the second argument pauses the script until a client attaches. The returned `Handle` is `AutoCloseable`. `cdp/inprocess/InProcessCdpServer.open(...)` provides the same protocol over an in-process channel for tests and embedded UIs.

Any CDP client can attach: Chrome (`chrome://inspect`), VS Code's node debugger, or the `js-debugger` module, a Swing front end (`org.monflabs.js.debugger.ui.SwingDebugger`) with source, call stack, scopes, breakpoints, watches and console panels. The playground (`js-playground`) integrates the same debugger for interactive sessions.

## Transpiled code

Transpiled programs are debuggable when generated with `JSTranspilerOptions.newBuilder().debuggable(true)`: a guarded `debugStatement()` call is emitted before every top-level statement of each block. Without the option the generated code is identical to non-debug output. `TranspiledGlobalRuntimeContext` implements the `Debuggable` hook, so the same `DebuggerImpl` works with a transpiled unit.

## Gotchas

- Without `debug(true)` on the environment, breakpoints never resolve: the parsed program has no hooks.
- Call `resume()` / step methods from a thread other than the one delivering the event.
- Breakpoint URLs match the unit name given to `createScript(text, name)`.
- `DebuggerImpl` drives one root unit per instance; create a new one for another script.

## Source

`debug/api/Debugger.java`, `debug/api/impl/DebuggerImpl.java`, `debug/api/DebugListener.java`, `debug/api/PausedEvent.java`, `debug/api/BreakpointRequest.java`, `debug/api/DebugOptions.java`, `node/debug/ASTDebugHook.java`, `cdp/CdpServer.java`, `cdp/inprocess/InProcessCdpServer.java`, `galta/parent-js/js-debugger/`
