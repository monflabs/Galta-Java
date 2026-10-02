# Execution Modes

GaltaJS runs the same JavaScript in three ways: interpreted (walk the AST), interpreted with the AST optimizer, or transpiled to Java source that is compiled by a Java compiler and loaded as classes. All three share the runtime (`RuntimeUtil`, the built-ins, the executors), so a script behaves the same in every mode; the engine's own test suite runs each test in all modes to keep it that way.

## Interpreted

The default. `createScript()` parses the text into an AST (`ASTProgram`), and executing the unit evaluates the nodes directly. There is no intermediate representation. Start-up is immediate, which makes it the right mode for scripts that change often, templates, expressions and tooling. Internals: [Interpreter](/GaltaJS/Architecture/Interpreter).

## Interpreted, optimized

The `ScriptOptimizer` configured on the environment runs after parsing. `ScriptOptimizer.defaultOptimizer()` (the builder default) applies two passes: constant folding with unreachable-code removal, and a scope-resolution pass that annotates identifiers with their frame distance and slot so that the interpreter can skip the name lookup. `ScriptOptimizer.emptyOptimizer()` disables both; `ScriptOptimizer.newBuilder().optimizers(ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES).build()` is the explicit form of the default.

The decompiler (`ASTProgram.decompile()`) prints an AST back as JavaScript, which is a convenient way to see what the optimizer did.

Sample: `doc_examples/TranspilerExamples.java` (`testOptimizedInterpretedMode`)

```java
String script = "function f(a) { if (true) { return a * 2 } return 0 }\nconst x = 2 * 3 * 7; f(x)";

JSEnvironment plain = JavaScriptEnvironment.newBuilder().scriptOptimizer(ScriptOptimizer.emptyOptimizer()).build();
JSEnvironment optimized = JavaScriptEnvironment.newBuilder()
        .scriptOptimizer(ScriptOptimizer.newBuilder().optimizers(ScriptOptimizer.DEFAULT_NODE_OPTIMIZER_NODES).build())
        .build();
assertEquals(84, plain.createScript(script, "o.js").execute());
assertEquals(84, optimized.createScript(script, "o.js").execute());

// The decompiler prints the AST back as JavaScript, which shows what the optimizer did
String decompiled = optimized.createScript(script, "o.js").getProgram().decompile();
assertTrue(decompiled.contains("const x=42"));       // constant folding
assertFalse(decompiled.contains("return 0"));         // unreachable code removed
```

Internals: [Optimizer](/GaltaJS/Architecture/Optimizer).

## Transpiled

The transpiler (`transpiler/JSTranspiler`) converts the AST into Java source: one Java class per script or module, extending `JSTranspiledUnit`, with every JavaScript function becoming a local class. The generated source is compiled with a standard Java compiler and executed against a `TranspiledGlobalRuntimeContext`. Use it when:

- the JavaScript is a fixed library shipped with the application (no parsing at start-up, faster execution, ordinary Java stack traces and profiling), or
- the script is large or hot enough that the interpreter's overhead matters.

### Why a transpiler rather than a bytecode compiler

- Generating Java source is simpler than generating bytecode, and the output is readable.
- The generated code can be stepped through with a Java debugger and profiled with Java tools.
- The Java compiler applies its own optimizations.

