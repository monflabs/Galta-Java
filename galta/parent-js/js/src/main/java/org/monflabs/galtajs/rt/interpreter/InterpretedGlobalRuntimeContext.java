/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monflabs.galtajs.rt.interpreter;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.debug.api.impl.DebugHook;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSModuleResolver;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.GlobalThis;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.performance.PerformanceData;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;
import org.monflabs.util.Console;


/**
 * Implementation that hides some methods from the runtime
 */
public class InterpretedGlobalRuntimeContext extends InterpretedUnitRuntimeContext implements JSInterpretedGlobalRuntimeContext, org.monflabs.galtajs.debug.api.impl.Debuggable {
	
	private JSExecutor executor;

	// Written by other threads (a debugger or a UI asking to stop)
	private volatile RunningState runningState;
	private JSScriptUnit scriptUnit;
	private boolean evalExecution;
	// The strictness of the PERMANENT top-level script/module owning this
	// global context - set once by JSInterpretedUnit.executeWithContext(),
	// deliberately NOT touched by executeForEval()'s scriptUnit swap-and-
	// restore (scriptUnit is temporarily repointed to a nested eval's own
	// unit while it runs, sharing this SAME global context instance). Using
	// scriptUnit itself for isStrictMode() would leak that nested eval's
	// strictness into any OTHER code path that queries this global context's
	// strictness while the eval is mid-execution (e.g. a sloppy top-level
	// function's this-substitution check when called via eval() from
	// genuinely strict code - test262 language/function-code/10.4.3-1-82-s.js).
	private boolean ownForceStrictMode;

	private PerformanceData performanceData;
	private PrintStream out;
	private PrintStream err;
	
	private GlobalThis globalThis;
	
	protected DebugHook debugHook;

	protected Map<String, Object> runtimeProperties;
	
	protected Map<String,JSInterpretedUnit> sourceCode = new HashMap<>();
	
	private Object _this;
	
	private Map<String,JSModule> modules;

	public InterpretedGlobalRuntimeContext(JSEnvironment env, JSExecutor executor) {
		this(env,executor,RuntimeUtil.NOT_AVAILABLE);
	}
	public InterpretedGlobalRuntimeContext(JSEnvironment env, JSExecutor executor, Object _this) {
		super(env,_this);
		this.executor = executor;
		this.globalContext = this;
		this.runningState = RunningState.STOPPED;
		this.globalThis = new GlobalThis(env);
		this._this = _this!=RuntimeUtil.NOT_AVAILABLE ? _this : globalThis;
	}
	
	@Override
	public boolean isStrictMode() {
		return ownForceStrictMode || getEnvironment().isStrictMode();
	}

	public void setOwnForceStrictMode(boolean ownForceStrictMode) {
		this.ownForceStrictMode = ownForceStrictMode;
	}
	
	@Override
	public JSExecutor getExecutor() {
		return executor;
	}
	
	@Override
	public Object getThis() {
		return _this;
	}

	// JSRuntimeContext's own default setThis() delegates to getParent()
	// (correct for a NESTED context that doesn't own a `this` binding of
	// its own, e.g. a block) - this IS the root, so it must set its OWN
	// field directly instead (or the default's null-parent delegation
	// would NPE). Used by JSInterpretedUnit.executeWithContext() to force
	// a MODULE's own `this` to `undefined`, unconditionally, regardless of
	// what was passed to this context's constructor (spec: a module's
	// `this` is ALWAYS undefined, never the ambient globalThis fallback a
	// plain script gets for an unspecified `this`).
	@Override
	public void setThis(Object _this) {
		this._this = _this;
	}

	@Override
	public PerformanceData getPerformanceData() {
		if (performanceData == null) {
			performanceData = new PerformanceData(getEnvironment());
		}
		return performanceData;
	}
	
	public Map<String,JSInterpretedUnit> getSourceCode() {
		return sourceCode;
	}

	@Override
	public void reset() {
		super.reset();
		this.performanceData = null;
		this.runtimeProperties = null;
	}
	
	@Override
	public GlobalThis getGlobalThis() {
		return globalThis;
	}

