# Architecture

This category describes how the GaltaJS engine works internally: how source text becomes an executable program, how the two execution modes (interpreter and transpiler) run it, how asynchronous code, modules and objects are implemented, and how the engine is tested. It is written for engine developers; usage is covered by the [User's Guide](/GaltaJS/UserGuide/).

## The pipeline

```
   source text
       |
       |  (optional, host-invoked) ScriptPreProcessor.preprocess(source, symbols)
       v
 +-------------+     +------------------+     +----------------------+
 |  JSParser   | --> |  ASTProgram      | --> |  ScriptOptimizer     |
 |  (JavaCC)   |     |  __init(): early |     |  constant folding,   |
 |             |     |  errors, scoping |     |  dead code, scope    |
 +-------------+     +------------------+     |  resolution metadata |
                                              +----------+-----------+
                                                         |
                            +----------------------------+----------------------------+
                            |                                                         |
                            v                                                         v
               +------------------------+                               +---------------------------+
               |  Interpreter           |                               |  JSTranspiler             |
               |  ASTNode.evaluate()    |                               |  AST -> Java source       |
               |  InterpretedGlobal-    |                               |  javacompiler (in memory) |
               |  RuntimeContext        |                               |  FactoryClassLoader       |
               +------------------------+                               |  JSTranspiledUnit         |
                                                                        +---------------------------+
```

Both branches end in the same runtime: `RuntimeUtil` statics, the accessor registry, the built-ins under `rt/builtins/`, and an executor (`JSExpressionExecutor` or `JSAsyncExecutor`) that owns the event loop.

## Package map

All paths are relative to `galta/parent-js/js/src/main/java/org/monflabs/galtajs/`.

| Package | Role |
|---|---|
| `parser/` | Hand-maintained parser support classes (`TokenMgrError`, `ParserContextImpl`, `JSParserContext`). The grammar itself is `src/main/javacc/.../parser/JSParser.jj`; the generated parser lands in `target/generated-sources/javacc/`. |
| `node/` | The AST: `ASTNode`, `ASTProgram`, `ASTVarContainer` and the node families (`control/`, `clazz/`, `binaryop/`, `unaryop/`, `assignop/`, `ternaryop/`, `call/`, `literal/`, `variable/`, `debug/`). |
| `optimizer/` | AST-level passes: `ScriptOptimizer`, `ConstantFoldingAndUnreachableCodeOptimizer`, `ScopeResolutionOptimizer`. |
| `rt/` | Runtime contracts and helpers: `JSRuntimeContext`, `JSGlobalContext`, `JSResult`, `RuntimeUtil`, `JSRuntimeException`, `JSScriptExecutor`. |
| `rt/interpreter/` | Interpreted runtime contexts (`InterpretedGlobalRuntimeContext`, function/block/with contexts) and `VariableMap`. |
| `rt/transpiler/` | Transpiled runtime: `JSTranspiledUnit`, `TranspiledGlobalRuntimeContext`, `JSVar`, `VarAccessor`, `JSTranspilerMap`. |
| `rt/executors/` | Event loop: `JSExecutor`, `JSAsyncExecutor`, `JSExpressionExecutor`, tasks. |
| `rt/builtins/` | Accessors, property descriptors, callable base classes, and every ECMAScript built-in under `primitives/`, `errors/`, `standard/`. |
| `transpiler/` | `JSTranspiler`, `JSTranspilerOptions`, generator contexts and `context/TranspilerCodeSplitter`. |
| `modules/` | Module units, descriptors and resolvers; `JSInterpretedUnit` holds the linking algorithm. |
| `library/` | Libraries contributed to an environment (`StandardLibrary` lives in `rt/builtins/standard/`; `JavaLibrary`, `HostLibrary`, `NodeLibrary`... here). |
| `jsonfactory/` | `JSObject`/`JSArray` and their implementations on top of the Galta JSON library; `JSValue`. |
| `preprocessor/` | `ScriptPreProcessor`. |
| `debug/` | Engine-neutral debugger API (`debug/api`) and its implementation (`debug/api/impl`). |
| `cdp/` | Chrome DevTools Protocol server (WebSocket and in-process transports). |
| `external/` | Vendored third-party code: `org_joni` (regexp), `ch_obermuhlner_math_big` (BigDecimal math), `org_mozilla_javascript` (number-to-string). |
| `types/` | `JSType`, a static type lattice used by the transpiler. |
| `util/` | Small helpers (`JavaBuilder`, string utilities, weak identity maps under `rt/util/`). |

## Design decisions

- **No wrapper values.** A JavaScript number is a `java.lang.Integer`/`Double`/`Long`, a string is a `java.lang.String`, an object is a `JSObject`. JavaScript semantics come from a per-class `JSAccessor` looked up in `JSEnvironment.getAccessor()`. See [Object model](/GaltaJS/Architecture/ObjectModel).
- **The AST is the program.** The interpreter walks `ASTNode.evaluate()` directly; there is no bytecode or intermediate representation. Optimizations are AST rewrites plus metadata. See [Interpreter](/GaltaJS/Architecture/Interpreter) and [Optimizer](/GaltaJS/Architecture/Optimizer).
- **Transpile to Java source, not bytecode.** The transpiler emits readable Java that `javac` compiles (in memory or at build time), so the JIT, the Java debugger and the Java tool chain apply. See [Transpiler](/GaltaJS/Architecture/Transpiler).
- **Real threads as coroutines.** Generators and `async` functions run on (virtual) threads handed control through `Exchanger` rendezvous; JavaScript semantics stay single-threaded because only one side runs at a time. See [Async runtime](/GaltaJS/Architecture/AsyncRuntime).
- **JSON objects are JavaScript objects.** `JSObjectImpl` implements the JSON library's `JsonObject`, so JSON data flows between Java and JavaScript without conversion. See [Object model](/GaltaJS/Architecture/ObjectModel).

## Pages

- [Parser and AST](/GaltaJS/Architecture/Pipeline) - grammar, early-error pass, scoping model, caches, decompiler.
- [Optimizer](/GaltaJS/Architecture/Optimizer) - constant folding, unreachable code, scope resolution metadata.
- [Interpreter](/GaltaJS/Architecture/Interpreter) - runtime contexts, completion signals, inline caches, `with` and `eval`.
- [Transpiler](/GaltaJS/Architecture/Transpiler) - generated code layout, code splitting, compilation, source maps, transpiled modules.
- [Async runtime](/GaltaJS/Architecture/AsyncRuntime) - executors, event loop, coroutines, generators, promises, timers.
- [Modules runtime](/GaltaJS/Architecture/ModulesRuntime) - units, resolvers, linking, top-level await, `import defer`.
- [Object model](/GaltaJS/Architecture/ObjectModel) - accessors, property storage, arrays, primitives vs wrappers, built-in representations.
- [Java interop](/GaltaJS/Architecture/JavaInterop) - Java member lookup, overload resolution, argument conversion, function proxies and their caches.
- [RegExp engines](/GaltaJS/Architecture/RegExpEngines) - the pluggable engine interface, the JDK translation, the Joni port.
- [Debugger](/GaltaJS/Architecture/Debugger) - debug hooks, the debugger API, the CDP server.
- [Testing and compliance](/GaltaJS/Architecture/Testing) - test suites, test262, Rhino, benchmarks, documentation samples.
- [Internal notes and history](/GaltaJS/Architecture/Notes) - historical design briefs and session records.
- [Known ECMAScript gaps](/GaltaJS/KnownGaps) - the living list of deliberate deviations.
