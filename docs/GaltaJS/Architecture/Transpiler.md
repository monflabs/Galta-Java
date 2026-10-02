# Transpiler

`transpiler/JSTranspiler` converts an `ASTProgram` into one Java compilation unit, which `javac` compiles (in memory through the `javacompiler` module, or at build time through the Maven plugin) into a class extending `rt/transpiler/JSTranspiledUnit`. The generated code calls the same runtime (`RuntimeUtil`, accessors, built-ins) as the interpreter, so both modes share semantics; the transpiler only replaces the AST walk with straight-line Java.

## Entry point

```java
JSTranspiler transpiler = new JSTranspiler(env, JSTranspilerOptions.newBuilder().build());
JSTranspiler.Result r = transpiler.compileResult(fullClassName, resultClass, program, moduleName);
r.getJavaCode();        // the Java source
r.getJavaScriptCode();  // the original source, when requested
r.getTranspilerMap();   // JSTranspilerMap, when sourceMap(true)
```

`JSTranspiler.moduleNameToJavaClassName(basePackage, moduleName)` derives class names; resolvers use the base package `js` (`JSSourceModuleResolver.JS_PACKAGE_NAME`).

The mapping drops an optional trailing `.js`, turns path separators into package separators and capitalizes the first letter of the class (`a/b/calc.js` -> `a.b.Calc`); it is otherwise injective (case preserved, `_` escapes: `beautify-html.js` -> `Beautify_dhtml`, `Ab` -> `_Ab`, `1a` -> `_1a`), so distinct modules never share a class.

## Generated class layout

Verified shape (from the probe in this session and `parent-js-precompiled/js-precompiled-beautify-js/target/generated-sources/js/js/Beautify.java`):

```java
// package XXX;                                   <- JSTranspiler.PACKAGE_COMMENT, replaced by the caller
import static org.monflabs.galtajs.rt.RuntimeUtil.*;   (plus java.util, jsonfactory, rt, rt.transpiler...)

public class Fib extends JSTranspiledUnit {
  public Fib(JSEnvironment env) { this(env, JSModule.DEFAULT_PROGRAM_NAME); }
  public Fib(JSEnvironment env, String moduleName) { super(env, new Descriptor(moduleName, Fib.class)); }

  @Override
  protected void _runValue(JSTranspiledRuntimeContext _ctx) {
    final var tmp = new TempVar();
    final var _this = _ctx.getThis();
    final Object[] p_0 = new Object[1];                 // one scope array per ASTVarContainer
    JSVar.initVars(p_0, 0);
    _ctx.initGlobalVariables(p_0, CST_0/*var names*/, CST_1/*var types*/, CST_2/*annexB*/);
    class F1 extends BuiltinFunctionTranspiler {        // one LOCAL class per JS function
      F1(JSTranspiledRuntimeContext parentCtx, int flags, String name, int length, int index, int srcStart, int srcEnd) { super(...); }
      @Override protected boolean isContextElidable() { return true; }
      // fib, F1.f_0
      ...callVoid1(ctx, _this, p_arg_0)...              // arity fast path
    }
    ...
  }
}
```

- Fixed names are static fields of `JSTranspiler`: `MAIN_FUNCTION_IMPL = "_runValue"`, `MAIN_CONTEXT = "_ctx"`, `FUNCTION_ARGUMENTS = "_args"`, `THIS_VAR = "_this"`, `EXCEPTION_VAR = "_ex"`, `CURRENT_VALUE = "_value"` (the JSON-path `@`), `TEMP_VAR = "tmp.v"`, `PACKAGE_COMMENT = "// package XXX;"`.
- **Scopes** are `final Object[] p_N` arrays (`generateUniqueId("p_")`); a variable is `p_N[i]/*name*/` (`VariableDef.getJavaVariableValue()`). `JSVar.initVars(p_N, doNotInitialize)` fills them, then `p_N[i] = TDZ;` seeds `let`/`const`/`using` slots.
- **Functions** are Java *local* classes (`F0`, `F2`, `F4_f0`...) extending `BuiltinFunctionTranspiler`, nested lexically in the enclosing method body. Closures work because a Java local class captures the effectively-final `p_N` locals of its enclosing methods, so nested functions read the outer scope's array directly.
- **Arity fast paths**: `callVoid0/1/2(ctx, _this, p_arg_0, ...)` with a bridging `callVoid(ctx, _this, Object[] _args)` that unpacks with `initArg(_args, i)`. `isContextElidable()` is emitted for functions that never touch `this`, `new.target`, the yielder or the variable map of their context.
- **Statements** are preceded by `// line: N, col C` plus the source line, and are expressed as `RuntimeUtil` static calls: `memberGet`, `assign`, `invokeFunction(_ctx, ...)`, `invokeMethod(_ctx, target, name, Object[])`, `eqStrict`, `andTest`, `typeof`, `getIdentifierValue(_ctx, name, throwError)`, `createObject().litProp(...)`, `createArray()`, `checkTDZ`, `deref`, `asResult`. Coercion wrappers come from `JSTranspiler.asValue/asBoolean/asBooleanCondition/asNumber/asString/asInt32/asCallable/asFunction`.
- **Constant pool**: `TranspilerGeneratorConstantPoolContext` collects literals emitted as `private static final` fields `CST_n`.
- Options baked in as overrides: `isForceStrictMode()`, `isCommonJS()`, `isAsyncExecution()`, `isModuleUnit()`.
- A program's completion value is not returned by `executeWithContext()`; read results through globals (`ctx.global(...)`) or exports.

