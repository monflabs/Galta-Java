# Executing Code

`evaluateExpression()` and `evaluateScript()` are convenient, but they parse the text on every call and create a throw-away context. For anything beyond a one-liner, compile the code into a unit once, then execute it in a global context that you control: the context holds the globals, the executor (event loop) and the output streams, and can be reused across several units.

## Units: compile once, run many times

`env.createScript(text, name)` parses the text into a `JSInterpretedUnit`; `unit.execute()` runs it in a temporary context, `executeThis(this)` binds `this`, and `executeWithContext(ctx)` runs it in a context you provide. `createExpression(text)` is the same for a single expression.

Sample: `doc_examples/ExecutingCodeExamples.java` (`testCompileOnceRunManyTimes`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
JSInterpretedUnit unit = env.createScript("Math.max(...[3, 9, 4])", "max.js");
assertEquals(9, unit.execute());
assertEquals(9, unit.execute());   // the parsed program is reused
```

`createScript(text, name, flags)` accepts a combination of:

| Flag | Meaning |
|---|---|
| `JSEnvironment.SCRIPT_ADDTOCACHE` | Store the parsed program in the script cache (default of the 2-argument form; the cache itself is off unless `scriptCacheSize` is set). |
| `JSEnvironment.SCRIPT_MODULE` | Parse as an ES module (`import`/`export`, `import.meta`, module scoping). |
| `JSEnvironment.SCRIPT_COMMONJS` | Parse as a CommonJS module (`module.exports`, `exports`, `require`). |
| `JSEnvironment.SCRIPT_EVAL` | Parse as an `eval` body. Used internally by `createEvalScript()`. |

## Global contexts

A `JSGlobalContext` is the top of the runtime context chain. The interpreted implementation is `InterpretedGlobalRuntimeContext(env, executor)`; the third optional argument is the `this` value. The executor comes from `env.createProgramExecutor()` (event loop) or `env.createExpressionExecutor()` (synchronous, see [Async & Event Loop](/GaltaJS/UserGuide/Async)).

Sample: `doc_examples/ExecutingCodeExamples.java` (`testSharedContextAcrossScripts`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
// A global context holds the variables, the executor (event loop) and the output streams
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());

env.createScript("var counter = 0; function inc() { return ++counter }", "defs.js").executeWithContext(ctx);
env.createScript("inc(); inc()", "calls.js").executeWithContext(ctx);
assertEquals(3, env.createScript("inc()", "more.js").executeWithContext(ctx));

// Java can read the globals back through the same context
assertEquals(3, ctx.global("counter").intValue());
assertEquals(4, ctx.global("inc").call().intValue());
```

`ctx.global(name)` returns a `JSValue` wrapper (see [Values & Java API](/GaltaJS/UserGuide/Values)) that looks up a script-level variable first, then an own property of `globalThis`.

### Installing globals from Java

`ctx.getGlobalThis()` is the `globalThis` object; `setOwnProperty(name, value)` defines a global. Environment-wide globals are better expressed as a library (`StaticLibrary`, see [Libraries](/GaltaJS/Extending/Libraries)).

Sample: `doc_examples/ExecutingCodeExamples.java` (`testGlobalsInstalledFromJava`)

```java
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
ctx.getGlobalThis().setOwnProperty("config", JSObject.of(env, "retries", 3, "hosts", List.of("a", "b")));
ctx.getGlobalThis().setOwnProperty("answer", 42);

Object r = env.createScript("`${config.retries} retries on ${config.hosts.length} hosts, answer ${answer}`", "g.js")
        .executeWithContext(ctx);
assertEquals("3 retries on 2 hosts, answer 42", r);
```

### The `this` value

Sample: `doc_examples/ExecutingCodeExamples.java` (`testThisArgument`)

```java
JSObject self = JSObject.of(env, "name", "ctx");
assertEquals("ctx!", env.evaluateScript("this.name + '!'", self));
assertEquals("ctx!", env.evaluateExpression("this.name + '!'", self));
```

Without an explicit value, `this` at the top level is `globalThis` for scripts and `undefined` for modules.

### Output streams

`console.log()` and `console.error()` write to `ctx.getOutStream()` / `ctx.getErrStream()`; both default to `System.out` / `System.err` and can be replaced with `setOutStream()` / `setErrStream()`.

Sample: `doc_examples/ExecutingCodeExamples.java` (`testCapturingOutput`) and the `captureOutput()` helper in `doc_examples/DocExampleSupport.java`

```java
ByteArrayOutputStream bytes = new ByteArrayOutputStream();
PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8);
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
ctx.setOutStream(out);
ctx.setErrStream(out);
Object value = env.createScript("console.log(`hello ${who}`); console.error('oops'); 1", "out.js").executeWithContext(ctx);
// bytes now holds "hello world\noops\n"
```

## JSScriptExecutor: scripts and files sharing one context

`rt/JSScriptExecutor` is a small facade for REPL-style usage: it creates one global context lazily and reuses it for every `execute(code)` and `executeFile(name)` call. `executeFile()` resolves names against the `Path` root given to the constructor (an optional `Charset` defaults to the platform charset) and fails if no root was given. `clearContext()` drops the context so the next call starts from a fresh global scope.

Sample: `doc_examples/ExecutingCodeExamples.java` (`testScriptExecutorWithFiles`)

```java
Path root = Files.createTempDirectory("galta-scripts");
Files.writeString(root.resolve("defs.js"), "var base = 10; function add(n) { return base + n }");
Files.writeString(root.resolve("main.js"), "add(5)");

// JSScriptExecutor keeps one context, so files and snippets share their globals
JSScriptExecutor exec = new JSScriptExecutor(JavaScriptEnvironment.create(), root);
exec.executeFile("defs.js");
assertEquals(15, exec.executeFile("main.js"));
assertEquals(20, exec.execute("base * 2"));

exec.clearContext();   // start from a fresh global scope
try {
    exec.execute("base");
    fail();
} catch(JSRuntimeException e) {
    assertTrue(e.getMessage().contains("ReferenceError: Unknown identifier base"));
}
```

The protected factory methods `createContext()`, `createExecutor()` and `createScriptUnit()` are the extension points, for instance to install a `JSPathModuleResolver` or to run with the synchronous executor.

## Running a module unit

A unit parsed with `SCRIPT_MODULE` is initialized with `initModule(ctx, commonJS)`, which links and evaluates it and returns the `JSModule`. `getExport(name)` reads a named export, `getDefaultExport()` the default one.

Sample: `doc_examples/ExecutingCodeExamples.java` (`testRunningAModuleUnit`)

```java
JSInterpretedUnit unit = env.createScript(
        "export const version = 3; export default function greet(n) { return 'hi ' + n }",
        "lib.js", JSEnvironment.SCRIPT_MODULE);
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
JSModule module = unit.initModule(ctx, false);
assertEquals(3, module.getExport("version"));
assertEquals("hi you", ctx.value(module.getDefaultExport()).call("you").stringValue());
```

Modules that import other modules need resolvers; see [Modules](/GaltaJS/UserGuide/Modules).

## Reuse across sessions

Most of what the engine builds is worth keeping. The costs are very unevenly
distributed, so a "session" - one user, one request, one REPL - should reuse
everything above the line and rebuild only what is below it.

| Thing | Cost to build | Reuse it? |
|---|---|---|
| `JSEnvironment` | High: intrinsics, prototypes, the per-class accessor registry, every registered library | **Yes** - build once per application, keep it for the process lifetime |
| Parsed program (`unit.getProgram()`) | Parsing | **Yes** - via `scriptCacheSize`, or by holding the `JSInterpretedUnit` |
| Transpiled classes | Java source generation + compilation | **Yes** - and better, move it to build time with `js-transpiler-maven` |
| `JSGlobalContext` | Low: a globals map, an executor, streams | **One per session** - this is the isolation boundary |
| Executor | Low | Per context (it *is* the event loop) |

The big win is the environment. Creating one registers every standard object
and library from scratch; creating a global context is comparatively free. A
server that builds a fresh `JSEnvironment` per request pays that cost on every
request for no benefit.

Sample: `doc_examples/ExecutingCodeExamples.java` (`testReuseAcrossSessions`)

```java
// Built once per application: intrinsics, accessors, libraries
JSEnvironment env = JavaScriptEnvironment.create();
// Parsed once, executed in every session
JSInterpretedUnit unit = env.createScript("`hello ` + name", "greet.js");

for (String who : List.of("ada", "alan")) {
    // One context per session - this is the isolation boundary
    JSGlobalContext session = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
    session.getGlobalThis().setOwnProperty("name", who);
    unit.executeWithContext(session);          // "hello ada", then "hello alan"
}
```

A parsed program is immutable and safe to share; see [Threading](#threading).
Executing the *same* unit in two contexts concurrently is fine, executing two
scripts in the *same* context concurrently is not.

### How much is shared: intrinsics, not globals

Two global contexts built on one environment are isolated in their globals but
**share their intrinsics**, because prototypes live on the environment:

Sample: `doc_examples/ExecutingCodeExamples.java` (`testSessionsShareIntrinsicsNotGlobals`)

```java
JSGlobalContext s1 = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
JSGlobalContext s2 = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());

env.createScript("var secret = 1", "a.js").executeWithContext(s1);
env.createScript("typeof secret", "b.js").executeWithContext(s2);   // "undefined" - isolated

env.createScript("Array.prototype.mine = () => 1", "c.js").executeWithContext(s1);
env.createScript("typeof [].mine", "d.js").executeWithContext(s2);  // "function" - shared!
```

That is the trade. Sharing one environment is what makes per-session contexts
cheap, and it is the right default for code you trust. It is *not* a security
boundary: one session can monkey-patch a built-in prototype, freeze it, or
exhaust a shared cache, and every other session on that environment sees it.

For genuine isolation, give the session its own `JSEnvironment`. That is
exactly what a second realm is - `$262.createRealm()` in the test harness
builds one this way - and it means paying the environment construction cost
per session. There is no middle setting: `ConfigurationImpl` carries an
`isSharedStandardObjects` flag, but the builder exposes no setter for it, so
every `build()` returns an environment with its own intrinsics.

Each environment also has one context of its own, `env.getRealmContext()`,
created lazily. It is the realm's root - what a cross-realm `new other.Function()`
or `other.eval(...)` runs in - and is independent of any context you create.
See the [multiple realms appendix](/GaltaJS/KnownGaps) for what cross-realm
support does and does not cover.

## The current context

While code runs, the engine binds the current context to the thread. `JSContext.get()`, `JSRuntimeContext.get()` and `JSEnvironment.getEnvironment()` return it and throw `IllegalStateException` when nothing is running (`getUnchecked()` / `getEnvironmentUnchecked()` return `null` instead). The binding is an inheritable thread-local scoped by `with(...)` / `run(...)`, so Java code that calls back into the engine outside a running script (for example `Callable.call()`) must do so inside `ctx.with(() -> ...)` or `env.run(() -> ...)`. `JSValue` does this for you.

## Threading

- A `JSEnvironment` is long-lived and shared; parsed programs are shared too, so the AST is immutable and its inline caches are published safely.
- A runtime context chain belongs to one execution. Do not run two scripts concurrently in the same global context.
- Only one thread runs JavaScript in a given context at a time. Asynchronous code (coroutines, worker tasks) hands control between threads through the executor; see [Async & Event Loop](/GaltaJS/UserGuide/Async).

## Gotchas

- `SCRIPT_MODULE` is required for `import.meta`; in a classic script it is a `SyntaxError`.
- `JSScriptExecutor.executeFile()` throws `JSException("There is no root Path assigned to this executor")` when created without a root.
- `Callable.call()` outside a running script needs `ctx.with(...)`; `JSValue.call()` already wraps it.
- The completion value of a unit is the value of its last statement (statement-level completion), not the return value of a transpiled program's `executeWithContext()` (see [Execution Modes](/GaltaJS/UserGuide/ExecutionModes)).

## Source

`JSEnvironment.java` (`createScript`, `createExpression`, `evaluate`), `modules/JSScriptUnit.java`, `modules/JSInterpretedUnit.java`, `rt/JSGlobalContext.java`, `rt/interpreter/InterpretedGlobalRuntimeContext.java`, `rt/JSScriptExecutor.java`, `JSContext.java`, `rt/builtins/GlobalThis.java`
