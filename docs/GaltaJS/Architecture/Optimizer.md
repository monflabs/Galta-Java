# Optimizer

The optimizer is a set of AST passes run once by `JSEnvironment.compileProgram()` after `__init`. It rewrites constant subtrees, removes unreachable statements and annotates identifiers with scope-resolution metadata that the interpreter's fast paths consume. It produces no intermediate representation and there is no JIT: "optimized" means the AST has been folded and annotated.

## Configuration

`optimizer/ScriptOptimizer` is built with `ScriptOptimizer.newBuilder()`:

| Builder method | Effect |
|---|---|
| `optimizers(NodeOptimizer[])` | The passes to run, in order. |
| `traceStream(PrintStream)` | Where to print an optimization trace. |
| `traceNodes(boolean)` | Trace per node. |

`ScriptOptimizer.defaultOptimizer()` (the environment default) runs `DEFAULT_NODE_OPTIMIZER_NODES`; `ScriptOptimizer.emptyOptimizer()` runs nothing. The environment is configured through `JSEnvironment.Builder.scriptOptimizer(...)`.

## Default passes

`DEFAULT_NODE_OPTIMIZER_NODES` contains exactly two `NodeOptimizer`s:

```
ASTProgram
   |
   |-- ConstantFoldingAndUnreachableCodeOptimizer   (one fused traversal)
   |       constant folding  +  unreachable-code removal
   |
   '-- ScopeResolutionOptimizer                      (metadata only)
           (scopeHops, slotIndex, resolvedVarType) on each ASTIdentifier
```

### Constant folding and unreachable code

`ConstantFoldingAndUnreachableCodeOptimizer` fuses two passes so the tree is walked once, with the folding result feeding the reachability analysis through `afterChildrenOptimized`:

- `ConstantFoldingOptimizer` evaluates constant subtrees by actually running them through `OptimizerEvaluationRuntimeContext`, so mixed-type arithmetic, rounding and string conversion follow the engine's own rules rather than a re-implementation. It also folds the `NaN` and `Infinity` identifiers into literals when they are not shadowed; it never folds `undefined`.
- `UnreachableCodeRemovalOptimizer` removes dead branches (`if (true)`), truncates statement lists after a `TERMINATES_FLOW` statement (`ASTReturn`, `ASTThrow`, `ASTBreak`, `ASTContinue`) and drops unreachable code, which the transpiler needs because Java rejects unreachable statements.

The decompiler shows the effect: `const x = 2 * 3 * 7` becomes `const x=42`, and a `return 0` after an always-taken branch disappears.

### Scope resolution

`ScopeResolutionOptimizer` publishes `(scopeHops, slotIndex, resolvedVarType)` on each `ASTIdentifier` through `setScopeResolution(byte hops, int slotIndex, VAR_TYPE)`. It models the runtime frames precisely: only `ASTProgram`, `ASTFunction`, declaration-bearing `ASTBlock`, `ASTFor`, `ASTFor_` and `ASTStringTemplate` create frames; `ASTCatch` does not. It bails out (leaves `scopeHops = -1`) for true globals, for any subtree containing `with` or a direct `eval`, for functions with parameter expressions (which run in two frames), and for functions containing a class declaration (whose bodies add an unmodeled frame, see [Known gaps](/GaltaJS/KnownGaps)). The interpreter's Phase 2b/2c fast paths use this metadata; see [Interpreter](/GaltaJS/Architecture/Interpreter).

It must run after constant folding, since folding can replace `NaN`/`Infinity` identifiers, which must not count toward slot indexing.

## The "optimized" test pass

`tests.AllGaltaJSInterpreterOptimizedTests` runs the whole suite with `BaseProjectTestCase._EXECUTE_OPTIMIZED = true`, which builds the environment with `ScriptOptimizer.newBuilder().optimizers(DEFAULT_NODE_OPTIMIZER_NODES)` and dumps an optimization trace at the end. It exercises the same interpreter; only the AST differs. See [Testing](/GaltaJS/Architecture/Testing).

## Source

`optimizer/ScriptOptimizer.java`, `optimizer/NodeOptimizer.java`, `optimizer/ConstantFoldingAndUnreachableCodeOptimizer.java`, `optimizer/ConstantFoldingOptimizer.java`, `optimizer/UnreachableCodeRemovalOptimizer.java`, `optimizer/ScopeResolutionOptimizer.java`, `optimizer/OptimizerEvaluationRuntimeContext.java`.
