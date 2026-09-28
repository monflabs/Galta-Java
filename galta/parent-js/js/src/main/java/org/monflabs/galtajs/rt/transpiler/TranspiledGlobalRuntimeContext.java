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
package org.monflabs.galtajs.rt.transpiler;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObject.DESC_CHECK;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSModuleResolver;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.node.ASTProgram;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.GlobalThis;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.standard.performance.PerformanceData;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;
import org.monflabs.util.Console;

/**
 * Runtime context used by the transpiler.
 */
public class TranspiledGlobalRuntimeContext extends TranspiledUnitRuntimeContext implements JSTranspiledGlobalRuntimeContext, org.monflabs.galtajs.debug.api.impl.Debuggable {

	private JSExecutor executor;
	
	private GlobalThis globalThis;

	private PrintStream out;
	private PrintStream err;

	protected Map<String, Object> runtimeProperties;
	// Written by other threads (a debugger or a UI asking to stop)
	private volatile RunningState runningState;
	private JSScriptUnit scriptUnit;
	// Snapshot of the TOP-LEVEL script/module's own isForceStrictMode(), taken
	// ONCE (see runValue()'s own setOwnForceStrictMode() call, mirroring
	// JSInterpretedUnit's identical setOwnForceStrictMode() pattern for
	// InterpretedGlobalRuntimeContext) - NOT read dynamically from scriptUnit
	// at call time like isStrictMode() used to. scriptUnit itself is a SHARED,
	// MUTABLE field (see its own constructor-arg doc): a direct eval running
	// via the AST-interpreter (JSInterpretedUnit) calls gc.setScriptUnit(this)
	// on THIS SAME global context object while it runs, temporarily
	// overwriting it with the EVAL's OWN unit - correct for the eval's own
	// code, but wrong for isStrictMode() itself, since a top-level sloppy
	// function's own [[ThisMode]] coercion (BuiltinFunctionTranspiler.call())
	// must reflect the FUNCTION's own lexical definition-time strictness, not
	// whatever unit happens to be dynamically executing when it's LATER
	// called from within a nested strict eval's own dynamic extent (test262
	// language/function-code/10.4.3-1-82{-s,gs}.js: a sloppy top-level
	// function called via `eval("f();")` from strict code must still get
	// `this` coerced to globalThis, matching real engines - not left as
	// `undefined`, which was happening because scriptUnit.isForceStrictMode()
	// picked up the eval's OWN unit instead of this script's own).
	private boolean ownForceStrictMode;

	private boolean evalExecution;
	private Object _this;
	
	private Map<String,JSModule> modules;