The costs: a Java compiler (the JDK's `javax.tools` compiler, through the `javacompiler` module) is needed at transpile time, and the output must be valid Java. Unreachable code has to be removed, variables get unique generated names (`p_N[i]`), and class-file limits (64K per method, constant pool size) force the transpiler to split large programs into several classes; see [Transpiler](/GaltaJS/Architecture/Transpiler).

### Programmatic transpilation

`new JSTranspiler(env, options).compileResult(className, resultClass, program, moduleName)` returns a `Result` with `getJavaCode()`, `getJavaScriptCode()` and `getTranspilerMap()`. `moduleName` is `null` for a classic script.

Sample: `doc_examples/TranspilerExamples.java` (`testTranspileToJavaSource`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
JSInterpretedUnit unit = env.createScript(FIB, "fib.js");

JSTranspiler transpiler = new JSTranspiler(env, JSTranspilerOptions.newBuilder().build());
JSTranspiler.Result result = transpiler.compileResult("Fib", "Object", unit.getProgram(), null);
String java = result.getJavaCode();

// One Java class per unit; each JS function becomes a local class inside it
assertTrue(java.contains("class Fib extends JSTranspiledUnit"));
assertTrue(java.contains("extends BuiltinFunctionTranspiler"));
assertTrue(java.contains("// line: 1"));   // source positions travel as comments
```

`JSTranspilerOptions.newBuilder()` options: `sourceMap`, `sourceCode`, `sourceInCode`, `sourceInComments` / `maxSourceInComments`, `commonJS`, `splitCode`, `debugInformation`, `mustDeclareVariables`, `specializeLoopCounterMath` (experimental), `debuggable` (experimental, see [Debugging](/GaltaJS/UserGuide/Debugging)).

### Compiling and running in memory

The `org.monflabs.galta:javacompiler` module (a transitive dependency of `js`) compiles from a `SourceFactory` into a `TargetFactory`; `FactoryClassLoader` loads the classes from the target. The generated class has a `(JSEnvironment, String moduleName)` constructor.

Sample: `doc_examples/TranspilerExamples.java` (`testCompileAndRunTranspiledCode`)

```java
String java = new JSTranspiler(env, JSTranspilerOptions.newBuilder().build())
        .compileResult("Fib", "Object", env.createScript(FIB, "fib.js").getProgram(), null)
        .getJavaCode();

// Compile in memory with the javacompiler module...
MapTargetFactory classes = new MapTargetFactory();
try(JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(env.getClassLoader())
        .sourceFactory(MapSourceFactory.of("Fib.java", java))
        .targetFactory(classes)
        .build()) {
    compiler.compile("Fib");
}

// ...load it, and run it against a transpiled global context
FactoryClassLoader loader = new FactoryClassLoader(env.getClassLoader(), classes);
JSTranspiledUnit unit = (JSTranspiledUnit)loader.loadClass("Fib")
        .getConstructor(JSEnvironment.class, String.class)
        .newInstance(env, null);
TranspiledGlobalRuntimeContext ctx = new TranspiledGlobalRuntimeContext(env, env.createProgramExecutor());
unit.executeWithContext(ctx);
assertEquals(55, ctx.global("fib").call(10).intValue());
```

Note that `executeWithContext()` on a transpiled program does not return the completion value of the last statement; read results through globals or exports.

### Transpiled modules through a resolver

Any `JSSourceModuleResolver` (memory, file, path) can transpile the modules it loads instead of interpreting them: call `initTranspiler(options, classLoader, targetFactory)` on it. Modules are transpiled, compiled and loaded the first time they are imported, then reused. `JSTranspiledModuleResolver(env, classLoader, basePackage)` loads modules that were transpiled ahead of time (for instance by the Maven plugin).

Sample: `doc_examples/TranspilerExamples.java` (`testTranspiledModulesThroughAResolver`)

```java
JSMemoryModuleResolver modules = new JSMemoryModuleResolver()
        .put("calc", "export function add(a, b) { return a + b }");
// With initTranspiler(), the resolver transpiles and compiles modules on the fly
modules.initTranspiler(JSTranspilerOptions.newBuilder().build(), JSEnvironment.class.getClassLoader(), new MapTargetFactory());

JSEnvironment env = JavaScriptEnvironment.newBuilder().addModuleResolver(modules).build();
assertEquals(5, env.evaluateScript("import { add } from 'calc'; add(2, 3)"));
```

### Maven plugin

`org.monflabs.galta:js-transpiler-maven` provides the `generate-sources` goal (bound to the `generate-sources` phase). It transpiles every matching `.js` file into Java sources under the output directory; pair it with `build-helper-maven-plugin:add-source` if the output directory is not picked up automatically.

```xml
<plugin>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js-transpiler-maven</artifactId>
  <version>0.8.0</version>
  <executions>
    <execution>
      <id>generate</id>
      <goals><goal>generate-sources</goal></goals>
    </execution>
  </executions>
  <configuration>
    <sourceDirectory>${basedir}/js</sourceDirectory>
    <includes><include>**/*.js</include></includes>
  </configuration>
</plugin>
```

| Parameter | Default | Purpose |
|---|---|---|
| `sourceDirectory` | `${basedir}/js` | Root of the JavaScript sources. |
| `outputDirectory` | `${project.build.directory}/generated-sources/js` | Where the Java sources are written. |
| `jsPackage` | `js` | Java package of the generated classes. |
| `includes` / `excludes` | `**/*.js` / none | Ant-style file filters. |
| `sourceMap` | `false` | Embed a GaltaJS line map (`JSTranspilerMap`) in the class. |
| `sourceCode` | `false` | Embed the original JavaScript in the class. |
| `mapFile` | `true` | Also write the map to a file. |
| `sourceFile` | `true` | Also copy the JavaScript source next to the output. |
| `sourceInComments` / `maxSourceInComments` | `false` / `64` | Emit the source as a numbered comment header. |
| `splitCode` | `false` | Split large functions and literals to stay under class-file limits. |
| `commonJS` | `false` | Treat the sources as CommonJS modules. |
| `encoding` | `${project.build.sourceEncoding}` (UTF-8) | Source encoding. |
| `failOnError` | `true` | Fail the build on a transpilation error. |
| `followSymlinks` | `true` | Follow symbolic links when scanning. |
| `galtaJs` | `false` | Enable the GaltaJS extensions while parsing. |
| `verbose` | `false` | Log every file. |

The `parent-js-precompiled` modules ship third-party libraries transpiled at build time: `js-precompiled-beautify-js`, `-css` and `-html` are generated and usable; `js-precompiled-typescript` exists but its plugin execution and Java facade are disabled in the current tree. These modules are not published to Maven Central; build them from this repository.

## Decompiled mode

Not an execution mode as such, but the fourth pass of the engine's test suite: the program is decompiled with `ASTProgram.decompile()`, parsed again, and interpreted. It verifies that the AST-to-source printer round-trips, which matters for tooling that rewrites scripts.

## Choosing a mode

| Situation | Mode |
|---|---|
| Expressions, templates, user scripts, anything that changes at runtime | Interpreted (default optimizer) |
| Bundled JavaScript libraries, hot loops, long-running services | Transpiled at build time with the Maven plugin |
| Modules loaded at runtime that are large and reused | Resolver with `initTranspiler()` |

## Gotchas

- Transpilation requires a JDK compiler at transpile time (`javax.tools`), not only a JRE.
- `executeWithContext()` on a transpiled program returns no completion value; use globals or exports.
- Generated names such as `p_0[1]/*name*/` and `F1` are stable per build but not across builds; do not rely on them from Java.
- The source map is GaltaJS's own line-map format, not a Source Map v3 file.

## Source

`transpiler/JSTranspiler.java`, `transpiler/JSTranspilerOptions.java`, `rt/transpiler/JSTranspiledUnit.java`, `rt/transpiler/TranspiledGlobalRuntimeContext.java`, `modules/JSSourceModuleResolver.java`, `modules/JSTranspiledModuleResolver.java`, `optimizer/ScriptOptimizer.java`, `node/ASTProgram.java` (`decompile`), `galta/parent-js/js-transpiler-maven/src/main/java/org/monflabs/galtajs/maven/JSTranspilerMojo.java`
