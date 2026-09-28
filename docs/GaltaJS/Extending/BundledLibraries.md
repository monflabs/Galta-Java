# Bundled Libraries

The `js` module ships several libraries. Only `StandardLibrary` (and, in `GaltaJSEnvironment`, `JavaLibrary`) is registered by the prebuilt environments; everything else is opt-in with `registerLibrary(new ...())`.

| Library | Package | Adds | Registered by default |
|---|---|---|---|
| `StandardLibrary` | `rt.builtins.standard` | `console`, `performance`, `JSON`, `Reflect`, `Math`, `Atomics`, `parseInt`, `parseFloat`, `isNaN`, `isFinite`, `encodeURI`, `encodeURIComponent`, `decodeURI`, `decodeURIComponent`, `eval`, plus `escape`/`unescape` when `deprecatedApis` (default true) | `JavaScriptEnvironment`, `GaltaJSEnvironment` |
| `JavaLibrary` | `library.java` | `Java` (`Java.type()`), reflection accessor for any Java class, `supportJavaNative` | `GaltaJSEnvironment` |
| `StaticLibrary` | `library` | Whatever you put in it | no |
| `CommonJSLibrary` | `library` | `require()` | no |
| `HostLibrary` | `library.platform` | `setTimeout`, `clearTimeout`, `setInterval`, `clearInterval`, `queueMicrotask`, `atob`, `btoa` | no |
| `FetchLibrary` | `library.platform` | `fetch`, `Headers`, `Request`, `Response` | no |
| `NodeLibrary` | `library.node` | modules `fs`, `node:fs`, `fs/promises`, `node:fs/promises` | no |
| `UnitTestLibrary` | `library` | `assert*`, `suite`, `test`, `fail`, resource loaders | no (test suites) |
| `RhinoShellLibrary` | `library.rhino` | `version`, `print`, `options`, `quit`, `gc`, `load` | no (Rhino test suite) |

Sample: `doc_examples/BundledLibrariesExamples.java`

## StandardLibrary

The ECMAScript globals that are not constructors. Constructors and prototypes (`Object`, `Array`, `Promise`, `Decimal`...) are installed by `JSEnvironment` itself, before any library runs; `StandardLibrary` adds the objects and functions listed above by calling `standardObjects.setOwnProperty(...)` and `setOwnMethod(...)`. It is the reference implementation of a `GlobalLibrary` (see [Libraries](/GaltaJS/Extending/Libraries)). `escape` and `unescape` are only added when `isDeprecatedApis()` is true, which `strictMode(true)` turns off and `enableGaltaJSExtensions()` turns back on.

Without it, an environment built from `JSEnvironment.newBuilder()` has no `Math`, `JSON` or `console`.

## JavaLibrary

Reflection-based access to Java: the `Java.type(className)` global, constructors, static and instance members, bean properties, overload resolution, functional-interface proxies, Java arrays, and the `$`-prefixed escape hatch on any value. Registering it sets `supportJavaNative(true)`; it always runs last so that every other accessor takes precedence. Full description in [Java Interop](/GaltaJS/UserGuide/JavaInterop).

## StaticLibrary

