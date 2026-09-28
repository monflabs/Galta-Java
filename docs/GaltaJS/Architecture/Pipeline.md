# Parser and AST

Source text becomes an `ASTProgram` in three steps: the JavaCC parser builds the tree, `ASTProgram.__init()` performs the early-error and scoping pass, and the optimizer annotates or rewrites nodes. The same `ASTProgram` then drives all four back-ends (interpreter, optimizer, transpiler, decompiler), which is why every `ASTNode` carries methods for each.

## Preprocessing (host-invoked)

`preprocessor/ScriptPreProcessor.preprocess(String source, Map<String,Object> symbols)` implements `// #if SYMBOL` / `// #elif` / `// #else` / `// #endif` line comments. It is not called by `JSEnvironment`; hosts (and the engine's own test harness) call it before `createScript()`. Suppressed lines are commented out rather than removed so line numbers survive. `#define`/`#undef` throw "not yet implemented", `#if` cannot be nested, and a condition is a single symbol. Usage is documented in [Preprocessor](/GaltaJS/UserGuide/Preprocessor).

## Parser

- The grammar is `src/main/javacc/org/monflabs/galtajs/parser/JSParser.jj` (about 5,500 lines). `javacc-maven-plugin` regenerates the parser into `target/generated-sources/javacc/` on every build; edit the `.jj`, never the output.
- `parser/TokenMgrError.java` is hand-maintained (it carries `errorLine`/`errorColumn` for `ScriptError`); an antrun step deletes JavaCC's generated copy before compilation.
- Other hand-written support classes: `ScriptError`, `ErrorList`, `ParserFastStringReader`, `ParserContextImpl`/`JSParserContext`.
- Lexer-level GaltaJS extensions (number suffixes, `..`, `[?`, `.(`, `@`, `?:`, `|>`, the `*==` family) are always tokenized; whether they are accepted is decided later, at AST init, from the environment flags (`ASTNode.sequenceEnabled`, `ASTIdentifier` for `@`). Type hints are the exception: their productions are guarded by `LOOKAHEAD({env.supportTypeHints() && ...})`.

## `JSEnvironment.compileProgram()`

```java
JSParser parser = new JSParser(this, text, isDebugEnabled());
program = parser.MainProgram(text);
program.__init(env, commonJS, module, eval, forceStrict,
               callerHasNewTarget, callerIsMethod, callerIsDerivedCtor,
               callerInParameterExpressionScope, callerInFieldInitializer, callerPrivateNames);
scriptOptimizer.optimize(env, program);
```

Everything runs inside `new ParserContextImpl(this).with(...)`. JavaCC's `ParseException`/`TokenMgrError` are converted to `JSParseException` with an annotated source excerpt (`JSException.extractSourceCode`).

`__init` is a second tree walk, separate from parsing: strict-mode resolution, scope and `VariableDef` creation, hoisting, `PrivateNameValidator` (AllPrivateNamesValid), and the direct-`eval` caller restrictions (`checkEvalCallerRestrictions`, `checkParameterExpressionArgumentsRestriction`, `checkFieldInitializerArgumentsRestriction`). The many `caller*` parameters exist only to reproduce the spec's early errors for code compiled by a direct `eval`.

## Nodes and their four back-ends

`node/ASTNode` (abstract, implements `INode`) declares one method group per consumer:

| Back-end | Methods |
|---|---|
| Interpreter | `Signal evaluate(JSInterpretedRuntimeContext, JSResult)`, `Object evaluateValue(...)`, `evaluateTypeof`, `evaluateAssign(context, rightValue, assigner, result, returnOriginalValue)`, `evaluateDelete` |
| Optimizer | `boolean isConstant(JSOptimizerContext)`, `createOptimizedContext`, `updateOptimizedContext` |
| Transpiler | `transpileJavaStatement(JSTranspilerGeneratorContext, TranspilerJavaBuilder)`, `String transpileJavaExpression(...)`, `transpileTypeofExpression`, `transpileDeleteExpression`, `transpileJavaAssignment(...)` |
| Decompiler | `decompileStatement(JavaBuilder)`, `String decompileExpression()` |

Node families: `node/control/` (statements: `ASTIf`, `ASTFor`, `ASTForIn`, `ASTForOf`, `ASTWhile`, `ASTDoWhile`, `ASTSwitch`, `ASTTry`, `ASTWith`, `ASTBlock`, `ASTFunction`/`ASTFunctionDecl`/`ASTFunctionArrow`/`ASTFunctionMethod`, `ASTImport`/`ASTExport`, `ASTYield`, and the GaltaJS-only `ASTSynchronized`), `node/clazz/` (`ASTClassDecl`, fields, methods, accessors, static blocks), `node/binaryop/`, `node/unaryop/`, `node/assignop/`, `node/ternaryop/`, `node/call/` (`ASTCall`, `ASTCallSpread`, `ASTPipelineCall`), `node/literal/`, `node/variable/` (`ASTVariableDecl`, `...Const`, `...ScopedLet`, `...Using`), `node/debug/`. The JSON-path extensions are `ASTArrayMember*` nodes (flatten, deep scan, filter, map).

## Scoping model

There is no class named `Scope`. Every scope-creating node extends `node/ASTVarContainer`, which owns a `VariableDefContainer` (a `LinkedHashMap<String,VariableDef>` in declaration order) and a list of nested `ASTFunction`s.

`VariableDef` carries: `name`, `varType`, `varIndex` (the slot), and flags such as `transpilerDeclared`, `used`, `annexBHoistBlocked`, `annexBBlockHoisted`, `functionSelfStrict`, `tdzExempt`, `declSite`, plus the live-import triple `importModuleRequest`/`importExportName`/`importAttributesType`.

`VAR_TYPE` (in `rt/interpreter/JSInterpretedRuntimeContext`): `CONST, LET, VAR, AUTO, FUNCTION, PREDECLARED, SYSTEM, FUNCTION_SELF, USING`, with predicates `canAssign()`, `canOverride()`, `canBeOverriden()`, `isDeclaredGlobally()`, `isHoisted()`, `isSlotEligible()`. `FUNCTION_SELF` is the immutable self-binding of a named function expression (silent no-op on write in sloppy code, `TypeError` in strict code).

## Runtime bindings

- `rt/interpreter/VariableMap` is the per-frame store: an open-hash map of `VariableEntry` (implements `VarAccessor` and `Map.Entry<String,Object>`) plus a declaration-order `Object[] slots` array filled once per frame by `initSlots`; hash entries alias the slots. A `volatile int mutationEpoch` invalidates identifier inline caches when bindings are added or removed.
- `rt/transpiler/VarAccessor` is the binding-cell interface shared by both modes; `VarAccessor.ofStatic(name, value)` and `VarAccessor.importBinding(localName, target)` (reports `CONST`, throws on `setValue`, per CreateImportBinding).
- **TDZ** is a sentinel value: `RuntimeUtil.TDZ` sits in the slot until the declaration runs, and reads go through `RuntimeUtil.checkTDZ(value, name)`. The transpiler seeds `p_N[i] = TDZ;` for `let`/`const`/`using` at container creation (`ASTVarContainer.transpilerDeclareStatement`) and can omit the seed (`needsTdzSeed()`, overridden by `ASTFor`) or the guard (`VariableDef.isTdzExempt()`, `ASTIdentifier.isTdzSafeLinearRead()`).
- **Hoisting**: `ASTProgram.hoistDeclarations(context)` runs before a module body; `ASTFunctionDecl.createFunction()` implements Annex B.3.3 block-function hoisting (`hoistedToRoot`, `isAnnexBHoistCandidate`); `initGlobalVariables(...)` mirrors a script's (never a module's) top-level `var`/function declarations onto `globalThis`.
- **Closures**: the interpreter captures the parent `JSRuntimeContext` chain; the transpiler captures the enclosing `Object[] p_N` arrays as Java local-class captures.

