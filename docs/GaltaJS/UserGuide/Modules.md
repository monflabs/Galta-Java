# Modules

GaltaJS implements ES modules (static and dynamic `import`, live bindings, top-level `await`, cycles, `import defer`, `import source`, import attributes including `bytes`) and a CommonJS bridge. Modules are located by *resolvers* registered on the environment; resolvers exist for in-memory sources, directories, NIO file systems, pre-transpiled classes and Java-implemented modules.

## Supported syntax

| Form | Notes |
|---|---|
| `import d from 'm'`, `import * as ns from 'm'`, `import { a, b as c } from 'm'`, `import d, { a } from 'm'`, `import 'm'`, `import {} from 'm'` | All static forms, including string export names and trailing commas. |
| `export`, `export default`, `export { a as b }`, `export * from`, `export * as ns from` | Live bindings, re-exports, star exports. |
| `import('m')` | Dynamic import; returns a promise of the namespace. |
| `import defer * as ns from 'm'` | The module is linked but evaluated on first access to `ns`. |
| `import source src from 'm'`, `import.source('m')` | Source-phase import: `src` is the module's *Module Source Object*, obtained without evaluating the module. Only non-JavaScript modules have one; see below. |
| `import x from 'm' with { type: 'json' }` | Import attributes; `type` may be `json` (parsed), `text` (the raw text) or `bytes` (a `Uint8Array` over an immutable `ArrayBuffer`). The environment's `importAttributedModule` decides what to do with them. |
| `import.meta` | Available in modules; an empty object (no `url`). |
| top-level `await` | Supported in modules. |
| `require('m')`, `module.exports`, `exports` | CommonJS, with `CommonJSLibrary` and a resolver in CommonJS mode. |

## Resolvers

A resolver implements `JSModuleResolver` (see [Native Modules & Resolvers](/GaltaJS/Extending/NativeModules) for the interface). The engine ships:

| Class | Loads from |
|---|---|
| `JSMemoryModuleResolver` | Strings added with `put(name, source)`. |
| `JSFileModuleResolver(File root[, Charset])` | Files under a directory. |
| `JSPathModuleResolver(Path root[, Charset])`, `(FileSystem[, Charset])` | Any NIO path or file system (zip, in-memory, ...). |
| `JSTranspiledModuleResolver(env, classLoader, basePackage)` | Classes transpiled ahead of time. |
| `NativeModuleResolver` (abstract) | Modules implemented in Java. |
| `NodeModuleResolver` (via `NodeLibrary`) | `fs`, `node:fs`, `fs/promises`. |

Resolvers are added with `builder.addModuleResolver(...)` and consulted in registration order. A bare specifier is tried as-is, then with a `.js` extension.

Sample: `doc_examples/ModulesExamples.java` (`testInMemoryModules`, `testFileModules`)

```java
JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
        .put("math", "export const PI = 3; export function twice(x) { return x * 2 } export default 'math-lib';")
        .put("app",  "import lib, { twice, PI } from 'math'; export const result = `${lib}:${twice(PI)}`;");
JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();

assertEquals("math-lib:6", env.evaluateScript("import { result } from 'app'; result"));
assertEquals(3, env.evaluateScript("import * as m from 'math'; m.PI"));
```

```java
Path root = Files.createTempDirectory("galta-modules");
Files.writeString(root.resolve("lib.js"), "export const answer = 42;");
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .addModuleResolver(new JSFileModuleResolver(root.toFile()))
        .build();
assertEquals(42, env.evaluateScript("import { answer } from './lib.js'; answer"));
assertEquals(42, env.evaluateScript("import { answer } from 'lib'; answer"));   // '.js' is implied
```

## Dynamic and deferred imports

Sample: `doc_examples/ModulesExamples.java` (`testDynamicImport`, `testDeferredImport`)

```java
assertEquals("loaded", env.evaluateScript("const m = await import('lazy'); m.v"));
```