`addStaticGlobal(name, value)` exposes Java values as globals. See [Libraries](/GaltaJS/Extending/Libraries#global-values-staticlibrary).

## CommonJSLibrary

Adds the global `require(name)`. It imports the module through the registered resolvers and returns its default export (`module.exports` for a CommonJS module). When `name` has no file extension it is tried as-is first, then with `.js`; JSON modules are not supported (the code path is present but disabled). The resolver that serves the files must have `setCommonJS(true)`, otherwise the file is parsed as an ES module and `module` is an unknown identifier.

```java
JSFileModuleResolver resolver = new JSFileModuleResolver(root.toFile());
resolver.setCommonJS(true);
JSEnvironment env = JavaScriptEnvironment.newBuilder()
		.addModuleResolver(resolver)
		.registerLibrary(new CommonJSLibrary())
		.build();
assertEquals("hello cjs", env.evaluateScript("require('app').msg"));
```

Sample: `doc_examples/ModulesExamples.java` (`testCommonJsModules`)

## HostLibrary

The browser/Node host functions that the ECMAScript specification does not define:

| Function | Notes |
|---|---|
| `setTimeout(fn, ms, ...args)` / `clearTimeout(id)` | One-shot timer |
| `setInterval(fn, ms, ...args)` / `clearInterval(id)` | Repeating timer |
| `queueMicrotask(fn)` | Queues on the microtask queue |
| `atob(s)` / `btoa(s)` | Base64 decode / encode |

Timers are scheduled on the `JSAsyncExecutor` timed queue; the loop keeps running until every timer has fired or been cleared, so `evaluateScript` returns after the last timer. Timer state is created in `configureStandardObjects`, hence per environment. `evaluateExpression` cannot use timers (its executor rejects any asynchronous task).

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
assertEquals(List.of("aGVsbG8=", "hello"), list(env.evaluateExpression("[btoa('hello'), atob('aGVsbG8=')]")));
assertEquals("done", env.evaluateScript("await new Promise(r => setTimeout(() => r('done'), 5))"));
assertEquals(List.of("sync", "micro"), list(env.evaluateScript(
		"let log = []; queueMicrotask(() => log.push('micro')); log.push('sync'); await null; log")));
```

Sample: `doc_examples/BundledLibrariesExamples.java` (`testHostLibrary`); see also [Async](/GaltaJS/UserGuide/Async).

## FetchLibrary

`fetch(input, init)` returning a promise of `Response`, plus the `Headers`, `Request` and `Response` constructors, implemented over `java.net.http.HttpClient` (JDK 11+, one shared client). The request runs on a worker thread and the promise settles on the event loop through a microtask, so it needs the program executor (`evaluateScript`).

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new FetchLibrary()).build();
Object r = env.evaluateScript("""
	const headers = new Headers({ 'Content-Type': 'text/plain' });
	const request = new Request('https://example.com/items', { method: 'POST', headers });
	const response = new Response('body', { status: 201 });
	[typeof fetch, headers.get('content-type'), request.method, request.url, response.status]
	""");
assertEquals("[function, text/plain, POST, https://example.com/items, 201]", String.valueOf(list(r)));
```

Sample: `doc_examples/BundledLibrariesExamples.java` (`testFetchLibraryObjects`)

## NodeLibrary

Registers `NodeModuleResolver`, which serves a Node-compatible file-system module under the specifiers `fs`, `node:fs`, `fs/promises` and `node:fs/promises`.

`fs` (`NodeFsModule`) exports the synchronous functions `readFileSync`, `writeFileSync`, `appendFileSync`, `existsSync`, `statSync`, `mkdirSync`, `rmSync`, `readdirSync`, `unlinkSync`, `renameSync`, `copyFileSync`, `realpathSync`, `accessSync`, and the callback forms `readFile`, `writeFile`, `appendFile`, `exists`, `stat`, `mkdir`, `rm`, `readdir`, `unlink`, `rename`, `copyFile`, `realpath`, `access`.

`fs/promises` (`NodeFsPromisesModule`) exports the promise-returning `readFile`, `writeFile`, `appendFile`, `stat`, `access`, `realpath`, `mkdir`, `rm`, `readdir`, `unlink`, `rename`, `copyFile`.

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder()
		.registerLibrary(new NodeLibrary())   // registers the 'fs' / 'fs/promises' module resolver
		.registerLibrary(globals)             // exposes `path`
		.build();
assertEquals("hello", env.evaluateScript("import fs from 'node:fs'; fs.writeFileSync(path, 'hello'); fs.readFileSync(path, 'utf8')"));
assertEquals("hello", env.evaluateScript("import { readFile } from 'node:fs/promises'; await readFile(path, 'utf8')"));
assertEquals(true, env.evaluateScript("import { existsSync } from 'fs'; existsSync(path)"));
```

Sample: `doc_examples/BundledLibrariesExamples.java` (`testNodeFileSystemModules`)

A separate Maven module, `js-mod-node`, contains an older parallel implementation (`org.monflabs.galtajs.modules.node.NodeModuleResolver`, `FsModule`); the core `library/node` package is the one documented here.

## UnitTestLibrary

The assertion DSL used by the engine's own test suites, usable by any host that wants JavaScript-side tests: `suite`, `test`, `fail`, `assertNull`, `assertNotNull`, `assertUndefined`, `assertNotUndefined`, `assertNullOrUndefined`, `assertNotNullOrUndefined`, `assertTrue`, `assertFalse`, `assertEquals`, `assertEqualsStrict`, `assertNotEquals`, `assertNotEqualsStrict`, `assertArrayEquals`, `assertSame`, `assertNotSame`, `assertParse`, `assertParseError`, `assertDeclared`, `assertNotDeclared`, `assertDeclaredInScope`, `assertNotDeclaredInScope`, `assertThrows`, `jsonDeepClone`, `_introspect`, `SPARSE`, `EMPTY`, `_n`, `_strictMode`, `loadTextResource`, `loadJsonResource`, and a test262-shaped `assert` object (`assert.sameValue`, `assert.throws`, `assert.compareArray`...).

`assertEquals` compares structurally (arrays and objects by content). A failed assertion throws `JSRuntimeUncatchableException`: JavaScript `try/catch` cannot swallow it, the failure always reaches the Java caller.

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new UnitTestLibrary()).build();
env.evaluateScript("assertEquals([1, 2], [1, 2]); assertTrue(1 < 2); assertUndefined(void 0)");
try {
	env.evaluateScript("try { assertEquals(1, 2) } catch(e) { /* never reached */ }");
	fail();
} catch(JSRuntimeUncatchableException e) {
	assertTrue(e.getMessage().contains("Assertion error"));
}
```

Sample: `doc_examples/BundledLibrariesExamples.java` (`testUnitTestLibrary`)

## RhinoLibrary and RhinoShellLibrary

Shims for running Mozilla Rhino's ECMA test suite (`js-test-rhino`). `RhinoShellLibrary` adds the Rhino shell globals `version`, `print`, `options`, `quit`, `gc` and `load`; `RhinoLibrary` is the companion library used by that suite. Not intended for applications.

## Gotchas

- `HostLibrary`, `FetchLibrary`, `CommonJSLibrary` and `NodeLibrary` are never registered implicitly; `setTimeout` in a plain environment is `ReferenceError: Unknown identifier setTimeout`.
- `require()` returns the default export; for an ES module served by a non-CommonJS resolver that is its `export default`, not the namespace.
- `UnitTestLibrary` failures bypass `catch` blocks by design.
- `FetchLibrary` performs real network requests; the sample only constructs the objects.

## Source

`rt/builtins/standard/StandardLibrary.java`, `library/java/JavaLibrary.java`, `library/StaticLibrary.java`, `library/CommonJSLibrary.java`, `library/platform/HostLibrary.java`, `library/platform/FetchLibrary.java`, `library/node/NodeLibrary.java`, `library/node/NodeModuleResolver.java`, `library/node/fs/NodeFsModule.java`, `library/node/fs/NodeFsPromisesModule.java`, `library/UnitTestLibrary.java`, `library/rhino/RhinoShellLibrary.java`.
