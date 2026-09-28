# Modules runtime

A *unit* is a compiled script or module; a *resolver* maps a specifier to a *descriptor* that can load a unit; a `JSModule` exposes exports. Linking follows the ECMAScript module algorithm (a Tarjan strongly-connected-components walk with async bookkeeping) implemented on `JSInterpretedUnit`. Usage is in [Modules](/GaltaJS/UserGuide/Modules) and [Native modules and resolvers](/GaltaJS/Extending/NativeModules).

## Class tree

```
JSModule (interface)                    JSModuleDescriptor (interface)
  '-- AbstractModule                      |-- NativeModuleDescriptor       (isScript()=false)
        |-- JSNativeModule                |-- StaticScriptDescriptor       (wraps a compiled ASTProgram)
        '-- JSScriptUnit  {ESM|COMMONJS}  |-- JSSourceModuleResolver.BaseDescriptor
              |-- JSInterpretedUnit       '-- TranspiledModuleDescriptor
              '-- JSTranspiledUnit (rt/transpiler)

JSModuleResolver (interface)
  '-- AbstractModuleResolver             (findModule(), ".js" fallback, isCommonJS())
        |-- NativeModuleResolver          '-- library/node/NodeModuleResolver
        '-- ScriptModuleResolver          (initModule(globalContext, unit))
              |-- JSSourceModuleResolver  (interpreted OR transpiled via initTranspiler())
              |     |-- JSMemoryModuleResolver
              |     '-- JSPathModuleResolver  '-- JSFileModuleResolver
              '-- JSTranspiledModuleResolver (precompiled classes under a base package)
```

`JSModuleResolver.loadModule(globalContext, name, Consumer<JSModule> earlyRegister)` hands the freshly constructed unit back *before* its body runs, so a self-import or cycle reached from inside the body finds the same instance in the caller's cache instead of loading a second copy. `ModuleUtil.resolvePath(...)` normalizes specifiers.

## `JSModule`

`getDefaultExport()/setDefaultExport()`, `getNamedExports()/addNamedExports()`, `addStarReExport(key, value, source)`, `addNamedReExport(exportedName, source, sourceName)`, `getModuleNamespaceObject()` (a cached `rt/builtins/standard/module/ModuleNamespaceObject`, spec 9.4.6), `getExport(name)`, `getExportAccessor(name)` returning a live `VarAccessor`, `getLiveDefaultExportAccessor()`, `getExportedNames(Set<JSModule>)` (16.2.1.6.2) and `resolveExport(name, Set<ResolveKey>)` (16.2.1.6.3) with the records `ResolveKey(JSModule, String)` and `ResolvedBinding(module, bindingName, accessor)`. `AbstractModule.registerLiveExport(name, accessor)` backs an export with a binding cell so importers see updates.

`JSScriptUnit` adds `execute()`, `executeThis(_this)`, `executeWithContext(ctx)`, `initModule(globalContext, commonJS)`, `getModuleContext()`, `getModuleType()`. `JSInterpretedUnit` adds `getProgram()`, `getModuleStatus()` (`UNLINKED, EVALUATING, EVALUATED, ERRORED`), `getEvaluationError()`, `isPendingDeferredEvaluation()`, and the callbacks `addEvaluationCompletionCallback(Runnable)` (a TLA module's "really finished"), `addSettleCallback(Runnable)` (circular `export * from`), `addDefaultExportCallback(Runnable)` (a self-import reaching a not-yet-run `export default`).

## Linking

`JSInterpretedUnit.linkModule(context)` is a spec-shaped Tarjan walk with the spec's own fields:

```
  int dfsIndex = -1            [[DFSIndex]] / [[DFSAncestorIndex]]
  JSInterpretedUnit cycleRoot  [[CycleRoot]]; null while the module is open on the stack
  pendingAsyncDependencies     count of async deps still evaluating
  asyncParentModules           [[AsyncParentModules]], filled only through dep.cycleRoot (step 11.c.iv.1)

  A --> B --> C --> A          one SCC; C's cycleRoot = A; the SCC settles as one unit
        '--> D (async, TLA)    B.pendingAsyncDependencies++ ; D.asyncParentModules += B
```

`import defer` dependencies are skipped during the walk (`item.deferred()`), parsed and cached without `initModule()`.

## Running a module

`JSInterpretedUnit.executeWithContext()` for a module:

1. `moduleStatus = EVALUATING`; save and restore the global context's current script unit.
2. `gc.registerRootModule(this)` so a self-import finds this instance.
3. `gc.setThis(UNDEFINED)` - a module's `this` is never `globalThis`.
4. `program.hoistDeclarations(context)` (import bindings first: `ASTImport.hoistBindings()`).
5. `gc.getExecutor().runWithDrainSuppressed(() -> linkModule(context))`.
6. If linking returns `false` (an async dependency is still pending) the body is *not* started: `drainAndShutdownIfOutermost()` lets the dependency's continuation run, and `notifyAsyncParents()` later calls `runBody()` when `pendingAsyncDependencies` reaches 0. Otherwise `runBody(context)` runs immediately.

Top-level `await` goes through `execute(supplier, async=true)` and `startCoroutine`: the synchronous prefix runs on the calling thread, the rest resumes from microtasks, and `execute(code, async, onGenuineCompletion)` reports the real completion because the method's return value is only a placeholder. A classic script root does not participate in this protocol, which is why a static import of a TLA module from a script sees its bindings in TDZ; the [User's Guide](/GaltaJS/UserGuide/Modules) shows the two workarounds (dynamic import, or a module root).