	public TranspiledGlobalRuntimeContext(JSEnvironment env, JSExecutor executor) {
		this(env,executor,RuntimeUtil.NOT_AVAILABLE);
	}
	public TranspiledGlobalRuntimeContext(JSEnvironment env, JSExecutor executor, Object _this) {
		super(env);
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
	public GlobalThis getGlobalThis() {
		return globalThis;
	}

	@Override
	public Object getThis() {
		return _this;
	}

	// JSRuntimeContext's own default setThis() delegates to getParent() -
	// wrong for a root context with no parent (NPE). Mirrors
	// InterpretedGlobalRuntimeContext.setThis()'s identical override - used
	// by JSInterpretedUnit.executeWithContext() to force a MODULE's own
	// `this` to `undefined`, unconditionally (spec: never the ambient
	// globalThis fallback a plain script gets) - applies here too, since a
	// module's body always executes via JSInterpretedUnit regardless of
	// whether the overall program is running in transpiled mode.
	@Override
	public void setThis(Object _this) {
		this._this = _this;
	}
	
	private org.monflabs.galtajs.debug.api.impl.DebugHook debugHook;

	@Override
	public org.monflabs.galtajs.debug.api.impl.DebugHook getDebugHook() {
		return debugHook;
	}
	@Override
	public void setDebugHook(org.monflabs.galtajs.debug.api.impl.DebugHook debugHook) {
		this.debugHook = debugHook;
	}

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
	
	
	/////////////////////////////////////////////////////////////////////////
	// Global context
	/////////////////////////////////////////////////////////////////////////
	

	private PerformanceData performanceData;
	
	@Override
	public PerformanceData getPerformanceData() {
		if (performanceData == null) {
			performanceData = new PerformanceData(getEnvironment());
		}
		return performanceData;
	}	
	
	@Override
	public void checkInterrupted() {
		if(runningState==RunningState.STOPPING) {
			throw new JSRuntimeInterruptException();
		}
	}
	
	
	@Override
	public Object getProperty(String name) {
		if (runtimeProperties != null) {
			return runtimeProperties.get(name);
		}
		return null;
	}

	@Override
	public TranspiledGlobalRuntimeContext putProperty(String name, Object value) {
		if (runtimeProperties == null) {
			runtimeProperties = new HashMap<String, Object>();
		}
		runtimeProperties.put(name, value);
		return this;
	}

	@Override
	public TranspiledGlobalRuntimeContext removeProperty(String name) {
		if (runtimeProperties != null) {
			runtimeProperties.remove(name);
		}
		return this;
	}

	
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
		return Console.outStream();
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


	// A top-level SCRIPT's own `var`/function declarations are transpiled as
	// a local Java array slot, never written directly to `globalThis`'s own
	// storage (see ASTVarContainer's "global" branch, which calls THIS
	// method) - so `globalThis.v`/sloppy top-level `this.v` (an explicit
	// MEMBER-ACCESS read, a completely different code path from ordinary
	// identifier resolution) couldn't find it. Bridged by handing
	// `globalThis` a reference to the SAME VariableMap super's own
	// initGlobalVariables() just populated - see GlobalThis.
	// bindScriptVariables()'s own doc for the full rationale and why this
	// is additive/zero-cost for every object other than globalThis itself.
	@Override
	public void initGlobalVariables(Object[] variables, String[] varNames, String[] varTypes, boolean[] annexBBlockHoisted) {
		// GlobalDeclarationInstantiation's upfront collision/extensibility
		// validation (ASTProgram.validateGlobalDeclarations() - see its own
		// doc comment for the full spec mapping) MUST run here, BEFORE
		// super.initGlobalVariables() below populates the variable map and
		// before bindScriptVariables() bridges those entries onto
		// `globalThis` as real own properties. Doing it after either would
		// make the script's OWN not-yet-declared names already appear as
		// existing bindings/properties by the time validation ran, so every
		// declaration would spuriously collide with itself - re-derived
		// empirically (not assumed) per KnownGaps.md's "Global-code
		// semantics" entry, which also documents the EARLIER, unrelated
		// `hasOwnProperty()`-proxy attempt that regressed 45 tests. At this
		// point `context.getVariableMap(true)`/`globalThis` still only
		// reflect state from BEFORE this script (e.g. a prior script sharing
		// this same realm), exactly matching interpreted mode's ordering in
		// ASTProgram.evaluate().
		if(varNames!=null) {
			ASTProgram.validateGlobalDeclarations(this, globalThis, varNames, varTypes);
		}
		// Per-name reconciliation with any PRE-EXISTING real globalThis own
		// property (e.g. via Object.defineProperty(this, ...) before this
		// script's own var/function hoisting runs) - evaluated here, BEFORE
		// any of the loop below's own writes, for the exact same "must see
		// only prior-script state" reason validateGlobalDeclarations() above
		// already relies on. The blanket VariableMap.initVariable() call
		// this loop otherwise makes for every name "doesn't check" anything
		// (see its own doc) - it never looks at globalThis's real
		// own-property storage at all, which silently diverges from spec
		// for a name that's ALSO already a real property:
		//  - FUNCTION: CreateGlobalFunctionBinding (8.1.1.4.17) unconditionally
		//    redefines an EXISTING property back to the standard
		//    writable/enumerable/configurable:false data-property shape (or
		//    leaves it, if non-configurable) - createVariable()'s FUNCTION
		//    branch already implements exactly this (reachable via the
		//    direct-eval path); reused verbatim here instead of duplicating
		//    it, so top-level/evalScript function hoisting also redefines a
		//    pre-existing property's attributes/value correctly (test262
		//    language/global-code/script-decl-func.js's `configurable` case).
		//  - VAR: CreateGlobalVarBinding (8.1.1.4.16) on an existing property
		//    is a pure no-op - nothing writes to it - but the array-slot
		//    VariableMap entry initVariable() would
		//    otherwise ALSO create for the same name becomes a second,
		//    orphaned "own property" once the real one is later deleted
		//    (GlobalThis.deleteProperty() only removes the real,
		//    checked-first, JSObjectImpl-stored one) - findScriptOwnAccessor()/
		//    hasOwnProperty() then still finds THAT leftover entry and
		//    reports true (test262 script-decl-var.js's `configurable` case:
		//    delete succeeds against the real property, but hasOwnProperty
		//    afterward still finds the array-slot entry, so isConfigurable()
		//    wrongly returns false). Skipping the array-slot entry entirely
		//    for an already-existing name avoids that second storage ever
		//    coming into being - identifier resolution for such a name, when
		//    not statically known to the CURRENT script's own compiled index
		//    (the common case: the name was declared by a separately-
		//    compiled $262.evalScript body), already correctly falls through
		//    to globalThis's real property via getIdentifierValue()/
		//    getIdentifierAccessor()'s existing globalThis fallback.
		VariableMap globalVars = getVariableMap(true);
		if(varNames!=null) {
			int l = varNames.length;
			for(int i=0; i<l; i++) {
				VAR_TYPE t = (varTypes!=null && varTypes[i]!=null) ? VAR_TYPE.valueOf(varTypes[i]) : VAR_TYPE.AUTO;
				if(t==VAR_TYPE.FUNCTION) {
					// FUNCTION's actual value isn't computed yet at this point
					// (the hoisted `variables[i] = new FnClass(...)` assignment
					// - see ASTFunctionDecl.transpileJavaStatement() - runs
					// later, as part of the statement list this method's own
					// caller emits BEFORE, per spec order, but AFTER this call
					// in the generated Java) - createVariable() here reconciles
					// the real property's descriptor SHAPE only (using
					// whatever placeholder value is in `variables[i]` right
					// now; harmless since CreateGlobalFunctionBinding's
					// redefined descriptor isn't checked for `value` by
					// verifyProperty() the way VAR names are). The array-slot
					// entry below is what ends up holding - and later
					// exposing, once the hoisted assignment runs - the REAL
					// function value, exactly as before this fix; the real
					// property's own (possibly stale) value field is
					// unreachable as long as it stays shadowed by the
					// non-configurable descriptor set here (`super.
					// getOwnPropertyDescriptor` finds it first).
					// EXCEPT for an Annex-B block-hoisted candidate (mirrors
					// ASTProgram.hoistDeclarations()'s identical interpreted-
					// mode `createType = ... ? VAR_TYPE.VAR : t` swap): B.3.3.3's
					// own synchronization step is CreateGlobalVarBinding, which
					// is a pure no-op when the property already exists - it must
					// NEVER redefine the descriptor here (test262 annexB/
					// language/global-code/*-existing-non-enumerable-global-
					// init.js). The array-slot bridging below still runs
					// unconditionally either way, exactly as before - skipping
					// IT too (treating this like the plain VAR branch) would
					// leave the array slot never bridged into globalThis at all,
					// so the later hoisted assignment's real value would stay
					// permanently unreachable through ordinary identifier
					// resolution (confirmed via a regression while developing
					// this fix).
					boolean isAnnexB = annexBBlockHoisted!=null && annexBBlockHoisted[i];
					if(globalThis.getOwnPropertyDescriptor(varNames[i])!=null && !isAnnexB) {
						createVariable(varNames[i], variables[i], t);
					}
					globalVars.initVariable(varNames[i], variables, i, t);
				} else if((t==VAR_TYPE.VAR || t==VAR_TYPE.AUTO) && globalThis.getOwnPropertyDescriptor(varNames[i])!=null) {
					// Intentionally no initVariable() call - see comment above.
				} else {
					globalVars.initVariable(varNames[i], variables, i, t);
				}
			}
		}
		if(varNames!=null) {
			globalThis.bindScriptVariables(getVariableMap(true));
		}
	}

	//
	// Global var/function hoisting
	//
	// Mirrors InterpretedGlobalRuntimeContext's identical overrides (see its
	// own comments for the full rationale): top-level var/function
	// declarations must be visible as real own properties of globalThis, not
	// just resolvable as identifiers. Missing here meant eval() reached
	// through a transpiled caller (StandardLibrary's eval builtin always
	// executes the eval'd source via the shared AST-interpreter, but routes
	// var/function hoisting back through THIS context's generic
	// AbstractRuntimeContext.createVariable(), which only writes to the
	// local VariableMap) silently never created the corresponding globalThis
	// property at all - confirmed via test262's annexB/language/eval-code
	// and language/eval-code global-function-hoisting-via-eval suites
	// ("obj should have an own property f"-style assertions). Ordinary
	// (non-eval) top-level transpiled globals are unaffected - the generated
	// Java codegen resolves them directly against getGlobalThis() (see
	// JSTranspiledUnit.getIdentifierAccessor()/getIdentifierValue()),
	// bypassing createVariable() entirely.
	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type) {
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

	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type, boolean configurable) {
		return createVariable(varName, value, type);
	}