```js
import defer * as ns from 'heavy';
const before = globalThis.heavyLoaded;   // module not evaluated yet
const v = ns.v;                          // first access evaluates it
[before, globalThis.heavyLoaded, v]
// -> [ undefined, true, 1 ]
```

## Source-phase and bytes imports

Sample: `doc_examples/ModulesExamples.java` (`testSourcePhaseAndBytesImports`)

`import source src from 'm'` (and its dynamic form `import.source('m')`) binds the module's *Module Source Object* instead of its namespace: an object whose prototype chain ends in `%AbstractModuleSource%.prototype`, with a `source` getter holding the module's text. The module is never loaded, linked or evaluated. As the proposal specifies, a JavaScript module has no source-phase representation, so importing one this way is a `SyntaxError`; the other module kinds do have one:

| Module kind | `src.source` |
|---|---|
| Native module (Java-implemented) | `// native module: <name>` |
| Pre-transpiled class (`JSTranspiledModuleResolver`) | The retained source text, or the native placeholder when the class carries none |
| JavaScript source (memory, file, transpiled on the fly) | none - `SyntaxError` |

```js
import source src from 'host:config';
src.source                               // -> "// native module: host:config\n"
Object.prototype.toString.call(src)      // -> "[object ModuleSource]"
try { await import.source('./lib.js'); } catch(e) { e.name }   // -> "SyntaxError"
```

`with { type: 'bytes' }` imports a module's raw bytes as a `Uint8Array` over an immutable `ArrayBuffer` (`buffer.immutable` is `true`; `resize`/`transfer` throw). A file-backed resolver returns the file as-is, binary files included; other resolvers return the UTF-8 encoding of the module text.

```js
import data from './logo.bin' with { type: 'bytes' };
[data.length, Array.from(data), data.buffer.immutable]   // -> [3, [1, 2, 255], true]
```

A resolver controls both through two `JSModuleDescriptor` hooks, `getModuleSourceText()` and `getBytes()` - see [Native Modules & Resolvers](/GaltaJS/Extending/NativeModules).

## Top-level await

A module may `await` at its top level; importers wait for it. One caveat: the *root* of an evaluation must itself be a module for the asynchronous linking to apply. A classic script (what `evaluateScript()` runs) does not wait for a statically imported async module and reads its bindings too early. Either import the module dynamically or make the root a module unit.

Sample: `doc_examples/ModulesExamples.java` (`testTopLevelAwaitInModules`)

```java
JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
        .put("config", "export const settings = await Promise.resolve({ mode: 'test' });");
JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
// A classic script does not wait for an async module: import it dynamically...
assertEquals("test", env.evaluateScript("const m = await import('config'); m.settings.mode"));
// ...or make the root a module, which links asynchronously like any other module
JSInterpretedUnit root = env.createScript("import { settings } from 'config'; export const mode = settings.mode;", "root.js", JSEnvironment.SCRIPT_MODULE);
JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
assertEquals("test", root.initModule(ctx, false).getExport("mode"));
```

## CommonJS

`CommonJSLibrary` adds the global `require(name)`, which loads the module through the resolvers and returns its default export (retrying with a `.js` extension when the name has none). Sources are parsed as CommonJS when their resolver is in CommonJS mode (`AbstractModuleResolver.setCommonJS(true)`), which gives them `module`, `exports` and `require`. A CommonJS module can also be imported with `import`, its `module.exports` being the default export.

Sample: `doc_examples/ModulesExamples.java` (`testCommonJsModules`)

```java
Files.writeString(root.resolve("greeter.js"), "module.exports = { hello: (n) => 'hello ' + n };");
Files.writeString(root.resolve("app.js"), "const g = require('greeter'); exports.msg = g.hello('cjs');");

JSFileModuleResolver resolver = new JSFileModuleResolver(root.toFile());
resolver.setCommonJS(true);   // files under this resolver are CommonJS modules
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .addModuleResolver(resolver)
        .registerLibrary(new CommonJSLibrary())   // provides the global require()
        .build();
assertEquals("hello cjs", env.evaluateScript("require('app').msg"));
// A CommonJS module can also be imported as an ES module
assertEquals("hello esm", env.evaluateScript("import g from 'greeter'; g.hello('esm')"));
```