	// Top-level `var`/function declarations must be visible as real own
	// properties of globalThis (e.g. via Object.getOwnPropertyDescriptor),
	// not just resolvable as identifiers. Route their hoisting through
	// globalThis instead of the local VariableMap; a freshly-created one
	// gets the spec's writable/enumerable descriptor, non-configurable for
	// a genuine script (CreateGlobalVarBinding's "deletable" is false) but
	// configurable when hoisted from direct eval code (deletable is true -
	// see EvalDeclarationInstantiation), tracked via isEvalExecution().
	// let/const/other bindings keep using the local VariableMap as before.
	// A ROOT module's own top-level var/function declarations must NOT
	// become globalThis properties (unlike a genuine script's) - they stay
	// in the module's own Module Environment Record. A root module shares
	// THIS SAME InterpretedGlobalRuntimeContext instance as its own
	// ASTProgram.evaluate() `context` (never a separate
	// InterpretedModuleRuntimeContext the way a DEPENDENCY module gets via
	// initModule()), so its var/function hoisting would otherwise reach the
	// globalThis-routing createVariable() override below - wrong for a
	// module (test262 instn-local-bndng-*.js: `var`/function declared at a
	// module's top level must be invisible via
	// Object.getOwnPropertyDescriptor(globalThis, name)).
	//
	// Deliberately a separate, STATELESS method rather than a persistent
	// "this context is currently running a module" flag on the instance:
	// $262.evalScript() can run a genuine SCRIPT against this SAME shared
	// context from within module code (test262 eval-*.js under
	// language/module-code) - that script's OWN var hoisting must still go
	// to globalThis, so the decision has to be made fresh at each
	// individual hoisting call (ASTProgram.evaluate(), which already knows
	// its own isModule()), not cached on the context across calls.
	public VarAccessor createModuleLocalVariable(String varName, Object value, VAR_TYPE type) {
		return super.createVariable(varName, value, type);
	}

	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type) {
		// Function declarations (CreateGlobalFunctionBinding) always redefine the
		// property's attributes - unlike var (CreateGlobalVarBinding), which only
		// defines a descriptor the first time - so a pre-existing configurable
		// property (e.g. from Object.defineProperty) gets reset to the standard
		// writable/enumerable/[[Configurable]]=D descriptor on every declaration.
		if(type==VAR_TYPE.FUNCTION) {
			PropertyDescriptor existing = globalThis.getOwnPropertyDescriptor(varName);
			PropertyDescriptor desc = (existing==null || existing.isConfigurable())
					? (isEvalExecution() ? PropertyDescriptor.DESC_DEFAULT : PropertyDescriptor.DESC_FIXED_PROP)
					: existing;
			globalThis.setOwnProperty(varName, value!=RuntimeUtil.NOT_AVAILABLE ? value : RuntimeUtil.UNDEFINED,
					desc, DESC_CHECK.STRICT, globalThis);
			return globalThis.getOwnVariableAccessor(varName, true);
		}
		if(type==VAR_TYPE.VAR || type==VAR_TYPE.AUTO) {
			if(!globalThis.hasOwnProperty(varName)) {
				PropertyDescriptor desc = isEvalExecution() ? PropertyDescriptor.DESC_DEFAULT : PropertyDescriptor.DESC_FIXED_PROP;
				globalThis.setOwnProperty(varName, value!=RuntimeUtil.NOT_AVAILABLE ? value : RuntimeUtil.UNDEFINED,
						desc, DESC_CHECK.NONE, globalThis);
			} else if(value!=RuntimeUtil.NOT_AVAILABLE) {
				globalThis.setOwnProperty(varName, value);
			}
			return globalThis.getOwnVariableAccessor(varName, true);
		}
		return super.createVariable(varName, value, type);
	}

	// Global bindings already track configurability themselves via isEvalExecution()
	// (see above) - the explicit configurable flag is for AbstractRuntimeContext's plain
	// VariableMap-backed local bindings, so it's ignored here rather than falling through
	// to AbstractRuntimeContext's 4-arg override, which would bypass the globalThis routing.
	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type, boolean configurable) {
		return createVariable(varName, value, type);
	}

	// Makes the vars hoisted above visible through the normal identifier
	// resolution chain (getVariableEntry/getVariableValue/hasVariable),
	// which otherwise only look at the local VariableMap.
	@Override
	public VarAccessor resolveOwnIdentifierEntry(String varName) {
		VarAccessor a = globalThis.getOwnVariableAccessor(varName, false);
		if(a!=null) {
			return a;
		}
		return super.resolveOwnIdentifierEntry(varName);
	}

	// A var/function-created global (not shadowed by a local binding) now
	// lives in globalThis rather than the local VariableMap, so the base
	// class's deleteVariable() wouldn't find it there and would incorrectly
	// fall through to "unresolvable identifier, delete succeeds". Route it
	// through globalThis's own [[Delete]], which correctly returns false for
	// its non-configurable descriptor without throwing (strict-mode
	// `delete identifier` is already rejected as a SyntaxError elsewhere,
	// before this can be reached).
	@Override
	public boolean deleteVariable(String varName) {
		if((getVariableMap()==null || getVariableMap().getEntry(varName)==null) && globalThis.hasOwnProperty(varName)) {
			if(isStrictMode()) {
				throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
			}
			return globalThis.deleteProperty(varName, DESC_CHECK.CHECK);
		}
		return super.deleteVariable(varName);
	}


	//
	// RunningState
	//
	@Override
	public RunningState getRunningState() {
		return runningState;
	}
	@Override
	public void setRunningState(RunningState runningState) {
		if(this.runningState!=runningState) {
			this.runningState = runningState;
			if(debugHook!=null) {
				debugHook.onStateChanged(this,runningState);
			}
		}
	}
	
	@Override
	public JSScriptUnit getScriptUnit() {
		return scriptUnit;
	}
	
	@Override
	public void setScriptUnit(JSScriptUnit unit) {
		this.scriptUnit = unit;
	}

	@Override
	public DebugHook getDebugHook() {
		return debugHook;
	}
	@Override
	public void setDebugHook(DebugHook debugHook) {
		this.debugHook = debugHook;
	}
	
	
	//
	// Execution signal
	//

	// Used to avoid the creation of new exceptions when using signals