## Dynamic import, `import defer`, attributes

- `import(spec[, options])` parses to `ASTImportCall` (`ImportCallMode.NORMAL`; `import.defer(...)`/`import.source(...)` give `DEFER`/`SOURCE`). `RuntimeUtil.dynamicImportWithAttributes(...)` queues a microtask and resolves the promise through `addEvaluationCompletionCallback`.
- `import defer * as ns from '...'`: `AbstractModule.getDeferredModuleNamespaceObject(deferredView)` returns a cached `ModuleNamespaceObject(env, view, deferred=true)` whose first property access triggers evaluation; a separate cached instance keeps namespace identity stable (test262 `import-defer/deferred-namespace-object/identity.js`).
- Import attributes (`with { type: 'json' }`) reach `JSGlobalContext.importAttributedModule(unitContext, name, attributes)`; both global contexts implement it, the interface default throws. The synthetic module's default export comes from `RuntimeUtil.parseAttributedModuleContent(env, type, descriptor, name)`: `json` parses `descriptor.getScript()`, `text` returns it, `bytes` wraps `descriptor.getBytes()` in `ArrayBuffer.immutableOf(...)` + `Uint8Array`. Cached under `"attr:<type>:<name>"`, a distinct record from the plain import. `import.meta` is an ordinary empty object per module.
- Source-phase imports (`import source x from '...'`, `ImportCallMode.SOURCE`): `JSGlobalContext.importModuleSource(unitContext, name)` -> `ModuleSource.resolve(env, cache, resolvedName)` consults the descriptor only (`getModuleSourceText()`; `null` -> `SyntaxError`, spec GetModuleSource) and never loads the module. The object is `rt/builtins/standard/module/ModuleSource` (prototype chain `ModuleSourcePrototype` -> `AbstractModuleSourcePrototype`; `%AbstractModuleSource%` is not a global, `AbstractModuleSourceConstructor.get(env)` reaches it). `ASTImport.hoistBindings()` binds it at hoist time and `evaluate()` does nothing; `getAllModuleRequests()` excludes such imports so `readyForSyncExecution()` cannot evaluate the target. A re-exported source binding (`import source x; export {x}`) is an indirect entry with `ASTProgram.SOURCE_IMPORT_NAME`; `resolveExport()` answers with a `ResolvedBinding` whose module is `null` and whose binding name carries the resolved target name, so two re-exports of the same target compare as unambiguous without loading it (the transpiler emits `registerIndirectExport(..., "*source*")` instead of a live local export for these).
- `JSGlobalContext` module surface: `importModule`, `importAttributedModule`, `importModuleSource`, `importDeferredNamespace`, `gatherAsynchronousTransitiveDependencies`, `ensureModuleEvaluated`, `startAsyncDependencyEvaluation`, `peekModule`, `registerRootModule`.

## CommonJS

`JSEnvironment.SCRIPT_COMMONJS` makes `ASTProgram.isCommonJS()` true (the transpiler bakes it as an `isCommonJS()` override), which provides `module`, `exports` and `require` to the unit. `library/CommonJSLibrary` adds the global `require(name)` (`RuntimeUtil.importModule(...)` then `getDefaultExport()`, retrying with `.js`); `AbstractModuleResolver.setCommonJS(true)` marks every module of a resolver as CommonJS. A CommonJS module can also be imported from ES code as a default export.

## Interpreted versus transpiled modules

Modules are not transpiled on the fly unless a resolver was configured with `initTranspiler(...)`; `JSInterpretedUnit.executeWithContext()` is reached "regardless of whether the overall program is running in transpiled mode". `JSTranspiledUnit` self-registers and resolves exports the same way (`resolveExport`, `getExportAccessor`), but the SCC/`cycleRoot` machinery lives on `JSInterpretedUnit`, and `JSSourceModuleResolver.loadTranspiledModule()` constructs-then-runs with no `earlyRegister` hook and returns `null` on any exception. See [Transpiler](/GaltaJS/Architecture/Transpiler) and [Known gaps](/GaltaJS/KnownGaps).

## Source

`JSModule.java`, `JSModuleDescriptor.java`, `modules/*.java` (`AbstractModule`, `JSScriptUnit`, `JSInterpretedUnit`, `JSModuleResolver`, `AbstractModuleResolver`, `NativeModuleResolver`, `JSNativeModule`, `ScriptModuleResolver`, `JSSourceModuleResolver`, `JSMemoryModuleResolver`, `JSPathModuleResolver`, `JSFileModuleResolver`, `JSTranspiledModuleResolver`, `StaticScriptDescriptor`, `ModuleUtil`), `rt/transpiler/JSTranspiledUnit.java`, `rt/JSGlobalContext.java`, `rt/interpreter/InterpretedGlobalRuntimeContext.java`, `rt/builtins/standard/module/ModuleNamespaceObject.java`, `rt/builtins/standard/module/ModuleSource.java` (+ `ModuleSourcePrototype`, `AbstractModuleSourceConstructor`, `AbstractModuleSourcePrototype`), `node/control/ASTImport.java`, `node/ASTImportCall.java`, `library/CommonJSLibrary.java`.