`require()` does not load JSON files.

## Modules implemented in Java

A `NativeModuleResolver` returns `JSNativeModule` instances built from Java values: a default export and a `JSObject` of named exports. See [Native Modules & Resolvers](/GaltaJS/Extending/NativeModules) for the full pattern; the short form:

Sample: `doc_examples/ModulesExamples.java` (`testNativeModuleImplementedInJava`)

```java
NativeModuleResolver resolver = new NativeModuleResolver() {
    @Override
    protected JSModuleDescriptor findModule(String name) {
        if(!name.equals("host:config")) {
            return null;
        }
        return new NativeModuleDescriptor(name) {
            @Override
            public JSModule loadModule(JSGlobalContext context) {
                JSEnvironment env = context.getEnvironment();
                return new JSNativeModule(env, this, "config-v1", JSObject.of(env, "host", "galta", "version", 7));
            }
        };
    }
    @Override
    public Stream<JSModuleDescriptor> getModules() {
        return Stream.empty();
    }
};
JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(resolver).build();
assertEquals("galta/7/config-v1", env.evaluateScript("import d, { host, version } from 'host:config'; `${host}/${version}/${d}`"));
```

## Running modules from Java

`env.createScript(text, name, JSEnvironment.SCRIPT_MODULE)` parses a module; `unit.initModule(ctx, false)` links and evaluates it and returns the `JSModule` (`getExport(name)`, `getDefaultExport()`, `getModuleNamespaceObject()`). `ctx.importModule(unitContext, name)` on a `JSGlobalContext` imports by name through the resolvers. Modules are cached per global context.

## Linking

Before the first module of a graph runs, every module it imports (directly or through other modules) is parsed and every imported or re-exported name is resolved, as the specification's link step requires (`modules/StaticModuleLinker`). A dependency with a syntax error, a name no module exports (`export *` never re-exports `default`), an ambiguous name (two `export *` providing different bindings) or a circular re-export is a `SyntaxError`, and none of the graph runs. Native, CommonJS and JSON modules are not inspected; a module no resolver finds is reported when the graph is evaluated. In transpiled mode the graph is linked when the root module is compiled.

## Gotchas

- `import`/`export` declarations are also accepted at the top level of a classic script (evaluated with `evaluateScript()`, for example): a GaltaJS extension, `supportImportExportInScripts`, on by default. Build the environment with `.supportImportExportInScripts(false)` to make them a `SyntaxError` outside modules, as in the specification.
- `import.meta` is an empty object; there is no `url`. Using it in a classic script is a `SyntaxError`.
- A classic-script root does not wait for a statically imported top-level-await module; use `await import()` or a module root.
- CommonJS sources must come from a resolver with `setCommonJS(true)`; otherwise `module` is an unknown identifier.
- `require()` needs `CommonJSLibrary`.
- `import source` of a JavaScript module is a `SyntaxError` by design (the proposal reserves source-phase imports for non-source-text modules such as WebAssembly); in GaltaJS that means native and pre-transpiled modules.
- With a transpiled module resolver, a module that imports itself (directly or through a cycle) during its own evaluation is a known gap; see [Known ECMAScript Gaps](/GaltaJS/KnownGaps).

## Source

`JSModuleResolver.java`, `modules/StaticModuleLinker.java`, `modules/AbstractModuleResolver.java`, `modules/JSSourceModuleResolver.java`, `modules/JSMemoryModuleResolver.java`, `modules/JSFileModuleResolver.java`, `modules/JSPathModuleResolver.java`, `modules/JSTranspiledModuleResolver.java`, `modules/NativeModuleResolver.java`, `modules/JSNativeModule.java`, `modules/JSInterpretedUnit.java`, `JSModule.java`, `library/CommonJSLibrary.java`
