# Interpreter

The interpreter executes the `ASTProgram` directly: every node's `evaluate()` runs against a chain of runtime contexts that hold the bindings, `this`, and the global state. There is no intermediate representation. Performance comes from AST metadata (see [Optimizer](/GaltaJS/Architecture/Optimizer)) and from per-node inline caches described below.

## Runtime contexts

Contracts live in `rt/`, implementations in `rt/interpreter/`:

```
JSContext                                   (thread-bound: JSContext.get(), with(), run())
  '-- JSRuntimeContext                      (bindings, this, new.target, private names, disposables)
        |-- JSUnitContext / JSModuleContext / JSGlobalContext / JSFunctionContext
        |-- JSBoundaryContext / JSEvalRuntimeContext
        '-- implementations:
              AbstractRuntimeContext
                '-- InterpretedRuntimeContext
                      |-- InterpretedUnitRuntimeContext
                      |     |-- InterpretedGlobalRuntimeContext      (executor, GlobalThis, streams, modules)
                      |     '-- InterpretedModuleRuntimeContext
                      |-- InterpretedBlockRuntimeContext
                      |     '-- InterpretedWithRuntimeContext
                      |-- InterpretedFunctionRuntimeContext
                      |-- InterpretedFunctionBodyRuntimeContext    (2nd frame when parameters have expressions)
                      |-- InterpretedFieldInitializerRuntimeContext
                      '-- InterpretedClassPrivateScopeContext
```

The "stack" is a parent-pointer chain (`getParent()`), not an array. The current context is thread-bound through `JSContext.get()` and installed for the extent of `ctx.with(() -> ...)` / `run(...)`; the underlying `_ScopedValue` is an `InheritableThreadLocal`. This is why calling a `Callable` from Java outside any script requires `ctx.with(...)`.

`JSRuntimeContext` API highlights: `getParent()`, `getMainContext()`, `getVarDeclContext()`, `getGlobalContext()`, `getFunctionContext()`, `getVariableValue`/`getVariableEntry`/`getLocalVariableEntry`, `createVariable(name, value, VAR_TYPE[, configurable])`, `setVariable`, `deleteVariable`, `getThis()/setThis()`, `getNewTarget()/setNewTarget()`, `resolvePrivateName(name)`, `collectEnclosingPrivateNames(Set)`, `registerDisposableResource`/`getOwnDisposableResources` (for `using` declarations, disposed by `DisposeResourcesUtil`).

`this` lives on the frame; `setThis` delegates up the chain, so arrow functions are transparent. `arguments` is only materialized (`rt/builtins/standard/arguments/Arguments`) when `ASTIdentifier.init()` sees `arguments` or `eval` in a non-arrow function (`markEnclosingNonArrowUsesArguments()`).

## Completions and exceptions

Control flow is a hybrid:

- `break`, `continue` and `return` are completion-record-like return values: `InterpretedUnitRuntimeContext.Signal` `{ Type NONE|CONTINUE|BREAK|RETURN, String label }` with shared singletons and labelled instances. Every `evaluate()` returns a `Signal`; statement lists and loops inspect it.
- `throw` is a real Java exception: `rt/JSRuntimeException` carries the JavaScript value (`getJavascriptException()`) and an `ASTNode`-based stack (`fillStackTrace`, `StackEntry`). `JSRuntimeUncatchableException` and its subclass `JSRuntimeInterruptException` bypass `catch` (script interruption, unit-test assertion failures).

Expression values flow through `rt/JSResult`, a mutable holder that is a plain value, a *sequence* (`SEQ_EMPTY`/`SEQ_ONE`/`SEQ_MULTI`, backed by a `JSArray`, the GaltaJS JSON-path extension) or `CHAINING_NULL` (an optional-chaining short circuit).

## Identifier fast paths

`ASTIdentifier.evaluateValue()` tries, in order:

1. **Phase 2b** - if the optimizer set `scopeHops >= 0`, walk exactly that many parents; then either **Phase 2c**, `frame.getSlot(slotIndex)` when the frame `hasSlots()` and the resolved `VAR_TYPE.isSlotEligible()`, or `frame.getAccessor(id)` followed by `checkTDZ`.
2. **Phase 2d** - `IdentIC(VariableMap owner, int epoch, VarAccessor entry)`, a single `volatile` record guarded by owner identity and `mutationEpoch`; published once, on a cold cache.
3. Slow path - `RuntimeUtil.getIdentifierValue(context, id, throwError)`: walk the chain (including `with` frames), then `globalThis`, else `ReferenceError`.

Assignment mirrors this (`ASTIdentifier.evaluateAssign(..., VarAccessor preResolved)`): `ASTAssign` resolves the reference *before* evaluating the right-hand side (spec 13.15.2 step 1a) only for identifiers without scope-resolution metadata (`scopeHops < 0`); an annotated binding cannot move while the right-hand side runs, so the pre-resolution is skipped there.

## Property fast path

`ASTMember` keeps a monomorphic `PropIC(JSObjectImpl owner, CustomLinkedMap.EntryImpl<String> entry)` in a `volatile` field, guarded by base identity and `!entry.isRemoved()`. Eligibility is decided once per class through `ClassValue<Boolean> PROPIC_ELIGIBLE`, true only when the class did not override `getOwnProperty(String,Object,Object)` from `JSObjectImpl` (so `BuiltinFunction`, `Arguments`, `GlobalThis` and proxies stay on the slow path). Own-property misses are never cached; `#private` names bypass the cache (`context.resolvePrivateName` then `RuntimeUtil.getPrivateField`). The cache is sound because `CustomLinkedMap` entries are stable across value writes, descriptor changes and rehashes (see [Object model](/GaltaJS/Architecture/ObjectModel)).

## `with`

`InterpretedWithRuntimeContext extends InterpretedBlockRuntimeContext` holds the with-object and its accessor. Name resolution returns a live facade `VarAccessor` (cached per name) whose has-property and `Symbol.unscopables` checks re-run on each access. A function resolved through a `with` scope is wrapped in `rt/builtins/WithClosure` so its `this` is the with-object; `ClosureAccessor` makes the wrapper transparent for every non-call access.

## Direct `eval`

`StandardLibrary`'s `eval` re-parses through `JSEnvironment.createEvalScript(text, name, forceStrict, callerHasNewTarget, callerIsMethod, callerIsDerivedCtor, callerInParameterExpressionScope, callerInFieldInitializer, callerPrivateNames)`; the caller facts reproduce the spec's early errors. `JSEvalRuntimeContext.resolveGlobalDeclarationTarget(varName)` mirrors EvalDeclarationInstantiation to decide whether a `var` reaches the global object. Eval-hoisted bindings are created *configurable* (`createVariable(name, value, type, true)`), unlike ordinary declarations. Any direct `eval` or `with` in an enclosing subtree disables the scope-resolution fast paths for that subtree.

## Strict mode

Strictness is a parse-time fact threaded through `ASTNode.InitContext` (`isStrictMode()` vs `isGenuinelyStrict()`, the latter excluding forced or inherited strictness for `eval`). Runtime checks read `context.isStrictMode()` (assignment to `eval`/`arguments`, writes to a `FUNCTION_SELF` binding, undeclared assignments when `mustDeclareAllVariables`).

## Source

`rt/JSContext.java`, `rt/JSRuntimeContext.java`, `rt/JSGlobalContext.java`, `rt/JSEvalRuntimeContext.java`, `rt/interpreter/*.java`, `rt/interpreter/InterpretedUnitRuntimeContext.java` (`Signal`), `rt/JSResult.java`, `rt/JSRuntimeException.java`, `node/ASTIdentifier.java`, `node/ASTMember.java`, `node/assignop/ASTAssign.java`, `rt/builtins/WithClosure.java`, `rt/builtins/ClosureAccessor.java`, `rt/RuntimeUtil.java`.