	@Override
	public VarAccessor resolveOwnIdentifierEntry(String varName) {
		VarAccessor a = globalThis.getOwnVariableAccessor(varName, false);
		if(a!=null) {
			// PutValue/SetMutableBinding's "stillExists" re-check (9.1.1.2.5):
			// mirrors JSTranspiledUnit.getIdentifierAccessor(context,varName,
			// isPlainAssignment)'s identical wrapping - this is a SEPARATE
			// path to the same globalThis-backed accessor, reached when a
			// deeply-nested closure resolves a free identifier by walking up
			// the runtime context chain (context.getVariableEntry(...)) all
			// the way to this top-level context, rather than going through
			// JSTranspiledUnit's own generated-code call site - test262
			// assignment-operator-calls-putvalue-lref--rval--1.js/
			// S11.13.1_A6_T2.js: `x = (delete global.x, 2)` inside a nested
			// strict arrow function must throw ReferenceError once the RHS's
			// own side effect deletes `x` out from under the assignment.
			VarAccessor global = a;
			return new VarAccessor() {
				@Override
				public String getKey() {
					return global.getKey();
				}
				@Override
				public Object getValue() {
					return global.getValue();
				}
				@Override
				public Object setValue(Object value) {
					if(RuntimeUtil.isStrictMode() && !global.stillExists()) {
						throw RuntimeUtil.referenceError("{0} is not defined", varName);
					}
					return global.setValue(value);
				}
				@Override
				public boolean stillExists() {
					return global.stillExists();
				}
			};
		}
		return super.resolveOwnIdentifierEntry(varName);
	}

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


