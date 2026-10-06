# Companion Modules

The engine is the `js` artifact, but `parent-js` contains more Maven modules: packaging variants, small layers built on the engine, tooling, and the test suites. All artifacts use the group `org.monflabs.galta` and the shared `${revision}` version.

| Artifact | Purpose |
|---|---|
| `js` | The engine: parser, interpreter, transpiler, runtime, built-ins, libraries, debugger, CDP server. Depends on `json` and `javacompiler` (which pull `utilities`). Vendors Joni for regular expressions. |
| `js-all` | No sources: a shaded, single-jar distribution of `js` and its transitive dependencies (signature files and a few unneeded packages stripped). Use it when the deployment wants one jar. Not published to Maven Central: build it with `mvn package` in `galta/parent-js/js-all`, which writes `target/galtajs-all.jar`. |
| `js-template` | A tiny template layer: `TemplateEngine.compile(source)` returns a `Template` whose `execute(InterpretedGlobalRuntimeContext)` renders it. `JspTemplateEngine` handles `<% ... %>` scriptlets. |
| `js-vb` | JSF/EL-style value bindings: `ValueBindingFactory(env[, elStart, elEnd])` (default `${` / `}`) with `isValueBinding(text)`, `createValueBinding(text)`, `createExpression(text)` and `evaluate(context, text)`. A `ValueBinding` reports `isConstant()` and `evaluate(context)`. |
| `js-debugger` | Swing debugger UI (`org.monflabs.js.debugger.ui.SwingDebugger`) speaking CDP over WebSocket or in-process. See [Debugging](/GaltaJS/UserGuide/Debugging). |
| `js-library-v8` | Test-only harness that pulls Javet (V8) with the right native artifact per OS, used to cross-check GaltaJS results against V8. Not published. |
| `js-transpiler-maven` | The Maven plugin (`generate-sources` goal) that transpiles `.js` files at build time. See [Execution Modes](/GaltaJS/UserGuide/ExecutionModes). `js-transpiler-maven-tests` exercises it. |
| `parent-js-precompiled` | Aggregator of libraries transpiled at build time: `js-precompiled-beautify-js`, `-css`, `-html` (js-beautify) with thin Java facades; `js-precompiled-typescript` (the TypeScript compiler, `Typescript`). See [Precompiled libraries](#precompiled-libraries). |
| `js-playground`, `js-playground-cheerpj` | The interactive Swing playground for running snippets with the engine, the extensions and the debugger. `js-playground-cheerpj` is its CheerpJ browser build: not published to Maven Central, it runs on the documentation site - [open the playground](playground/index.html ':ignore'). Each GitHub release also attaches the desktop playground as a runnable jar. |
| `js-test-suite`, `js-test-rhino`, `js-test-test262` | Shared test infrastructure, Mozilla's Rhino ECMA suite and TC39 test262 (not published). Cross-engine benchmarks are in the separate JavascriptPerformance project. See [Testing & Compliance](/GaltaJS/Architecture/Testing). |

## js-template

Not covered by `doc_examples` (separate Maven module).

```java
JSEnvironment env = JavaScriptEnvironment.create();
TemplateEngine engine = new JspTemplateEngine(env);
Template template = engine.compile("Hello <%= name %>!");
InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createExpressionExecutor());
ctx.getGlobalThis().setOwnProperty("name", "Ada");
String text = template.execute(ctx);
```

`TemplateEngine.execute(context, source)` compiles and renders in one call. Templates are rendered synchronously, which is why the expression executor is enough.

## js-vb

Not covered by `doc_examples` (separate Maven module).

```java
JSEnvironment env = JavaScriptEnvironment.create();
ValueBindingFactory factory = new ValueBindingFactory(env);           // "${" ... "}"
InterpretedGlobalRuntimeContext ctx = new InterpretedGlobalRuntimeContext(env, env.createExpressionExecutor());
ctx.getGlobalThis().setOwnProperty("user", JSObject.of(env, "name", "Ada"));

factory.isValueBinding("Hello ${user.name}");                        // true
ValueBinding vb = factory.createValueBinding("Hello ${user.name}");  // literal text mixed with expressions
Object text = vb.evaluate(ctx);                                      // "Hello Ada"
factory.createValueBinding("plain text").isConstant();               // true: no expression inside
```

## Precompiled libraries

js-beautify (`js-precompiled-beautify-js`, `-css`, `-html`) and the TypeScript compiler (`js-precompiled-typescript`), transpiled to Java at build time: the libraries run as compiled classes, with no JavaScript parsing at run time. Each module has a facade in `org.monflabs.galtajs.precompiled`, and its transpiled library in a package of its own below it.

Not covered by `doc_examples` (separate Maven modules): the facades are exercised by the tests of each module.

```java
String formatted = BeautifyJs.newBuilder().build().execute("function f(){return 1}");
String css = BeautifyCss.newBuilder().build().execute("a{color:red}");

Typescript ts = Typescript.newBuilder().build();
String js = ts.execute("let x: number = 1;");   // ES2020 JavaScript, no module system
```

`newBuilder().environment(env)` runs the library in a given `JSEnvironment` (by default a plain JavaScript environment with the `global` alias). The library is loaded once, when the facade is built: keep the facade to format or transpile many sources. The beautifiers use js-beautify's default options. The TypeScript jar is about 14 MB.

## Source

`galta/parent-js/pom.xml` and the module directories under `galta/parent-js/`