## Code splitting

Java limits a method body and a class constant pool to 64K, which the IIFE builds of TypeScript or the Octane benchmarks exceed. `transpiler/context/TranspilerCodeSplitter` decides where to split:

| Constant | Value |
|---|---|
| `DEFAULT_BLOCK_SPLIT_MIN` / `DEFAULT_BLOCK_SPLIT_MAX` | 20 / 150 statements |
| `DEFAULT_OBJECT_LITERAL_SPLIT_THRESHOLD` / `DEFAULT_OBJECT_SPLIT_MAX` | 5 / 150 properties |
| `DEFAULT_ARRAY_LITERAL_SPLIT_THRESHOLD` / `DEFAULT_ARRAY_SPLIT_MAX` | 5 / 150 items |
| `DEFAULT_MAX_FUNCTIONS_BEFORE_DISPATCHER` / `DEFAULT_MAX_FUNCTIONS_PER_DISPATCHER` | 50 / 500 functions |

Four kinds of split exist:

1. **Statement blocks** (`shouldSplitBlock`, `isNodeBlockSplitable`: assignments, calls, function and variable declarations) through `TranspilerGeneratorBlockSplitContext`.
2. **Object and array literals** above the thresholds.
3. **Function classes**: up to 50 functions per container get one class each with the direct-argument fast path; beyond that, a chain of dispatcher classes (`Name_0 extends BuiltinFunctionTranspiler`, `Name_1 extends Name_0`, ...) switches on `index` and loses the fast path (`ASTVarContainer.transpilerDeclareFunctionClasses`).
4. **Large `String[]` constants**, split into `X_c0()`, `X_c1()`... static methods merged by `JSTranspiledUnit.mergeStringArrays(...)`. This one is unconditional (not gated by `splitCode`) because it keeps `<clinit>` under the limit.

## Compilation and loading

`modules/JSSourceModuleResolver.loadTranspiledModule()` is the reference flow:

```java
JavaCompilerFactory.newBuilder().classLoader(classLoader)
    .sourceFactory(MapSourceFactory.of(javaFileName, javaCode))
    .targetFactory(targetFactory).failOnWarnings(true).build().compile(className);
clazz = classLoader.loadClass(className);                     // FactoryClassLoader
ctor  = clazz.getConstructor(JSEnvironment.class, String.class);
```

The `org.monflabs.galta:javacompiler` module (`parent-utilities/javacompiler`) provides `JavaCompilerFactory`, `JavaCompilerJavac`, `MapSourceFactory`/`MapTargetFactory` (in-memory) and `FactoryClassLoader`; `PathFileFactory` (sources and classes in any `java.nio` filesystem) and the utilities' `PathClassLoader` are the file-system variants used by the test harness. `TranspiledGlobalRuntimeContext` is the transpiled counterpart of `InterpretedGlobalRuntimeContext` and implements `Debuggable`. Supporting runtime classes: `JSTranspiledRuntimeContext`, `TranspiledUnitRuntimeContext`, `TranspiledModuleRuntimeContext`, `TranspiledFunctionRuntimeContext`, `TranspiledFieldInitializerRuntimeContext`, `TranspiledClassPrivateScopeRuntimeContext`, `TranspiledBridgeContext` (an interpreted context bridging into transpiled code), `JSVar`/`JSVarRef`, `HeadClosureSnapshotHolder`, `IteratorRecord`.

## Options

`JSTranspilerOptions.newBuilder()`:

| Option | Meaning |
|---|---|
| `sourceMap(boolean)` | Emit a nested `TranspilerMap` class holding `JSTranspilerMap.deserialize("...")` and override `getTranspilerMap()`. |
| `sourceCode(boolean)` | Embed the original JavaScript (`SourceCode.CODE`). |
| `sourceInCode(boolean)` | Emit each source line as a comment before its statement. |
| `sourceInComments(boolean)` / `maxSourceInComments(int)` | Numbered source header (default `DEFAULT_SOURCE_INCOMMENTS = 64` lines). |
| `commonJS(boolean)` | Treat the unit as CommonJS. |
| `splitCode(boolean)` | Enable the splitting described above (string constants are always split). |
| `debugInformation(boolean)` | Extra debug metadata. |
| `mustDeclareVariables(boolean)` | Mirror of the environment flag. |
| `specializeLoopCounterMath(boolean)` | Experimental, off by default. |
| `debuggable(boolean)` | Experimental, off by default: emit `JSTranspiledUnit.debugStatement()` at each top-level statement so the [debugger](/GaltaJS/Architecture/Debugger) can pause transpiled code. Off, the output is byte-identical to non-debug code. |

`rt/transpiler/JSTranspilerMap` is a nested `Block` tree of parallel `IntList` java/script line lists - a bespoke format, not Source Map v3.

## Build-time transpilation

The Maven plugin `js-transpiler-maven` exposes one mojo, `org.monflabs.galtajs.maven.JSTranspilerMojo` (goal `generate-sources`, prefix `monflabs`), which builds a `JSEnvironment` with `ScriptOptimizer.defaultOptimizer()` and delegates to `transpiler/path/PathTranspiler`. Parameters: `sourceDirectory` (`${basedir}/js`), `outputDirectory` (`${project.build.directory}/generated-sources/js`), `jsPackage` (`js`), `includes`/`excludes`, `sourceMap`, `sourceCode`, `mapFile`, `sourceFile`, `sourceInComments`, `maxSourceInComments`, `splitCode`, `commonJS`, `encoding`, `failOnError`, `followSymlinks`, `galtaJs`, `verbose`. Usage is in [Execution modes](/GaltaJS/UserGuide/ExecutionModes).

`parent-js-precompiled/` ships js-beautify (JS, CSS, HTML) and the TypeScript compiler (`typescript.js`, about 9 MB, a few minutes of build time) transpiled at build time; their tests check that the transpiled code gives the same output as the interpreted one.

## Transpiled modules

Since the live-bindings work (see the [design brief](/GaltaJS/Architecture/Notes)), transpiled modules behave like interpreted ones for exports and imports:

- `JSTranspiledUnit.resolveExport(name, Set<ResolveKey>)` and `getExportAccessor(name)` mirror `JSInterpretedUnit`; `AbstractModule.registerLiveExport()` backs local exports with a `JSVarRef` cell.
- `ASTImport.transpileItemBindings()` marks named imports with `setLiveImportBinding(...)`, so reads are emitted as `checkTDZ(importModule(_ctx, "...").getExportAccessor("x").getValue(), "x")` instead of an array slot (with a documented fallback to the plain slot for `default`). Import hoisting is emitted at the top of `_runValue`; `ASTFunctionDecl.hoistValue()` is the function-declaration counterpart.
- Remaining limitations: the *array slot* of a plain `import {x}` binding stays a one-time snapshot (only `ASTIdentifier`'s read path is live), and `JSSourceModuleResolver.loadTranspiledModule()` constructs and runs the unit with no `earlyRegister` hook, so self-imports and cycles through that path are a known gap (`KnownGaps.md`). Cycle linking (`dfsIndex`/`cycleRoot`) lives on `JSInterpretedUnit`; see [Modules runtime](/GaltaJS/Architecture/ModulesRuntime).

## Why Java source, not bytecode

- Source generation is simpler than bytecode generation and the output is readable and debuggable with a Java debugger.
- `javac` and the JIT optimize the result.
- The cost is a compiler at runtime (the JDK tool API or an external compiler such as ECJ) and Java's own rules: unreachable code is an error (hence the optimizer's dead-code pass and `asBooleanCondition`, which rewrites a literal `false` as `Boolean.FALSE.booleanValue()` to defeat the JLS 14.21 check), identifiers must be unique (hence `p_N`/`F_N` naming), and method and constant-pool sizes are capped (hence code splitting).

## Source

`transpiler/JSTranspiler.java`, `transpiler/JSTranspilerOptions.java`, `transpiler/context/TranspilerCodeSplitter.java`, `transpiler/context/TranspilerGeneratorBlockSplitContext.java`, `transpiler/context/TranspilerGeneratorConstantPoolContext.java`, `transpiler/path/PathTranspiler.java`, `rt/transpiler/JSTranspiledUnit.java`, `rt/transpiler/TranspiledGlobalRuntimeContext.java`, `rt/transpiler/JSTranspilerMap.java`, `rt/transpiler/JSVar.java`, `node/ASTVarContainer.java`, `node/control/ASTFunction.java`, `node/control/ASTImport.java`, `modules/JSSourceModuleResolver.java`, `galta/parent-js/js-transpiler-maven/.../JSTranspilerMojo.java`, `galta/parent-utilities/javacompiler/`.