	/////////////////////////////////////////////////////////////////////////
	// Module Resolver
	/////////////////////////////////////////////////////////////////////////

	// Mirrors InterpretedGlobalRuntimeContext.importModule() - module bodies
	// always run via JSInterpretedUnit regardless of whether the IMPORTING
	// host program is transpiled (modules aren't ahead-of-time transpiled
	// per file), so this class needs the exact same early-registration/
	// ERRORED/cycleRoot/deferred-evaluation handling, not a simpler,
	// independently-drifted copy. The previous version here called the
	// resolver's 2-arg loadModule() (no earlyRegister callback), so a
	// self-/circular-import reached from within a module's own body never
	// found its own already-registered-but-still-loading instance and
	// recursed into loading a second one from scratch - infinite recursion,
	// StackOverflowError (confirmed via a full test262 sweep: 606
	// transpiled-mode-only failures, dominated by this exact recursion,
	// isolated-reproducible via language/module-code alone).
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
					if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
						throw RuntimeUtil.wrap(unit.getEvaluationError());
					}
					JSInterpretedUnit cycleRoot = unit.getCycleRoot();
					if(cycleRoot!=null && cycleRoot!=unit && cycleRoot.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
						throw RuntimeUtil.wrap(cycleRoot.getEvaluationError());
					}
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
				JSModule m;
				try {
					m = moduleResolver.loadModule(unitContext.getGlobalContext(),resolvedName, (registered) -> {
						if(modules==null) {
							modules = new HashMap<>();
						}
						modules.put(resolvedName,registered);
					});
				} catch(RuntimeException ex) {
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

	// Import/export attributes (`with { type: "json"/"text" }`) - ported
	// verbatim from InterpretedGlobalRuntimeContext.importAttributedModule(),
	// see its own doc comment for the full rationale (separate cache/method
	// from importModule() above since an attributed import resolves to a
	// distinct Module Record per spec). Module bodies always run via the
	// same JSInterpretedUnit/JSModuleResolver infrastructure regardless of
	// whether the importing root program is transpiled, so this needs no
	// transpiled-specific logic of its own.
	@Override
	public JSModule importAttributedModule(JSUnitContext unitContext, String name, Map<String,String> attributes) {
		String type = attributes==null ? null : attributes.get("type");
		if(type==null) {
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
		org.monflabs.galtajs.JSModuleDescriptor descriptor = null;
		List<JSModuleResolver> moduleResolvers = getEnvironment().getModuleResolvers();
		if(moduleResolvers!=null) {
			for(JSModuleResolver moduleResolver: moduleResolvers) {
				descriptor = moduleResolver.getModule(resolvedName);
				if(descriptor!=null) {
					break;
				}
			}
		}
		if(descriptor==null) {
			throw RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
		}
		Object defaultValue = RuntimeUtil.parseAttributedModuleContent(getEnvironment(), type, descriptor, resolvedName);
		JSModule m = new org.monflabs.galtajs.modules.JSNativeModule(getEnvironment(), descriptor, defaultValue, null);
		if(modules==null) {
			modules = new HashMap<>();
		}
		modules.put(cacheKey, m);
		return m;
	}

	// Source-phase import - ported from InterpretedGlobalRuntimeContext.
	// importModuleSource(), see JSGlobalContext.importModuleSource()'s own
	// doc comment.
	private final Map<String,org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource> moduleSources = new HashMap<>();
	@Override
	public org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource importModuleSource(JSUnitContext unitContext, String name) {
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		return org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource.resolve(getEnvironment(), moduleSources, resolvedName);
	}

	// `import defer * as ns from '...'` - ported verbatim from
	// InterpretedGlobalRuntimeContext.importDeferredNamespace(), see its own
	// doc comment for the full rationale.
	@Override
	public JSObject importDeferredNamespace(JSUnitContext unitContext, String name, Map<String,String> attributes) {
		String type = attributes==null ? null : attributes.get("type");
		if(type!=null) {
			JSModule m = importAttributedModule(unitContext, name, attributes);
			if(m instanceof org.monflabs.galtajs.modules.AbstractModule am) {
				return (JSObject)am.getDeferredModuleNamespaceObject(m);
			}
			return (JSObject)m.getModuleNamespaceObject();
		}
		String resolvedName = ModuleUtil.resolvePath(unitContext.getScriptUnit().getDescriptor().getName(), name);
		JSModule cached = modules!=null ? modules.get(resolvedName) : null;
		JSModule unit;
		if(cached!=null) {
			// Was previously special-cased to bypass deferred-evaluation
			// wrapping entirely for anything that wasn't a JSInterpretedUnit
			// (returning the ORDINARY, non-deferred namespace object
			// directly) - silently skipping EnsureDeferredNamespaceEvaluation
			// for a cached JSTranspiledUnit target (a self-/sibling-
			// reference to an already-registered transpiled module, see
			// JSGlobalContext.registerRootModule()) and letting `ns.prop`
			// read whatever's there instead of throwing TypeError for a
			// still-evaluating module (test262 import-defer/errors/
			// get-other-while-evaluating.js, get-other-while-dep-evaluating.js).
			// Now wrapped the same way as any other cached module, below.
			unit = cached;
		} else {
			org.monflabs.galtajs.JSModuleDescriptor descriptor = null;
			List<JSModuleResolver> resolvers = getEnvironment().getModuleResolvers();
			if(resolvers!=null) {
				for(JSModuleResolver r: resolvers) {
					descriptor = r.getModule(resolvedName);
					if(descriptor!=null) {
						break;
					}
				}
			}
			if(descriptor==null) {
				throw RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
			}
			JSInterpretedUnit freshUnit = getEnvironment().createScript(descriptor.getScript(), resolvedName, JSEnvironment.SCRIPT_MODULE);
			freshUnit.markPendingDeferredEvaluation();
			if(modules==null) {
				modules = new HashMap<>();
			}
			modules.put(resolvedName, freshUnit);
			for(String req: freshUnit.getProgram().getAllModuleRequests()) {
				String depResolvedName = ModuleUtil.resolvePath(resolvedName, req);
				if(modules!=null && modules.containsKey(depResolvedName)) {
					continue;
				}
				boolean found = false;
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
			unit = freshUnit;
		}
		JSModule finalUnit = unit;
		JSModule deferredView = new DeferredModuleView(finalUnit, this);
		if(finalUnit instanceof org.monflabs.galtajs.modules.AbstractModule am) {
			return (JSObject)am.getDeferredModuleNamespaceObject(deferredView);
		}
		return (JSObject)finalUnit.getModuleNamespaceObject();
	}

	// GatherAsynchronousTransitiveDependencies (import-defer proposal) -
	// ported verbatim from InterpretedGlobalRuntimeContext's identical pair
	// of methods, see their own doc comments for the full rationale.
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
			if((u.getModuleStatus()==JSInterpretedUnit.ModuleStatus.EVALUATING && !u.isAsyncEvaluation())
					|| u.getCycleRootOrSelf().getModuleStatus()==JSInterpretedUnit.ModuleStatus.EVALUATED) {
				return;
			}
			unit = u;
		} else if(cached!=null) {
			return;
		} else {
			org.monflabs.galtajs.JSModuleDescriptor descriptor = null;
			List<JSModuleResolver> resolvers = getEnvironment().getModuleResolvers();
			if(resolvers!=null) {
				for(JSModuleResolver r: resolvers) {
					descriptor = r.getModule(resolvedName);
					if(descriptor!=null) {
						break;
					}
				}
			}
			if(descriptor==null) {
				throw RuntimeUtil.typeError("Cannot find module {0}", resolvedName);
			}
			unit = getEnvironment().createScript(descriptor.getScript(), resolvedName, JSEnvironment.SCRIPT_MODULE);
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

	// Delegates every JSModule method to the real, underlying module
	// (JSInterpretedUnit OR JSTranspiledUnit - see ensureModuleEvaluated()'s
	// own doc comment for how each is checked), forcing its evaluation the
	// first time any of them is actually invoked - the JSInterpretedUnit
	// case is ported verbatim from InterpretedGlobalRuntimeContext's
	// identical inner class (see its own doc comment for the full
	// rationale); generalized to any JSModule so a cached JSTranspiledUnit
	// target gets the same deferred-evaluation check instead of bypassing
	// it (see importDeferredNamespace()'s own doc comment on that fix).
	private static final class DeferredModuleView implements JSModule {
		private final JSModule unit;
		private final JSGlobalContext globalContext;
		private DeferredModuleView(JSModule unit, JSGlobalContext globalContext) {
			this.unit = unit;
			this.globalContext = globalContext;
		}
		private JSModule real() {
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

	// Spec EnsureDeferredNamespaceEvaluation (10.4.6.14) - the
	// JSInterpretedUnit branch is ported verbatim from
	// InterpretedGlobalRuntimeContext.ensureModuleEvaluated() (see its own
	// doc comment for the full rationale); the JSTranspiledUnit branch
	// mirrors it using JSTranspiledUnit's own (narrower - no cycle-root
	// merging) ModuleEvalStatus/readyForSyncExecution() - see that class's
	// own doc comment for the precise scope this covers and doesn't.
	@Override
	public void ensureModuleEvaluated(JSModule module, JSGlobalContext globalContext) {
		if(module instanceof JSInterpretedUnit unit) {
			if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
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
		} else if(module instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu) {
			if(tu.getEvalStatus()==org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.ModuleEvalStatus.ERRORED) {
				throw RuntimeUtil.wrap(tu.getEvaluationError());
			}
			if(tu.getEvalStatus()!=org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.ModuleEvalStatus.EVALUATED) {
				if(!tu.readyForSyncExecution(new java.util.HashSet<>(), globalContext)) {
					throw RuntimeUtil.typeError("Cannot synchronously evaluate module {0}: it is already being evaluated", tu.getDescriptor().getName());
				}
				// No isPendingDeferredEvaluation()/initModule() equivalent
				// needed here - a JSTranspiledUnit reached via this path is
				// already registered/running (a self- or sibling reference
				// to an ALREADY-STARTED module), never a fresh, not-yet-
				// touched one (those are created as a plain
				// JSInterpretedUnit - see importDeferredNamespace()'s own
				// "not cached" branch).
			}
		}
	}

	// Starts a gathered-but-not-yet-started async dependency running - see
	// ensureModuleEvaluated()'s own doc comment for the JSInterpretedUnit-
	// vs-JSTranspiledUnit split rationale.
	@Override
	public void startAsyncDependencyEvaluation(JSModule module, JSGlobalContext globalContext) {
		if(module instanceof JSInterpretedUnit unit) {
			if(unit.getModuleStatus()==JSInterpretedUnit.ModuleStatus.ERRORED) {
				throw RuntimeUtil.wrap(unit.getEvaluationError());
			}
			if(unit.isPendingDeferredEvaluation()) {
				unit.initModule(globalContext, false);
			}
		} else if(module instanceof org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit tu) {
			if(tu.getEvalStatus()==org.monflabs.galtajs.rt.transpiler.JSTranspiledUnit.ModuleEvalStatus.ERRORED) {
				throw RuntimeUtil.wrap(tu.getEvaluationError());
			}
			// No isPendingDeferredEvaluation() equivalent - see
			// ensureModuleEvaluated()'s own identical note above.
		}
	}

	// Pure cache lookup for JSInterpretedUnit.readyForSyncExecution()'s
	// dependency walk - ported verbatim from InterpretedGlobalRuntimeContext.
	// peekModule(), see its own doc comment for why this must never load/
	// evaluate anything.
	@Override
	public JSModule peekModule(JSModule self, String name) {
		String resolvedName = ModuleUtil.resolvePath(self.getDescriptor().getName(), name);
		return modules==null ? null : modules.get(resolvedName);
	}

	// A module executed as the ROOT of a run - ported verbatim from
	// InterpretedGlobalRuntimeContext.registerRootModule(), see
	// JSGlobalContext.registerRootModule()'s own doc comment for why this is
	// needed here too (a module's body always executes via JSInterpretedUnit,
	// regardless of whether the overall program is transpiled).
	@Override
	public void registerRootModule(JSModule module) {
		String resolvedName = module.getDescriptor().getName();
		if(modules==null) {
			modules = new HashMap<>();
		}
		modules.putIfAbsent(resolvedName, module);
	}
}