## Script cache

`JSEnvironment` keeps two `LRUCache<String,ASTProgram>` instances on its shared data (`getScriptCache()`, `getEvalCache()`), sized by `scriptCacheSize`/`evalCacheSize`; both default to 0 (disabled). `createScript(text, moduleName, flags, ...)` caches only when `SCRIPT_ADDTOCACHE` is set, `SCRIPT_COMMONJS` is not, `forceStrict` is false and every eval caller fact is permissive, because the same text parses differently depending on its caller.

The cache stores the `ASTProgram`, not the unit. Every call wraps the (possibly shared) program in a fresh `JSInterpretedUnit` via `StaticScriptDescriptor.loadScript(env)`. Programs are therefore shared across executions and threads, which is why the inline caches on nodes are published through `volatile` fields with validity guards. `evaluateScript`/`evaluateExpression` pass flags `0` and never cache; `evaluate()` also has a shortcut that returns a variable's value directly when the whole text is a bare identifier.

## Decompiler

There is no `JSDecompiler` class. `ASTProgram.decompile()` / `decompile(JavaBuilder)` drives each node's `decompileStatement`/`decompileExpression`, printing the AST back as JavaScript through `util/JavaBuilder` with `// line: N, col C` comments. The fourth pass of `AllGaltaJSTests` parses, decompiles, re-parses and runs every test to check the round trip; the optimizer's effect is also visible this way (see [Execution modes](/GaltaJS/UserGuide/ExecutionModes)).

## Source

`JSEnvironment.java`, `src/main/javacc/org/monflabs/galtajs/parser/JSParser.jj`, `parser/TokenMgrError.java`, `node/ASTNode.java`, `node/ASTProgram.java`, `node/ASTVarContainer.java`, `node/ASTIdentifier.java`, `rt/interpreter/VariableMap.java`, `rt/transpiler/VarAccessor.java`, `rt/RuntimeUtil.java` (`TDZ`, `checkTDZ`), `preprocessor/ScriptPreProcessor.java`, `util/JavaBuilder.java`.
