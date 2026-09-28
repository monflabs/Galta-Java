# Debugger

Debugging is layered: an engine-neutral API in `debug/api` shaped after the Chrome DevTools Protocol, its implementation in `debug/api/impl` that hooks the interpreter (and optionally transpiled code), and a CDP server in `cdp/` that exposes it over WebSocket or in process. Front ends are the `js-debugger` Swing application and the playground. Usage is in [Debugging](/GaltaJS/UserGuide/Debugging).

## API (`debug/api`)

`Debugger`: `addListener`/`removeListener(DebugListener)`, `executionContexts()`, `scripts()`, `currentPause()`, `setBreakpoint(BreakpointRequest)`, `removeBreakpoint(id)`, `setBreakpointsActive`, `setSkipAllPauses`, `setPauseOnExceptions(PauseOnExceptions.ALL|UNCAUGHT|CAUGHT)`, `pause()`, `pauseOnStart()`, `evaluate(ExecutionContext, expression)`, `call(ExecutionContext, Callable)`, `values()`, `close()`.

Supporting types: `DebugListener` (default methods `executionContextCreated`, `scriptParsed`, `breakpointResolved`, `paused`, `resumed`, `exceptionThrown`, `consoleCalled`, `executionFinished`), `PausedEvent` (`thread()`, `reason()`, `frames()`, `hitBreakpoints()`, `exception()`, `context()`, `isResumed()`, `resume()`, `stepInto()`, `stepOver()`, `stepOut()`), `PauseReason`, `DebugFrame` (`id()`, `functionName()`, `location()`, `functionLocation()`, `scopes()`, `thisValue()`), `DebugScope`, `DebugProperty`, `DebugValues`, `DebugScript`, `Location(script, line, column)`, `Breakpoint`, `BreakpointRequest(url, urlRegex, scriptId, line, column, condition)` with `BreakpointRequest.at(url, line)`, `ConsoleEvent`, `ExceptionEvent`, `DebugOptions`, `DebuggerFrontend`, `DebugException`.

## Implementation (`debug/api/impl`)

`DebuggerImpl(JSScriptUnit rootUnit, Supplier<? extends JSGlobalContext> contextFactory)`:

```
start()
  globalContext = contextFactory.get()        must implement Debuggable (Interpreted/TranspiledGlobalRuntimeContext do)
  executionContext / DebugScriptImpl created  -> listeners: executionContextCreated, scriptParsed
  pending breakpoints resolved against the script -> breakpointResolved
  debuggable.setDebugHook(this); DebugRuntime.ACTIVE_SESSIONS++
  new Thread("GaltaJS-Debug-<name>") { rootUnit.executeWithContext(globalContext) }
      finally: setDebugHook(null); ACTIVE_SESSIONS--; listeners.executionFinished()
```

Pauses are reported on the script thread; `PausedEvent.resume()` may be called from another thread (the queue pattern used in `doc_examples/DebuggerExamples.java`). `DebuggerImpl` implements `DebugHook` (`onStatement`, `onExit`, `onExceptionThrown`, `onStateChanged`, `onWoken`) and stops the script with `JSRuntimeInterruptException` on `close()`.

Hooks reach the engine only when the environment is built with `debug(true)`: the parser then wraps statements in `node/debug/ASTDebugHook` (`DebuggableNode`), and `debugger;` is `node/debug/ASTDebugger`. The hook is stateless (breakpoints live in the session), so instrumented ASTs remain cacheable. `DebugRuntime` is the mode-neutral pause point: a JVM-wide `public static volatile int ACTIVE_SESSIONS` fast path, `hookFor(JSRuntimeContext)`, `checkStatement(context, line, col, debuggerStatement)`, `checkPause(...)`, and `shouldReportException(Throwable)`, a `ThreadLocal` identity de-duplication so one throw unwinding through nested statement wrappers pauses once.

Transpiled code is debuggable when generated with `JSTranspilerOptions.debuggable(true)`: `ASTBlock.transpileBlockStatements()` then emits a guarded `JSTranspiledUnit.debugStatement()` call before each top-level statement (one volatile read per statement when no debugger is attached; byte-identical output when the option is off).

## CDP server (`cdp/`)

`CdpServer.open(debugger, DebugOptions.parse("9229", false))` returns a `Handle` (`AutoCloseable`) serving Node-inspector-compatible discovery documents plus a WebSocket endpoint (`cdp/ws/HttpWebSocketServer`, `WebSocketConnection`). `InProcessCdpServer.open(debugger, options)` offers the same protocol over an in-memory channel for tests and embedded UIs (`inprocess/ClientEnd`, `ServerEnd`). `cdp/protocol/CdpSession` dispatches to `DebuggerDomain` and `RuntimeDomain`; `RemoteObjects` maps values, `Params`/`CdpError`/`cdp/json/Json` handle the wire format; `CdpTransport`/`CdpClientChannel` abstract the transport.

`DebuggerDomain` handles: `enable`, `disable`, `setBreakpointByUrl`, `setBreakpoint`, `removeBreakpoint`, `setBreakpointsActive`, `setSkipAllPauses`, `getPossibleBreakpoints`, `getScriptSource`, `pause`, `resume`, `stepInto`, `stepOver`, `stepOut`, `continueToLocation`, `evaluateOnCallFrame`, `setPauseOnExceptions` (`all`/`uncaught`/`caught`), `setVariableValue`; `setAsyncCallStackDepth` is accepted as a no-op. `RuntimeDomain` handles `enable`, `disable`, `evaluate`, `callFunctionOn`, `getProperties`, `releaseObject`, `releaseObjectGroup`, `runIfWaitingForDebugger`, `getIsolateId`, `getHeapUsage`, `compileScript`, `globalLexicalScopeNames`, `terminateExecution`, `discardConsoleEntries`. Breakpoints set on scripts not yet compiled resolve later through `DebugListener.breakpointResolved`.

## Front ends

`js-debugger` is a Swing UI (`org.monflabs.js.debugger.ui.SwingDebugger`, `DebuggerPanel`, panels for source, scopes, call stack, breakpoints, watches, console, script navigator) talking CDP through its own `ui/cdp/CdpConnection` over a JDK WebSocket (`JdkWebSocketChannel`), with an in-process variant. `js-playground` hosts scripts interactively and unifies "Debug" and "External debugger" into one cancel-and-restart session.

## Source

`debug/api/*.java`, `debug/api/impl/DebuggerImpl.java`, `debug/api/impl/DebugRuntime.java`, `debug/api/impl/DebugHook.java`, `debug/api/impl/Debuggable.java`, `node/debug/ASTDebugHook.java`, `node/debug/ASTDebugger.java`, `cdp/CdpServer.java`, `cdp/inprocess/InProcessCdpServer.java`, `cdp/protocol/CdpSession.java`, `cdp/protocol/DebuggerDomain.java`, `cdp/protocol/RuntimeDomain.java`, `cdp/ws/HttpWebSocketServer.java`, `rt/transpiler/JSTranspiledUnit.java` (`debugStatement`), `galta/parent-js/js-debugger/`.