//	@Override
//	public Object getReturnValue() {
//		return returnValue;
//	}
//
//	@Override
//	public void setReturnValue(Object signalValue) {
//		this.returnValue = signalValue;
//	}
	
	@Override
	public void checkInterrupted() {
		if(runningState==RunningState.STOPPING) {
			throw new JSRuntimeInterruptException();
		}
	}

	
	//
	// Eval Execution
	//
	@Override
	public boolean isEvalExecution() {
		return evalExecution;
	}

	@Override
	public void setEvalExecution(boolean eval) {
		this.evalExecution = eval;
	}


	//
	// Access to the output stream
	//
	
	@Override
	public PrintStream getOutStream() {
		if(out!=null) {
			return out;
		}
		return Console.outStream();
	}
	@Override
	public PrintStream getErrStream() {
		if(err!=null) {
			return err;
		}
		return Console.errStream();
	}
	@Override
	public void setOutStream(PrintStream out) {
		this.out = out;
	}
	@Override
	public void setErrStream(PrintStream err) {
		this.err = err;
	}
	
	
	//
	// Runtime properties
	// This is used to pass information from the calling context down
	// to the libraries
	//

	@Override
	public Object getProperty(String name) {
		if (runtimeProperties != null) {
			return runtimeProperties.get(name);
		}
		return null;
	}

	@Override
	public InterpretedGlobalRuntimeContext putProperty(String name, Object value) {
		if (runtimeProperties == null) {
			runtimeProperties = new HashMap<String, Object>();
		}
		runtimeProperties.put(name, value);
		return this;
	}

	@Override
	public InterpretedGlobalRuntimeContext removeProperty(String name) {
		if (runtimeProperties != null) {
			runtimeProperties.remove(name);
		}
		return this;
	}
	
	
	
	/////////////////////////////////////////////////////////////////////////
	// Module Resolver
	/////////////////////////////////////////////////////////////////////////

	@Override
	public JSModule importModule(JSUnitContext unitContext, String name) {
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);

		if(modules!=null) {
			JSModule m = modules.get(resolvedName);
			if(m!=null) {
				// A precompiled module that threw stays cached as errored, like an
				// interpreted one: importing it again rethrows instead of re-running it
				if(m instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu
						&& tu.getEvalStatus()==org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.ModuleEvalStatus.ERRORED) {
					throw RuntimeUtil.wrap(tu.getEvaluationError());
				}
				if(m instanceof JSInterpretedUnit unit) {
					// Re-throw the SAME (by identity) error every time - a
					// plain (non-deferred) re-import of an already-errored
					// module must reject the same way as the deferred-
					// namespace path (DeferredModuleView.real()) already
					// does, not silently hand back the broken module
					// object as if it were valid (test262 import-errored-
					// module.js: second import must also reject; import-
					// defer/errors/module-throws/defer-import-after-
					// evaluation.js: the SAME error object identity every
					// time, which requires staying cached rather than
					// being evicted and re-run from scratch below).
					if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
						throw RuntimeUtil.wrap(unit.getEvaluationError());
					}
					// `unit` itself may have finished successfully (no OWN
					// error) while a DIFFERENT member of its own module
					// cycle errored later, asynchronously, after `unit`'s
					// own body already completed - the cycle's ROOT (not
					// `unit`) carries the recorded [[EvaluationError]] in
					// that case. Per spec, Evaluate() redirects to
					// module.[[CycleRoot]] before its own status/error
					// check - a LATER, separate re-import of `unit` must
					// still reject with the cycle root's error (test262
					// import-fulfilled-member-of-errored-cycle.js).
					// getCycleRoot()==null only before this unit's own
					// linkModule() has ever run at all - unreachable here,
					// since a cached, non-ERRORED, non-pending-deferred
					// unit already finished (or is mid-)  evaluating, which
					// always happens after its own SCC has closed.
					JSInterpretedUnit cycleRoot = unit.getCycleRoot();
					if(cycleRoot!=null && cycleRoot!=unit && cycleRoot.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
						throw RuntimeUtil.wrap(cycleRoot.getEvaluationError());
					}
					// A cached-but-not-yet-EVALUATED entry can only come
					// from a prior `import defer` of this same path (see
					// importDeferredNamespace()'s own doc comment) - an
					// ORDINARY (non-deferred) import reaching it now must
					// force evaluation immediately, same as if it had
					// loaded the module itself from scratch.
					if(unit.isPendingDeferredEvaluation()) {
						unit.initModule(this, false);
					}
				}
				return m;
			}
		}

		List<JSModuleResolver> moduleResolvers = getEnvironment().getModuleResolvers();
		if(moduleResolvers!=null) {
			for(JSModuleResolver moduleResolver: moduleResolvers) {
				// earlyRegister: cache the module as soon as it's
				// CONSTRUCTED, before its own top-level body runs - a
				// self-/circular-import reached from within that body then
				// finds this same instance here instead of recursing into
				// loading a second one from scratch (see
				// JSModuleDescriptor.loadModule()'s own doc comment).
				JSModule m;
				try {
					m = moduleResolver.loadModule(unitContext.getGlobalContext(),resolvedName, (registered) -> {
						if(modules==null) {
							modules = new HashMap<>();
						}
						modules.put(resolvedName,registered);
					});
				} catch(RuntimeException ex) {
					// A module whose OWN top-level body throws must stay a
					// permanent failure. For a JSInterpretedUnit,
					// ModuleStatus.ERRORED/evaluationError (set inside
					// JSInterpretedUnit.executeWithContext()) already give
					// it a durable, SAME-identity failure marker - keep it
					// cached so every subsequent lookup (this method's own
					// cache-hit branch above, or DeferredModuleView.real())
					// re-throws the EXACT SAME error object every time
					// (test262 import-defer/errors/module-throws/defer-
					// import-after-evaluation.js requires this - evicting
					// and re-running from scratch would construct a brand
					// new, non-identical error value each time). Any other
					// module kind (no ModuleStatus tracking) falls back to
					// the OLD behavior: evict so the next lookup re-
					// attempts (and re-fails) instead of returning that
					// broken instance as if it were valid (confirmed via
					// test262's own import-errored-module.js: importing an
					// already-errored module a SECOND time must reject too
					// - satisfied either way, but only evict-and-retry is
					// verified safe for these other module kinds).
					if(modules!=null) {
						JSModule cached = modules.get(resolvedName);
						boolean erroredTranspiled = cached instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu
								&& tu.getEvalStatus()==org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.ModuleEvalStatus.ERRORED;
						if(!(cached instanceof JSInterpretedUnit) && !erroredTranspiled) {
							modules.remove(resolvedName);
						}
					}
					throw ex;
				}
				if(m!=null) {
					if(modules==null) {
						modules = new HashMap<>();
					}
					modules.put(resolvedName,m);
					return m;
				}
			}
		}
		throw RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
	}

	// Import/export attributes (`with { type: "json"/"text" }`) - see
	// JSGlobalContext.importAttributedModule()'s own doc comment for why
	// this is a separate method/cache from importModule() above. Cached
	// under a key that includes the TYPE (not just the resolved path), so
	// the exact same path imported both attributed and un-attributed (or
	// with two DIFFERENT types) never collide - matching spec's "distinct
	// Module Record per distinct attribute set" model (only "type"
	// participates in GaltaJS's own cache key/support, since it's the
	// only attribute that affects module semantics here - see
	// getSupportedImportAttributeType()'s own doc comment for the rest).
	@Override
	public JSModule importAttributedModule(JSUnitContext unitContext, String name, Map<String,String> attributes) {
		String type = attributes==null ? null : attributes.get("type");
		if(type==null) {
			// No "type" attribute at all - not an ATTRIBUTED import in any
			// sense GaltaJS gives special meaning to (other attribute keys,
			// with no "type", are inert - see ASTImpExp.getAttributes()'s
			// own doc comment), so this is really just an ordinary module
			// import that happened to carry a (functionally empty) with
			// clause - test262 import-attribute-empty.js's own idiom.
			return importModule(unitContext, name);
		}
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		String cacheKey = "attr:" + type + ":" + resolvedName;
		if(modules!=null) {
			JSModule m = modules.get(cacheKey);
			if(m!=null) {
				return m;
			}
		}
		org.monflabs.galtajs.JSModuleDescriptor descriptor = findModuleDescriptor(resolvedName);
		Object defaultValue = RuntimeUtil.parseAttributedModuleContent(getEnvironment(), type, descriptor, resolvedName);
		JSModule m = new org.monflabs.galtajs.modules.JSNativeModule(getEnvironment(), descriptor, defaultValue, null);
		if(modules==null) {
			modules = new HashMap<>();
		}
		modules.put(cacheKey, m);
		return m;
	}

	// Source-phase import - see JSGlobalContext.importModuleSource()'s own
	// doc comment. A separate cache from `modules` above: a Module Source
	// Object is not a module record, and obtaining it must never load or
	// evaluate the module.
	private final Map<String,org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource> moduleSources = new HashMap<>();
	@Override
	public org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource importModuleSource(JSUnitContext unitContext, String name) {
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		return org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource.resolve(getEnvironment(), moduleSources, resolvedName);
	}

	// `import defer * as ns from '...'` - see JSGlobalContext.
	// importDeferredNamespace()'s own doc comment for the general
	// contract. Cached under the SAME key (resolvedName) importModule()
	// itself uses - a deferred and a later ordinary import of the SAME
	// path must resolve to the SAME Module Record (spec: one Module
	// Record per resolved specifier, regardless of how it's reached) -
	// importModule() above already knows to force-evaluate a cached
	// entry it finds still uninitialized.
	@Override
	public JSObject importDeferredNamespace(JSUnitContext unitContext, String name, Map<String,String> attributes) {
		String type = attributes==null ? null : attributes.get("type");
		if(type!=null) {
			// `import defer * as ns from '...json' with {type:'json'}` - a
			// JSON/text synthetic module (importAttributedModule()) is
			// already fully "evaluated" (parsed) the instant it's
			// constructed - there's no lazy body left to defer, but the
			// namespace object's own toStringTag must still read
			// "Deferred Module" (test262 import-defer/deferred-namespace-
			// object/json-module.js) - use the SAME cached-deferred-slot
			// mechanism as an interpreted module, just with the module
			// acting as its own (already-real, non-lazy) "deferred view".
			JSModule m = importAttributedModule(unitContext, name, attributes);
			if(m instanceof org.monflabs.galtajs.modules.AbstractModule am) {
				return (JSObject)am.getDeferredModuleNamespaceObject(m);
			}
			return (JSObject)m.getModuleNamespaceObject();
		}
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		JSModule cached = modules!=null ? modules.get(resolvedName) : null;
		JSInterpretedUnit unit;
		if(cached instanceof JSInterpretedUnit u) {
			unit = u;
		} else if(cached!=null) {
			// A non-interpreted module (native, or a JSON/text synthetic
			// module - see importAttributedModule() above) is already
			// fully "evaluated" the moment it's constructed - nothing left
			// to defer, just its ordinary namespace object.
			return (JSObject)cached.getModuleNamespaceObject();
		} else {
			// Not loaded at all yet - parse it now (so a genuine SYNTAX
			// error in the deferred module surfaces immediately, at this
			// import statement's own position - test262 import-defer/
			// errors/syntax-error.js), but do NOT call initModule() -
			// leaving the body unevaluated is the entire point.
			org.monflabs.galtajs.JSModuleDescriptor descriptor = findModuleDescriptor(resolvedName);
			unit = getEnvironment().createScript(descriptor.getScript(), resolvedName, JSEnvironment.SCRIPT_MODULE);
			unit.markPendingDeferredEvaluation();
			if(modules==null) {
				modules = new HashMap<>();
			}
			modules.put(resolvedName, unit);
			// Spec: `import defer` still requires its target's own module
			// graph to be fully LINKED (every import/export-from specifier
			// resolvable) even though EVALUATION is deferred - a
			// resolution failure must surface now, not silently wait for
			// first access (test262 import-defer/errors/resolution-error/
			// import-defer-of-missing-module-fails.js). Only checks this
			// unit's own DIRECT specifiers (one level deep, not the whole
			// transitive graph - see getAllModuleRequests()'s own doc
			// comment for why that's a known, narrower limitation).
			for(String req: unit.getProgram().getAllModuleRequests()) {
				String depResolvedName = ModuleUtil.resolvePath(resolvedName, req);
				if(modules!=null && modules.containsKey(depResolvedName)) {
					continue;
				}
				boolean found = false;
				List<JSModuleResolver> resolvers = getEnvironment().getModuleResolvers();
				if(resolvers!=null) {
					for(JSModuleResolver r: resolvers) {
						if(r.getModule(depResolvedName)!=null) {
							found = true;
							break;
						}
					}
				}
				if(!found) {
					modules.remove(resolvedName);
					throw RuntimeUtil.typeError("Cannot find module {0}", depResolvedName);
				}
			}
		}
		JSInterpretedUnit finalUnit = unit;
		JSModule deferredView = new DeferredModuleView(finalUnit, this);
		return finalUnit.getDeferredModuleNamespaceObject(deferredView);
	}

	// GatherAsynchronousTransitiveDependencies (spec, `import.defer`
	// proposal) - see RuntimeUtil.dynamicImportDefer()'s own doc comment
	// for why this exists: `import.defer(specifier)` must NOT stay lazy
	// for a target (or any transitively-reachable, not-yet-resolved
	// dependency) that has its own top-level await - unlike synchronous
	// deferred evaluation, an async module's own completion can't be
	// produced on-demand from a plain property access, so it must be
	// evaluated NOW, eagerly, before the `import.defer()` promise
	// resolves. Called AFTER importDeferredNamespace() has already
	// resolved/cached (and, for a fresh target, marked pending-deferred)
	// the top-level specifier - this walk may find that SAME cached unit
	// again (fine; its own [[HasTLA]] is just read off the already-parsed
	// AST, no re-parsing) or discover BRAND NEW nested dependencies not
	// reached by any other import yet, which get parsed and cached here
	// (but never marked pending-deferred - only the ORIGINAL `import
	// defer`/`import.defer()` target itself gets that treatment; a
	// transitive dependency is just an ordinary module, evaluated either
	// eagerly right now (if gathered here) or later, normally, whenever
	// the target's own eventual initModule() call reaches it).
	@Override
	public List<JSInterpretedUnit> gatherAsynchronousTransitiveDependencies(JSUnitContext unitContext, String name) {
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		List<JSInterpretedUnit> result = new java.util.ArrayList<>();
		gatherAsynchronousTransitiveDependencies(resolvedName, new java.util.HashSet<>(), result);
		return result;
	}
	private void gatherAsynchronousTransitiveDependencies(String resolvedName, java.util.Set<String> seen, List<JSInterpretedUnit> result) {
		if(!seen.add(resolvedName)) {
			return;
		}
		JSModule cached = modules!=null ? modules.get(resolvedName) : null;
		JSInterpretedUnit unit;
		if(cached instanceof JSInterpretedUnit u) {
			// Spec step 6: a module already evaluating (reached via some
			// OTHER, concurrent load) or fully evaluated (IsModuleSCCEvaluated
			// - see JSInterpretedUnit.getCycleRootOrSelf()'s own doc comment
			// for why this redirects through the module's own cycle root
			// rather than checking its own status directly, same as
			// JSInterpretedUnit.readyForSyncExecution()'s identical fix)
			// has nothing left to gather - either it already settled, or
			// whoever is already evaluating it owns that responsibility.
			// "Already evaluating" here means spec's real EVALUATING status
			// specifically (genuinely still on the call stack right now) -
			// NOT evaluating-ASYNC (suspended mid-body, e.g. mid-TLA),
			// which GaltaJS's single EVALUATING enum value also covers but
			// spec step 6 does NOT exclude - a suspended module with its
			// own [[HasTLA]] is exactly the case that needs to be gathered
			// (test262 async-cycle-dependency-of-deferred-module.js: A is
			// suspended mid-`await blocker.promise`, must still be found
			// and added below), so isAsyncEvaluation() must NOT short-
			// circuit this check.
			if((u.getModuleStatus()==JSInterpretedUnit.ModuleStatus.EVALUATING && !u.isAsyncEvaluation())
					|| u.getCycleRootOrSelf().getModuleStatus()==JSInterpretedUnit.ModuleStatus.EVALUATED) {
				return;
			}
			unit = u;
		} else if(cached!=null) {
			// Spec step 5: "If module is not a Cyclic Module Record,
			// return result" - a native/JSON/text synthetic module is
			// already fully realized the instant it's constructed, never
			// asynchronous.
			return;
		} else {
			org.monflabs.galtajs.JSModuleDescriptor descriptor = findModuleDescriptor(resolvedName);
			unit = getEnvironment().createScript(descriptor.getScript(), resolvedName, JSEnvironment.SCRIPT_MODULE);
			// Same "cache it but leave it unevaluated" marking
			// importDeferredNamespace() gives the TOP-level target - a
			// dependency discovered HERE, for structural walking only
			// (checking ITS OWN isAsyncExecution()/further requests), is
			// otherwise left cached with NOTHING to signal "still needs a
			// real evaluation" to a LATER, ordinary importModule() cache
			// hit (which only force-evaluates a cached entry when this
			// flag is set) - silently leaving it permanently unevaluated,
			// its body never running at all, once something genuinely
			// tries to import it for real (test262 import-defer/sync/
			// main.js: a purely-synchronous graph reachable only through
			// gathering, never itself gathered since it has no TLA
			// anywhere, still needs its OWN later, ordinary evaluation -
			// on the deferred target's own first access - to actually
			// run).
			unit.markPendingDeferredEvaluation();
			if(modules==null) {
				modules = new HashMap<>();
			}
			modules.put(resolvedName, unit);
		}
		if(unit.getProgram().isAsyncExecution()) {
			result.add(unit);
			return;
		}
		for(String req: unit.getProgram().getAllModuleRequests()) {
			gatherAsynchronousTransitiveDependencies(ModuleUtil.resolvePath(resolvedName, req), seen, result);
		}
	}

	// Delegates every JSModule method to the real, underlying
	// JSInterpretedUnit - but forces its evaluation (initModule(), exactly
	// once - JSInterpretedUnit.initModule() itself throws if called
	// twice, so isModuleInitialized() guards that) the FIRST time any of
	// them is actually invoked. This is the object a deferred namespace's
	// own trap methods (ModuleNamespaceObject.resolveExportValue() etc.,
	// via getExportAccessor()/getNamedExports()) end up calling into -
	// merely CONSTRUCTING this view, or the ModuleNamespaceObject wrapping
	// it, never triggers evaluation; only a genuine property access does.
	private static final class DeferredModuleView implements JSModule {
		private final JSInterpretedUnit unit;
		private final JSGlobalContext globalContext;
		private DeferredModuleView(JSInterpretedUnit unit, JSGlobalContext globalContext) {
			this.unit = unit;
			this.globalContext = globalContext;
		}
		private JSInterpretedUnit real() {
			// Spec EnsureDeferredNamespaceEvaluation (10.4.6.14) - see
			// JSInterpretedUnit.ModuleStatus's own doc comment for the
			// full contract.
			globalContext.ensureModuleEvaluated(unit, globalContext);
			return unit;
		}
		@Override public JSEnvironment getEnvironment() { return unit.getEnvironment(); }
		@Override public org.monflabs.galtajs.JSModuleDescriptor getDescriptor() { return unit.getDescriptor(); }
		@Override public Object getDefaultExport() { return real().getDefaultExport(); }
		@Override public void setDefaultExport(Object defaultExport) { real().setDefaultExport(defaultExport); }
		@Override public boolean hasDefaultExport() { return real().hasDefaultExport(); }
		@Override public JSObject getNamedExports() { return real().getNamedExports(); }
		@Override public void addNamedExports(String key, Object value) { real().addNamedExports(key, value); }
		@Override public JSObject ensureNamedExports() { return real().ensureNamedExports(); }
		@Override public void addStarReExport(String key, Object value, JSModule source) { real().addStarReExport(key, value, source); }
		@Override public void addNamedReExport(String exportedName, JSModule source, String sourceName) { real().addNamedReExport(exportedName, source, sourceName); }
		@Override public JSObject getModuleNamespaceObject() { return real().getModuleNamespaceObject(); }
		@Override public Object getExport(String name) { return real().getExport(name); }
		@Override public VarAccessor getExportAccessor(String name) { return real().getExportAccessor(name); }
		@Override public VarAccessor getLiveDefaultExportAccessor() { return real().getLiveDefaultExportAccessor(); }
		@Override public java.util.Set<String> getExportedNames(java.util.Set<JSModule> exportStarSet) { return real().getExportedNames(exportStarSet); }
		@Override public ResolvedBinding resolveExport(String name, java.util.Set<ResolveKey> resolveSet) { return real().resolveExport(name, resolveSet); }
	}

	// Spec EnsureDeferredNamespaceEvaluation (10.4.6.14): forces `unit` to
	// finish evaluating synchronously, right now, if it hasn't already -
	// shared by DeferredModuleView.real() (an `import defer`/`import.
	// defer()` namespace's own property-access trigger) AND ASTImport.
	// hoistBindings()'s eager-async-dependency branch (which must trigger
	// evaluation of JUST the modules GatherAsynchronousTransitiveDependencies
	// found, not the whole deferred target - see that call site's own doc
	// comment), so this logic isn't duplicated between the two.
	@Override
	public void ensureModuleEvaluated(JSModule module, JSGlobalContext globalContext) {
		// This class is the interpreted backend - a JSTranspiledUnit target
		// is handled by TranspiledGlobalRuntimeContext's own override
		// instead (see its own doc comment); not expected to reach here at
		// all, but no-op rather than throw, matching the interface's own
		// default.
		if(!(module instanceof JSInterpretedUnit unit)) {
			return;
		}
		if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
			// Re-throw the SAME (by identity) error every time - never
			// re-attempt evaluation of an already-failed module (test262
			// import-defer/errors/module-throws/*.js).
			throw RuntimeUtil.wrap(unit.getEvaluationError());
		}
		if(unit.getModuleStatus()!=JSInterpretedUnit.ModuleStatus.EVALUATED) {
			if(!unit.readyForSyncExecution(new java.util.HashSet<>(), globalContext)) {
				throw RuntimeUtil.typeError("Cannot synchronously evaluate module {0}: it is already being evaluated", unit.getDescriptor().getName());
			}
			if(unit.isPendingDeferredEvaluation()) {
				unit.initModule(globalContext, false);
			}
		}
	}

	// Starts a gathered-but-not-yet-started async dependency running, the
	// same way an ordinary eager import request would (spec's
	// InnerModuleEvaluation just adds these to the same evaluationList as
	// any other requested module - no synchronous-readiness gate at all).
	// Deliberately NOT ensureModuleEvaluated() above: that method's
	// readyForSyncExecution() check is UNCONDITIONALLY false for any
	// module with top-level await (by design - see that method's own doc
	// comment, mirroring spec step 8's unconditional "if HasTLA, return
	// false") - correct for a genuine synchronous-access-must-succeed-or-
	// throw trigger (a deferred namespace's own property access), but
	// wrong here: a freshly-gathered TLA module is expected to just START
	// (and asynchronously suspend on its own await, settling later,
	// normally) - not be required to already be synchronously complete.
	@Override
	public void startAsyncDependencyEvaluation(JSModule module, JSGlobalContext globalContext) {
		if(!(module instanceof JSInterpretedUnit unit)) {
			return;
		}
		if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
			throw RuntimeUtil.wrap(unit.getEvaluationError());
		}
		if(unit.isPendingDeferredEvaluation()) {
			unit.initModule(globalContext, false);
		}
	}

	// A module executed as the ROOT of a run (the file directly handed to
	// JSScriptExecutor/a test harness, never itself reached via
	// importModule()) is otherwise NEVER entered into `modules` above - only
	// a module loaded AS A DEPENDENCY gets that, via loadModule()'s own
	// earlyRegister callback. So a self-import from the root file itself
	// (`import {x} from './this-same-file.js'`, a common test262 idiom)
	// previously found nothing cached under its own resolved name and fell
	// through to loadModule(), which re-parses and independently re-executes
	// the SAME source as a completely separate JSInterpretedUnit/module
	// graph - never truly self-referencing (confirmed via instn-named-
	// bndng-var.js: importing "x" this way returned the DUPLICATE unit's
	// already-fully-evaluated x, not a live view of the root's own x as it
	// evaluates). Called unconditionally by JSInterpretedUnit.
	// executeWithContext() whenever program.isModule() is true - harmless
	// (a putIfAbsent no-op) for a module that already reached here via
	// initModule()/loadModule()'s own earlyRegister, since that path
	// registers the SAME instance under the SAME resolved name first.
	@Override
	public void registerRootModule(JSModule module) {
		String resolvedName = module.getDescriptor().getName();
		if(modules==null) {
			modules = new HashMap<>();
		}
		modules.putIfAbsent(resolvedName, module);
	}

	// Pure cache lookup for JSInterpretedUnit.readyForSyncExecution()'s
	// dependency walk on a module that hasn't started executing yet - see
	// its own call site's doc comment for why this must NEVER load/
	// evaluate anything, unlike importModule() above. Resolves `name`
	// relative to `self`'s own path exactly like importModule() does, but
	// only ever returns whatever's already cached (or null).
	@Override
	public JSModule peekModule(JSModule self, String name) {
		String resolvedName = ModuleUtil.resolvePath(self.getDescriptor().getName(), name);
		return modules==null ? null : modules.get(resolvedName);
	}

	// The descriptor of a module, from the first resolver that knows it
	private org.monflabs.galtajs.JSModuleDescriptor findModuleDescriptor(String resolvedName) {
		List<JSModuleResolver> resolvers = getEnvironment().getModuleResolvers();
		if(resolvers!=null) {
			for(JSModuleResolver r: resolvers) {
				org.monflabs.galtajs.JSModuleDescriptor descriptor = r.getModule(resolvedName);
				if(descriptor!=null) {
					return descriptor;
				}
			}
		}
		throw RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
	}
}
