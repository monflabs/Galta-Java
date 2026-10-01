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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSArrayImpl;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSObjectImpl;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.modules.ModuleUtil;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSModuleContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeException;
import org.monflabs.galtajs.rt.MemberAssigner;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.RuntimeUtil.HINT;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.builtins.JSAccessor;
import org.monflabs.galtajs.rt.builtins.PropertyDescriptor;
import org.monflabs.galtajs.rt.builtins.WithClosure;
import org.monflabs.galtajs.rt.builtins.primitives.array.BuiltinArray;
import org.monflabs.galtajs.rt.builtins.primitives.object.BuiltinObject;
import org.monflabs.galtajs.rt.builtins.primitives.symbol.Symbol;
import org.monflabs.galtajs.rt.builtins.standard.arguments.Arguments;
import org.monflabs.galtajs.rt.builtins.standard.function.BuiltinClassConstructor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.util.PrimitivePropertyMap;
import org.monflabs.galtajs.rt.util.strings.ConsSequence;
import org.monflabs.util.BaseException;
import org.monflabs.util.Console;
import org.monflabs.util.StringFormat;
import org.monflabs.util.function.TriFunction;
import org.monflabs.util.function.TriPredicate;

/**
 * Base class for a transpiled unit.
 */
public abstract class JSTranspiledUnit extends JSScriptUnit {
	
//	public static int MIN_INT_CACHE = -128;
//	public static int MAX_INT_CACHE = 127;
//	private static Integer[] intCache = new Integer[MAX_INT_CACHE-MIN_INT_CACHE];
//	static {
//		for(int i=MIN_INT_CACHE; i<=MAX_INT_CACHE; i++) {
//			intCache[i] = Integer.valueOf(i);
//		}
//	}
//	public static Integer INT(int i) {
//		if(i>=MIN_INT_CACHE && i<=MAX_INT_CACHE) {
//			return intCache[i-MIN_INT_CACHE];
//		}
//		return Integer.valueOf(i);
//	}
//	public static Long INT(int i) {
//		if(i>=MIN_INT_CACHE && i<=MAX_INT_CACHE) {
//			return intCache[i-MIN_INT_CACHE];
//		}
//		return Integer.valueOf(i);
//	}	
	

	public static final Integer ZERO = Integer.valueOf(0);
	public static final Integer ONE = Integer.valueOf(1);
	public static final Integer MINUS_ONE = Integer.valueOf(-1);

	public static final Double ZERO_DOUBLE = Double.valueOf(0.0);
	public static final Double MINUS_ZERO_DOUBLE = Double.valueOf(-0.0);

	public static final Float ZERO_FLOAT = Float.valueOf(0.0f);
	public static final Float MINUS_ZERO_FLOAT = Float.valueOf(-0.0f);
	
	protected static class Descriptor implements JSModuleDescriptor {
		
		private String name;
		private Class<? extends JSTranspiledUnit> clazz;
		
		public Descriptor(String name, Class<? extends JSTranspiledUnit> clazz) {
			this.name = name;
		}
		
		@Override
		public String getName() {
			return name;
		}

		public Class<? extends JSTranspiledUnit> getTranspiledClass() {
			return clazz;
		}
		
		@Override
		public boolean isScript() {
			return false;
		}
		
		@Override
		public String getScript() {
			throw new JSException(null,"Transpiled Modules don't have source code, yet'{0}'",name);
		}

		@Override
		public JSModule loadModule(JSGlobalContext globalContext) {
			throw new JSException(null,"Transpiled modules cannot be loaded dynamically {0}'",name);
		}
	}
	
	public final class TempVar {
		public Object v;
	}

	private ModuleRuntimeContext moduleContext;
	// Set by runValue() on every entry (root execution AND initModule()'s
	// own dependency-load path routes through it too) - see runValue()'s own
	// doc comment for why resolveModuleForRequest() can't just use
	// moduleContext (only ever non-null for the dependency-load case).
	private JSRuntimeContext activeContext;
	private String sourceCode;
	private JSTranspilerMap transpiledMap;
	
	// For fast access
	protected final JSEnvironment env;
	
	public JSTranspiledUnit(JSEnvironment env, JSModuleDescriptor descriptor) {
		super(env,descriptor);
		this.env = env;
	}

	// Ported verbatim from JSInterpretedUnit.addDefaultExportCallback()/
	// setDefaultExport() - see docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's
	// P3 and that interpreted-mode method's own doc comment for the full
	// rationale. A transpiled self-import reaching a not-yet-executed
	// NON-hoistable default export (`export default class{}`/`export
	// default <expr>;`, positioned AFTER the self-importing `import`
	// statement) needs to register a callback that fires the MOMENT the
	// real setDefaultExport() call runs, instead of reading
	// getDefaultExport() synchronously and observing nothing - see
	// ASTImport.transpileJavaStatement()'s own default-import branch for
	// the registering side.
	private java.util.List<Runnable> defaultExportCallbacks;
	public void addDefaultExportCallback(Runnable r) {
		if(hasDefaultExport()) {
			r.run();
			return;
		}
		if(defaultExportCallbacks==null) {
			defaultExportCallbacks = new java.util.ArrayList<>();
		}
		defaultExportCallbacks.add(r);
	}
	@Override
	public void setDefaultExport(Object defaultExport) {
		super.setDefaultExport(defaultExport);
		if(defaultExportCallbacks!=null) {
			java.util.List<Runnable> callbacks = defaultExportCallbacks;
			defaultExportCallbacks = null;
			for(Runnable cb: callbacks) {
				cb.run();
			}
		}
	}

	// Overridden with a literal `true` by the generated subclass when
	// ASTProgram.isModule() - see JSTranspiler.createIsModuleOption()'s own
	// doc comment. Same "bake a static AST fact as an overridden method"
	// pattern as isForceStrictMode()/isCommonJS() below.
	public boolean isModuleUnit() {
		return false;
	}

	// Spec 16.2.1.6's own Cyclic Module Record [[Status]] - mirrors
	// JSInterpretedUnit.ModuleStatus (see its own doc comment for the
	// general rationale: just enough to implement ReadyForSyncExecution/
	// EnsureDeferredNamespaceEvaluation for import-defer, spec 10.4.6.8/
	// 10.4.6.14), but NARROWER STILL: no cycle-root/SCC merging at all
	// (JSInterpretedUnit's own cycleRoot/DFSIndex machinery, computed by
	// linkModule()'s DFS-based algorithm, is entirely private to that
	// class - genericizing it to also cover JSTranspiledUnit would be a
	// substantial, separate undertaking, see KnownGaps.md). This narrower
	// version only asks "is THIS module's own status EVALUATING/EVALUATED/
	// ERRORED", never "is this module a settled member of a cycle whose
	// ROOT is still pending elsewhere" - correct for a direct self-
	// reference or a simple sibling-dependency chain (test262 import-defer/
	// errors/get-other-while-evaluating.js, get-other-while-dep-evaluating.js),
	// but not for a genuine multi-module SCC where the root differs from
	// the member being checked. Gated to isModuleUnit() at every write site
	// below, mirroring JSInterpretedUnit's own isModule() gating.
	public enum ModuleEvalStatus { UNLINKED, EVALUATING, EVALUATED, ERRORED }
	private ModuleEvalStatus evalStatus = ModuleEvalStatus.UNLINKED;
	// The JS-visible error value (matching what a `catch(e)` in the
	// importing module would see) from this module's own evaluation, set
	// only when evalStatus==ERRORED - mirrors JSInterpretedUnit.
	// evaluationError's own doc comment.
	private Object evaluationError;
	public ModuleEvalStatus getEvalStatus() {
		return evalStatus;
	}
	public Object getEvaluationError() {
		return evaluationError;
	}

	// Baked at transpile time from ASTProgram.getAllModuleRequests() (a
	// pure AST-structure computation - see its own doc comment - safe to
	// call during codegen, not just at runtime) - mirrors JSInterpretedUnit.
	// program.getAllModuleRequests()'s identical role in its own
	// readyForSyncExecution(), just pre-computed into a literal array
	// instead of read from a retained AST (JSTranspiledUnit keeps no AST at
	// runtime at all).
	private String[] allModuleRequests = EMPTY_MODULE_REQUESTS;
	private static final String[] EMPTY_MODULE_REQUESTS = new String[0];
	public void registerModuleRequests(String... requests) {
		this.allModuleRequests = requests;
	}

	// Spec ReadyForSyncExecution (10.4.6.14) - see ModuleEvalStatus's own
	// doc comment for why this is a narrower slice than JSInterpretedUnit.
	// readyForSyncExecution() (no cycle-root merging). `seen` is keyed by
	// JSModule (not JSInterpretedUnit-only) so a dependency chain that
	// passes through a JSInterpretedUnit and back into a JSTranspiledUnit
	// (or vice versa) still terminates correctly; a JSInterpretedUnit
	// dependency itself is checked via ITS OWN (fully general)
	// readyForSyncExecution(), with a fresh seen-set (this narrower
	// version's seen-set isn't type-compatible with that method's
	// Set<JSInterpretedUnit> parameter, and cross-kind cycles aren't the
	// shape this was built to handle precisely anyway).
	public boolean readyForSyncExecution(java.util.Set<JSModule> seen, JSGlobalContext globalContext) {
		if(!seen.add(this)) {
			return true;
		}
		if(evalStatus==ModuleEvalStatus.EVALUATED || evalStatus==ModuleEvalStatus.ERRORED) {
			return true;
		}
		if(evalStatus==ModuleEvalStatus.EVALUATING) {
			return false;
		}
		if(isAsyncExecution()) {
			// [[HasTLA]] - mirrors JSInterpretedUnit's own identical
			// conservative "never sync-ready" treatment for an async
			// module's own body.
			return false;
		}
		for(String req: allModuleRequests) {
			// peekModule() is a pure, non-loading cache lookup - see
			// JSGlobalContext.peekModule()'s own doc comment for why this
			// must never load/evaluate anything (a documented past
			// regression: eagerly evaluating a dependency nobody asked for
			// yet, purely as a side effect of checking readiness).
			JSModule m = globalContext.peekModule(this, req);
			if(m==null) {
				// Never touched - can't be mid-evaluation or part of a
				// cycle (nothing has ever reached it), so optimistically
				// ready - mirrors JSInterpretedUnit.requestedModuleReady()'s
				// identical fallback and its own doc comment for why.
				continue;
			}
			if(m instanceof JSInterpretedUnit iu) {
				if(!iu.readyForSyncExecution(new java.util.HashSet<>(), globalContext)) {
					return false;
				}
			} else if(m instanceof JSTranspiledUnit tu) {
				if(!tu.readyForSyncExecution(seen, globalContext)) {
					return false;
				}
			}
		}
		return true;
	}

	// Static (AST-shape) indirect/star export-entry metadata, hoisted by
	// ASTProgram.transpileJavaStatement() alongside registerLiveExport() -
	// mirrors JSInterpretedUnit.resolveExport()'s own identical algorithm
	// (see its doc comment for the full spec citation: 15.2.1.16.3
	// ResolveExport). Needed because AbstractModule.getExportAccessor()'s
	// generic bookkeeping (namedReExportSources/starExportSources) is only
	// populated as a SIDE EFFECT of each `export ... from` statement's own
	// NORMAL-position evaluate() actually running - fine for an acyclic
	// dependency graph, but wrong for a genuine cycle (test262
	// instn-*-iee-cycle.js: module A's indirect export resolves through B
	// back into A, for a name A hasn't source-reached yet - B, loaded as
	// A's dependency, needs A's FULL indirect-export shape before A has run
	// any of its own statements) or for star-export ambiguity identity
	// (test262 ambiguous-export-bindings/*.js: two different immediate
	// `export *` sources that both resolve to the SAME underlying binding
	// must NOT be flagged ambiguous, but addStarReExport()'s naive "same
	// immediate source object" check can't tell the difference). Populated
	// at HOIST TIME as pure, no-side-effect metadata - registering an entry
	// here does not itself trigger resolveModuleForRequest()/importModule();
	// that only happens once resolveExport() actually needs to walk through
	// it. A self-referencing `export {x as y} from './this-file.js'` is
	// registered the same way as any other (moduleRequest is just this
	// module's own path) - resolveModuleForRequest() naturally resolves it
	// back to `this` via the module cache (see JSGlobalContext.
	// registerRootModule()), no special-casing needed.
	private java.util.List<org.monflabs.galtajs.node.ASTProgram.IndirectExportEntry> indirectExportEntries;
	private java.util.List<org.monflabs.galtajs.node.ASTProgram.StarExportEntry> starExportEntries;

	public void registerIndirectExport(String exportName, String moduleRequest, String importName) {
		if(indirectExportEntries==null) {
			indirectExportEntries = new java.util.ArrayList<>();
		}
		indirectExportEntries.add(new org.monflabs.galtajs.node.ASTProgram.IndirectExportEntry(exportName, moduleRequest, importName));
	}

	public void registerStarExport(String moduleRequest) {
		if(starExportEntries==null) {
			starExportEntries = new java.util.ArrayList<>();
		}
		starExportEntries.add(new org.monflabs.galtajs.node.ASTProgram.StarExportEntry(moduleRequest));
	}

	// Mirrors JSInterpretedUnit.resolveModuleForRequest() exactly: never
	// throws, returns null on any failure (a genuinely missing module
	// surfaces instead at the ordinary import/export statement's own
	// normal-position evaluate(), which already throws a proper error for
	// it) - may return a module that's still mid-evaluation, which is
	// exactly what makes resolving INTO a not-yet-finished module (the
	// cyclic case above) safe here.
	private JSModule resolveModuleForRequest(String moduleRequest) {
		if(activeContext==null) {
			return null;
		}
		try {
			return RuntimeUtil.importModule(activeContext, moduleRequest);
		} catch(RuntimeException ex) {
			return null;
		}
	}

	// Mirrors JSInterpretedUnit.getExportAccessor()/resolveExport() exactly
	// - see their own doc comments. Tries the static, execution-order-
	// independent algorithm first; falls back to AbstractModule's own
	// runtime-bookkeeping-based getExportAccessor() (local snapshot/live
	// re-export/star-merge maps) only when that finds nothing, preserving
	// existing behavior for any case this static metadata doesn't cover.
	@Override
	public VarAccessor getExportAccessor(String name) {
		JSModule.ResolvedBinding rb = resolveExport(name, new java.util.HashSet<>());
		if(rb!=null) {
			return rb.accessor();
		}
		return super.getExportAccessor(name);
	}

	// Spec 16.2.1.6.3 ResolveExport - see JSModule.resolveExport()'s own doc
	// comment for the general contract, and JSInterpretedUnit.resolveExport()
	// for the line-for-line equivalent this mirrors. Order matches spec:
	// local export entries first (registerLiveExport()'s own hoisted map -
	// works even if this module's body hasn't started, or hasn't reached the
	// relevant `export` statement, since the STATIC shape never depends on
	// execution order), then "default" (non-hoistable form), then indirect
	// (named-`from`) entries, then star entries - with resolveSet as the
	// cycle guard (spec step 3/8b).
	@Override
	public JSModule.ResolvedBinding resolveExport(String name, java.util.Set<JSModule.ResolveKey> resolveSet) {
		JSModule.ResolveKey key = new JSModule.ResolveKey(this, name);
		if(!resolveSet.add(key)) {
			// Already resolving (this, name) along this same walk - a
			// genuine cycle with no local binding anywhere to terminate it.
			return null;
		}
		VarAccessor local = getLiveLocalExport(name);
		if(local!=null) {
			return new JSModule.ResolvedBinding(this, name, local);
		}
		// See JSInterpretedUnit.resolveExport()'s own doc comment for why
		// this fallback exists and is checked here, before the indirect-
		// entry loop below (only reached once a hoistable local export has
		// already had its chance and found nothing).
		if("default".equals(name) && hasDefaultExport()) {
			return new JSModule.ResolvedBinding(this, "default", VarAccessor.ofStatic("default", getDefaultExport()));
		}
		if(indirectExportEntries!=null) {
			for(org.monflabs.galtajs.node.ASTProgram.IndirectExportEntry ie: indirectExportEntries) {
				if(!ie.exportName().equals(name)) {
					continue;
				}
				if(org.monflabs.galtajs.node.ASTProgram.SOURCE_IMPORT_NAME.equals(ie.importName())) {
					// See JSInterpretedUnit.resolveSourceBinding() - the
					// target is never loaded; its resolved name carries the
					// identity the star-ambiguity comparison needs.
					if(activeContext==null) {
						return null;
					}
					try {
						org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource ms = RuntimeUtil.importModuleSource(activeContext, ie.moduleRequest());
						return new JSModule.ResolvedBinding(null, org.monflabs.galtajs.node.ASTProgram.SOURCE_IMPORT_NAME + ms.getModuleName(), VarAccessor.ofStatic(name, ms));
					} catch(RuntimeException ex) {
						return null;
					}
				}
				JSModule source = resolveModuleForRequest(ie.moduleRequest());
				if(source==null) {
					return null;
				}
				if(org.monflabs.galtajs.node.ASTProgram.NAMESPACE_IMPORT_NAME.equals(ie.importName())) {
					return new JSModule.ResolvedBinding(source, org.monflabs.galtajs.node.ASTProgram.NAMESPACE_IMPORT_NAME,
							VarAccessor.ofStatic(name, RuntimeUtil.buildImportNamespaceObject(env, source)));
				}
				return source.resolveExport(ie.importName(), resolveSet);
			}
		}
		JSModule.ResolvedBinding starResult = null;
		if(starExportEntries!=null) {
			for(org.monflabs.galtajs.node.ASTProgram.StarExportEntry se: starExportEntries) {
				JSModule source = resolveModuleForRequest(se.moduleRequest());
				if(source==null) {
					continue;
				}
				JSModule.ResolvedBinding r = source.resolveExport(name, resolveSet);
				if(r==null) {
					continue;
				}
				if(starResult==null) {
					starResult = r;
				} else if(starResult.module()!=r.module() || !starResult.bindingName().equals(r.bindingName())) {
					// Two DIFFERENT star sources both resolve this name, to
					// different bindings - ambiguous (spec step 8d.ii.2-3).
					return null;
				}
			}
		}
		return starResult;
	}

	@Override
	public boolean isForceStrictMode() {
		return false;
	}

	public boolean isCommonJS() {
		return false;
	}

	protected String getSourceCode() {
		return sourceCode;
	}
	public void setSourceCode(String sourceCode) {
		this.sourceCode = sourceCode;
	}
	// Public wrapper for a debugger (a different package) to read this
	// unit's own source text - same rationale as getOriginalSourceSlice()'s
	// own doc comment just below.
	public String getFullSourceCode() {
		return getSourceCode();
	}

	// Called from generated Java (see ASTBlock.transpileBlockStatements(),
	// guarded by JSTranspilerOptions.isDebuggable()) at each instrumented
	// statement boundary - an instance method (like add()/incNumber() above)
	// so generated code can call it unqualified. All the actual pause/resume
	// logic lives in DebugRuntime, shared with interpreted mode's
	// ASTDebugHook - this is just the transpiled-side entry point into it.
	public final void debugStatement(JSRuntimeContext ctx, int line, int col, boolean debuggerStatement) {
		org.monflabs.galtajs.debug.api.impl.DebugRuntime.checkStatement(ctx, line, col, debuggerStatement);
	}

	// Function.prototype.toString() source fidelity: return the [start,end)
	// slice of this unit's original source text, or null when it's unavailable
	// (source not tracked - getSourceCode() null - or the offsets are out of
	// bounds). A transpiled function stores only these two integers (see
	// BuiltinFunctionTranspiler) rather than its own copy of the substring; the
	// whole source is carried once here (the generated getSourceCode() override
	// returns the baked SourceCode.CODE - reached via virtual dispatch), so this
	// reslices it on demand. getSourceCode() stays protected; this is the public
	// entry point the (differently-packaged) function class calls.
	public String getOriginalSourceSlice(int start, int end) {
		String code = getSourceCode();
		if(code==null || start<0 || end>code.length() || start>=end) {
			return null;
		}
		return code.substring(start, end);
	}

	protected JSTranspilerMap getTranspilerMap() {
		return transpiledMap;
	}
	public void setTranspilerMap(JSTranspilerMap transpiledMap) {
		this.transpiledMap = transpiledMap;
	}	
	
    @Override
	public Object executeThis(Object _this) {
    	JSTranspiledRuntimeContext ctx = new TranspiledGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor());
    	return executeWithContext(ctx);
    }
     
    //
    // Execute with a context
    //
    @Override
	public Object executeWithContext(JSRuntimeContext context) {
    	return runValue((JSTranspiledRuntimeContext)context);
    }
	public boolean isAsyncExecution() {
		return false;
	}

	public final Object runValue(JSTranspiledRuntimeContext context) { // to be removed
		return context.with(() -> {
			JSGlobalContext gc = context.getGlobalContext();
			gc.setScriptUnit(this); // we keep this for later access when evaluating properties
			// resolveModuleForRequest()'s own context - unlike moduleContext
			// (only ever set by initModule(), i.e. when this unit is loaded
			// as a DEPENDENCY), a root script/module reaches here via
			// runValue() directly, never initModule() - resolveExport() must
			// still be able to resolve a relative moduleRequest string
			// against THIS module's own path even when it's the root (test262
			// ambiguous-export-bindings/*.js's self-import, instn-*-iee-
			// cycle.js reached recursively from a dependency while this root
			// is still mid-load).
			activeContext = context;
			// A module executed as the ROOT of a run must be discoverable by a
			// self-/circular-import reaching back to it (see
			// docs/GaltaJS/TranspiledModuleLiveBindingsDesignBrief.md's P1 and
			// JSGlobalContext.registerRootModule()'s own doc) - mirrors
			// JSInterpretedUnit.executeWithContext()'s identical call. Safe
			// ONLY now that P2 (AbstractModule.registerLiveExport()) exists:
			// an earlier, standalone attempt at just this one line regressed
			// self-imports that used to "accidentally" work via a duplicate,
			// freshly-interpreted copy of this same module (which has no
			// awareness this instance exists) - once self-imports instead
			// correctly find THIS real instance, they need its exports to
			// already be live, not one-time snapshots. No-op for a script
			// (isModuleUnit()==false) or a module reached as a dependency,
			// not a root - registerRootModule() only matters for the
			// specific self-/circular-reference-back-to-the-currently-
			// running-root case peekModule() looks up.
			if(isModuleUnit()) {
				evalStatus = ModuleEvalStatus.EVALUATING;
				gc.registerRootModule(this);
				// A module's `this` is ALWAYS undefined, unconditionally,
				// never globalThis - mirrors JSInterpretedUnit.
				// executeWithContext()'s identical call and doc comment
				// (test262 language/module-code/eval-this.js): the
				// constructor's own "no `this` specified, default to
				// globalThis" fallback (correct for a SCRIPT) must be
				// overridden here for a module root, which otherwise falls
				// through to that same script-shaped default.
				gc.setThis(RuntimeUtil.UNDEFINED);
			}
			// Snapshot THIS (top-level) unit's own forced-strict status ONCE,
			// separately from the scriptUnit field above - mirrors
			// JSInterpretedUnit's identical setOwnForceStrictMode() call for
			// InterpretedGlobalRuntimeContext. See TranspiledGlobalRuntimeContext.
			// ownForceStrictMode's own doc for why isStrictMode() can't just
			// read scriptUnit.isForceStrictMode() dynamically (a later direct
			// eval running via the AST-interpreter temporarily overwrites
			// scriptUnit on this SAME shared global context object).
			if(gc instanceof TranspiledGlobalRuntimeContext tgc) {
				tgc.setOwnForceStrictMode(isForceStrictMode());
			}
			Object val = runOnce(context);
			// A module that deferred its body (deferModuleUntilSettled()) may
			// have finished, during the drain runOnce() just ran, with an
			// error: a failed dependency, or its own body failing on the
			// retry, whose exception had no caller to reach. Surface it the
			// way a module running its own body reports a failure - mirrors
			// JSInterpretedUnit.executeWithContext() (test262
			// language/module-code/top-level-await/module-import-rejection*.js).
			if (isModuleUnit() && evalStatus == ModuleEvalStatus.ERRORED && evaluationError != null) {
				throw RuntimeUtil.wrap(evaluationError);
			}
			if (val instanceof ConsSequence cs) {
				return cs.toString();
			}
			return val;
		});
	}

	// True for the duration of a single _runValue() call that ends up
	// deferring itself (see deferModuleUntilSettled()'s own doc comment) -
	// set by deferModuleUntilSettled() itself, called from ASTProgram's own
	// generated import-hoisting code (see its `_pendingModuleDeps` local's
	// own doc comment) immediately before _runValue()'s early `return;`,
	// checked by runOnce() right after _runValue() returns to tell a
	// genuine completion (mark EVALUATED) apart from an early, deliberate
	// return (leave EVALUATING - a retry is already scheduled). Reset at
	// the top of every runOnce() call so a retry starts with a clean slate.
	private boolean deferredPending;

	// Runs this unit's _runValue() exactly once, handling both a genuine
	// completion (success or error, unchanged from before this fix) and a
	// deliberate early return (deferredPending==true - leaves evalStatus at
	// EVALUATING, doesn't touch evaluationError, doesn't rethrow anything -
	// deferModuleUntilSettled() has already arranged for this SAME method
	// to run again once whatever it's waiting for settles). Factored out of
	// runValue() so deferModuleUntilSettled()'s own retry callback can
	// reuse the identical completion-handling logic instead of duplicating
	// it.
	private Object runOnce(JSTranspiledRuntimeContext context) {
		return context.getGlobalContext().getExecutor().execute(() -> {
			try {
				deferredPending = false;
				_runValue(context);
				if(deferredPending) {
					return RuntimeUtil.UNDEFINED;
				}
				if(isModuleUnit()) {
					evalStatus = ModuleEvalStatus.EVALUATED;
				}
				return context.getReturnValue();
			} catch (Exception ex) {
				if(isModuleUnit()) {
					evalStatus = ModuleEvalStatus.ERRORED;
					evaluationError = JSRuntimeException.exceptionObject(ex);
				}
				JSRuntimeException rt = RuntimeUtil.wrap(ex);

				JSTranspilerMap map = getTranspilerMap();
				if (map != null) {
					StringBuilder builder = new StringBuilder();
					String currentClass = getClass().getName();
					// Get the exception stack
					for (Throwable t = ex; t != null; t = BaseException.getCause(t)) {
						StackTraceElement[] elt = t.getStackTrace();
						for (int i = 0; i < elt.length; i++) {
							// for(int i=elt.length-1; i>=0; i--) {
							StackTraceElement e = elt[i];
							String className = e.getClassName();
							if (className.equals(currentClass)) {
								int javaLine = e.getLineNumber();
								int line = map.findScriptLine(javaLine);
								if (line > 0) {
									int col = 1; // Can we do better??
									builder.append(StringFormat.format("at line {0}, column {1}\n", line, col));
									JSException.extractSourceCode(builder, JSException.EXTRACT_LINES,
											getSourceCode(), line, col);
									builder.append('\n');
								}
							}
						}
					}
					if (builder.length() > 0) {
						rt.setStackTraceMessage(builder.toString());
					}
				}
				throw rt;
			}
		}, isAsyncExecution());
	}

	// Called from ASTProgram's own generated import-hoisting code (see its
	// `_pendingModuleDeps` local's own doc comment) once every one of this
	// module's own imports has had its importModule() triggered and at
	// least one resolved to a genuinely different, still-evaluating module
	// (not yet EVALUATED/ERRORED, never a self-import - see ASTImport's own
	// doc comment on why that case is excluded upstream). Defers finishing
	// THIS module's own body until every entry in `deps` settles, instead
	// of running against not-yet-ready state (test262 top-level-await/
	// module-import-resolution.js, module-sync-import-async-resolution-
	// ticks.js, dfs-invariant.js, pending-async-dep-from-cycle.js). Safe to
	// simply RE-RUN _runValue() from scratch once ready, rather than
	// somehow resuming mid-method: nothing observable can have happened
	// yet by the time this is reached (only variable declaration, live-
	// export registration, and imports - all idempotent, all re-computed
	// identically on retry; test262's own spec-mandated import hoisting
	// guarantees no non-import statement runs before every import
	// resolves). A plain countdown (not a re-run per callback) is required
	// here: two of `deps` settling in the same tick must trigger exactly
	// ONE retry, not two re-executions of this module's body (which would
	// double any of its own side effects, e.g. a global mutation) - the
	// countdown, captured per this specific deferral, guarantees that.
	// Deliberately narrower than a full per-module pendingAsyncDependencies
	// count: only interoperates with JSInterpretedUnit dependencies
	// (empirically confirmed to be the ONLY kind a transpiled unit's own
	// dependencies ever are - modules aren't ahead-of-time transpiled per
	// file, only the entry point is) via that class's own existing generic
	// addEvaluationCompletionCallback() API - zero changes needed there.
	// Does not attempt cycle-root merging itself (see KnownGaps.md's own
	// account of the fuller, reverted attempt this narrows) - relies
	// entirely on JSInterpretedUnit's own already-correct cycle-root
	// tracking to decide when a cyclic dependency has genuinely settled.
	public void deferModuleUntilSettled(JSTranspiledRuntimeContext context, java.util.List<JSInterpretedUnit> deps) {
		deferredPending = true;
		int[] remaining = { deps.size() };
		for (JSInterpretedUnit dep : deps) {
			dep.addEvaluationCompletionCallback(() -> {
				if (--remaining[0] == 0) {
					// A dependency whose evaluation failed (e.g. a rejected
					// top-level await) makes this module fail with the same
					// error, without running its body (spec
					// AsyncModuleExecutionRejected, as JSInterpretedUnit's
					// notifyAsyncParents() does). runValue() reports it.
					for (JSInterpretedUnit d : deps) {
						if (d.getModuleStatus() == JSInterpretedUnit.ModuleStatus.ERRORED) {
							evalStatus = ModuleEvalStatus.ERRORED;
							evaluationError = d.getEvaluationError();
							deferredPending = false;
							return;
						}
					}
					runOnce(context);
				}
			});
		}
	}

	@Override
	public JSModule initModule(JSGlobalContext globalContext, boolean commonJS) {
		if(moduleContext!=null) {
			throw RuntimeUtil.error("Module {0} is already initialized",getDescriptor().getName());
		}
		this.moduleContext = new ModuleRuntimeContext(globalContext,this);
		if(commonJS) {
			executeWithContext(moduleContext);
			Object module = moduleContext.getVariableValue(ModuleUtil.MODULE,null);
			if(module instanceof JSObject jm) {
				Object exports =jm.getOwnProperty(ModuleUtil.EXPORTS);
				if(RuntimeUtil.isNotNullOrUndefined(exports)) {
					if(exports instanceof JSObject jo) {
						setNamedExports(jo);
					}
					setDefaultExport(exports);
				}
			}
		} else {			
			executeWithContext(moduleContext);
		}
		// We remove the global context as this was ephemeral during the module initialization
		// We remove it afterwards to avoid a memory leak...
		//moduleContext.clearGlobalContext();
		return this;
	}

	protected void initModule(JSGlobalContext globalContext, JSInterpretedUnit script) {
		script.executeWithContext(moduleContext);
    }
	private static class ModuleRuntimeContext extends TranspiledModuleRuntimeContext {
		private ModuleRuntimeContext(JSGlobalContext globalContext, JSTranspiledUnit module) {
			super(globalContext.getEnvironment(),module);
			// This is the variable defined in AbstractContext
			this.globalContext =globalContext;
		}
	}
    
	@Override
	public JSModuleContext getModuleContext() {
		if(moduleContext==null) {
			throw RuntimeUtil.error("Module {0} is not yet initialized",getDescriptor().getName());
		}
		return moduleContext;
	}

	protected abstract void _runValue(JSTranspiledRuntimeContext _ctx) throws Exception;
	
	//
	public static void debugger() {
		Console.log("Entering debug - add a java breakpoint to {0}.debugger",JSTranspiledUnit.class.getSimpleName());
	}
	
	//
	public static String largeString(String...parts) {
		StringBuilder b = new StringBuilder();
		for(int i=0; i<parts.length; i++) {
			b.append(parts[i]);
		}
		return b.toString();
	}

	// Reassembles a large String[] constant-pool literal (e.g. ~8000 top-level
	// var names, language/identifiers/start-unicode-10.0.0.js) that
	// JSTranspiler.createConstantPool split into several separately-compiled
	// chunk methods - see that method's own comment for why a single flat
	// array-literal expression alone can approach/exceed the JVM's 64KB
	// per-method bytecode limit.
	public static String[] mergeStringArrays(String[]...chunks) {
		int total = 0;
		for(String[] c: chunks) {
			total += c.length;
		}
		String[] result = new String[total];
		int pos = 0;
		for(String[] c: chunks) {
			System.arraycopy(c, 0, result, pos, c.length);
			pos += c.length;
		}
		return result;
	}

	
	
	//
	// Object & Array Constructor
	//

	public JSObjectImpl createObject() {
		return new BuiltinObject(env);
	}
	public JSObjectImpl createObject(Object...values) {
		return JSObject.of(env,values);
	}
	public static JSObjectImpl createObject(JSEnvironment env, String k1, Object v1) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		return o;
	}
	public static JSObjectImpl createObject(JSEnvironment env, String k1, Object v1, String k2, Object v2) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		o.putValue(k2, v2);
		return o;
	}
	public static JSObjectImpl createObject(JSEnvironment env, String k1, Object v1, String k2, Object v2, String k3, Object v3) {
		BuiltinObject o = new BuiltinObject(env);
		o.putValue(k1, v1);
		o.putValue(k2, v2);
		o.putValue(k3, v3);
		return o;
	}

	public JSArrayImpl createArray() {
		return new BuiltinArray(env);
	}
	public JSArrayImpl createArray(Object...values) {
		return JSArray.of(env,values);
	}
	
	public BuiltinClassConstructor createClass(String className, Object _superClass, BuiltinClassConstructor.Initializer initializer) {
		return RuntimeUtil.createClass(env, className, _superClass, initializer);
	}
	
	public Arguments createArguments(Object[] values) {
		return Arguments.create(env,values);
	}
	public Arguments createArguments(Object[] values, Object callee) {
		return Arguments.create(env,values,callee);
	}
	// A strict-mode (or non-simple-parameter-list) function's own `arguments`
	// object must have a "callee" ACCESSOR property whose getter/setter are
	// both the shared %ThrowTypeError% intrinsic (a "poison pill" forbidding
	// access), not a plain data property - mirrors the interpreter's own
	// InterpretedFunctionRuntimeContext poison-flag computation. See
	// ASTFunction.transpileParameterBindingPrologue's own call site.
	public Arguments createArguments(Object[] values, Object callee, boolean poisonPillCallee) {
		return Arguments.create(env,values,callee,poisonPillCallee);
	}
	// A non-strict, simple-parameter-list function's own `arguments` object
	// is MAPPED - arguments[i] and the i-th formal parameter stay live-
	// linked (spec 9.4.4 CreateMappedArgumentsObject). mappedAccessors is a
	// literal VarAccessor[] built at transpile time (one JSVarRef per
	// SIMPLE parameter position, directly against this function's own
	// local-array slot - see ASTFunction.transpileParameterBindingPrologue's
	// own call site for how/when this is emitted, including why the mapped-
	// vs-unmapped choice needs a runtime check, not just a static one).
	public Arguments createArguments(Object[] values, Object callee, VarAccessor[] mappedAccessors) {
		return Arguments.create(env,values,callee,mappedAccessors);
	}

	
	//
	// Function Arguments
	//
	
	public static Object initArg(Object[] array, int index) {
		if(index<array.length) {
			return array[index]; 
		} else {
			return RuntimeUtil.UNDEFINED;
		}
	}
	public static Object  initArg(Object[] array, int index, Object defaultValue) {
		if(index<array.length && array[index]!=RuntimeUtil.UNDEFINED) {
			return array[index]; 
		} else {
			return defaultValue;
		}
	}
	public static Object  initArg(Object[] array, int index, Supplier<Object> defaultValue) {
		if(index<array.length && array[index]!=RuntimeUtil.UNDEFINED) {
			return array[index]; 
		} else {
			if(defaultValue!=null) {
				return defaultValue.get();
			} else {
				return RuntimeUtil.UNDEFINED;
			}
		}
	}
	public final Object  initArg(Object[] array, Object[] indexes, Object spread) {
		return VarAccessor.destruct(env,array,indexes,spread,(DefaultStep)null);
	}
	public final Object  initArg(Object[] array, Object[] indexes, Object spread, Supplier<Object> defaultValue) {
		return VarAccessor.destruct(env,array,indexes,spread,defaultValue);
	}
	public final Object  initArg(Object[] array, Object[] indexes, Object spread, DefaultStep defaults) {
		return VarAccessor.destruct(env,array,indexes,spread,defaults);
	}

	
	//
	// Identifier Access
	//

	public Object getIdentifierValue(JSTranspiledRuntimeContext context, String varName,  boolean throwError) {
		Object v = context.getVariableValue(varName,RuntimeUtil.NOT_AVAILABLE);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		v = context.getGlobalContext().getGlobalThis().getOwnProperty(varName, RuntimeUtil.NOT_AVAILABLE);
		if(v!=RuntimeUtil.NOT_AVAILABLE) {
			return v;
		}
		if(throwError) {
			throw RuntimeUtil.referenceError("Unknown identifier {0}",varName);
		}
		return RuntimeUtil.UNDEFINED;
	}

	// Emitted only for an identifier read whose static resolution walked OUT
	// of a function that ASTFunction.hasNonStrictDirectEvalInOwnBody() found
	// to have a literal, non-strict direct eval() call in its own body (see
	// ASTIdentifier.getIdentifierReadAccessor's crossedEvalShadow tracking and
	// TranspilerEvalShadowContext's own doc) - `context` is that SAME
	// function's own runtime context (never walked past it, unlike
	// getIdentifierValue above), since a non-strict direct eval's own var
	// declaration lands directly on it (see StandardLibrary.BaseEvalContext.
	// createVariable / VarAccessor.isOwnScope()'s doc). getLocalVariableEntry
	// checks ONLY this exact context's own dynamically-created bindings (no
	// parent walk, so this can never accidentally reach an unrelated outer or
	// global binding of the same name) - a real hit means the eval actually
	// ran and declared `varName`, genuinely shadowing whatever the
	// compile-time-fixed `staticValue` resolves to; no hit (the common case
	// whenever the eval either didn't run on this particular execution path,
	// or declared a different name) falls straight back to it.
	public Object evalShadowRead(JSTranspiledRuntimeContext context, String varName, Object staticValue) {
		VarAccessor a = context.getLocalVariableEntry(varName);
		return a!=null ? a.getValue() : staticValue;
	}

	// Object Environment Record's HasBinding (spec 9.1.1.2.1), the
	// [[IsWithEnvironment]]==true branch: a with-object's own @@unscopables
	// property, if it's an object, can mark individual names as "not
	// visible through this with" by mapping them to a truthy value.
	private boolean isUnscopable(Object withObj, String varName) {
		Object unscopables = RuntimeUtil.getProperty(env, withObj, Symbol.UNSCOPABLES, RuntimeUtil.UNDEFINED);
		if(RuntimeUtil.isObject(env, unscopables)) {
			return RuntimeUtil.toBoolean(env, RuntimeUtil.getProperty(env, unscopables, varName, RuntimeUtil.UNDEFINED));
		}
		return false;
	}

	public Object getIdentifierValue(JSTranspiledRuntimeContext context, String varName,  Object[] vars, int index, boolean throwError, Object...with) {
		for(int i=0; i<with.length; i++) {
			// HasBinding (9.1.1.2.1) and GetBindingValue (9.1.1.2.6) are two
			// DISTINCT [[HasProperty]]/[[Get]] operations, each independently
			// observable through a Proxy with-object's own `has`/`get` traps
			// (test262 has-binding-call-with-proxy-env.js and neighbors) -
			// using [[Get]] alone (as a "not NOT_AVAILABLE" existence check)
			// wrongly fires `get` for a binding that HasBinding would have
			// reported false for (and skipped straight to the enclosing
			// scope), and skips the SEPARATE `get` trap call GetBindingValue
			// itself must make once existence is confirmed.
			if(RuntimeUtil.hasProperty(env,with[i],varName) && !isUnscopable(with[i],varName)) {
				// GetBindingValue (9.1.1.2.6) re-checks HasProperty on its own,
				// independently of the HasBinding check just above that decided
				// THIS environment record is where the identifier resolves -
				// observably distinct for a Proxy with-object whose `has` trap
				// can log/count calls, or a self-deleting @@unscopables getter
				// (test262 get-binding-value-*-with-proxy-env.js: expects
				// has,get(unscopables),has,get - not just has,get(unscopables),
				// get). GetBindingValue's own S parameter is the REFERENCING
				// code's own strictness (context.isStrictMode()) - a `with`
				// statement itself is always non-strict syntactically, but a
				// nested strict-mode function inside it can still resolve a
				// free identifier through this with environment and must get
				// a ReferenceError, not undefined, if the binding vanished
				// (test262 get-mutable-binding-binding-deleted-in-get-
				// unscopables-strict-mode.js: a same-named property deleted
				// by its own @@unscopables getter, read by a nested "use
				// strict" function).
				if(!RuntimeUtil.hasProperty(env,with[i],varName)) {
					if(context.isStrictMode()) {
						throw RuntimeUtil.referenceError("{0} is not defined", varName);
					}
					return RuntimeUtil.UNDEFINED;
				}
				Object v = RuntimeUtil.getProperty(env,with[i],varName,RuntimeUtil.UNDEFINED);
				if(v instanceof Callable c) {
					return WithClosure.of(with[i],c);
				}
				return v;
			}
		}
		if(vars!=null) {
			return vars[index];
		}
		return getIdentifierValue(context, varName, throwError);
	}
	
	public VarAccessor getIdentifierAccessor(JSTranspiledRuntimeContext context, String varName) {
		return getIdentifierAccessor(context, varName, true);
	}
	public VarAccessor getIdentifierAccessor(JSTranspiledRuntimeContext context, String varName, boolean isPlainAssignment) {
		VarAccessor a = context.getVariableEntry(varName);
		if(a!=null) {
			return constGuarded(a, varName);
		}
		// Auto-vivifying a missing identifier as a new global is a sloppy-mode-only
		// behavior, and even then ONLY for a plain assignment ("x = 1") - a
		// read-modify-write (compound assignment, ++/--) must first GetValue the
		// existing binding, which throws ReferenceError for an unresolvable
		// reference regardless of strict mode, unless the binding already exists
		// (matches the interpreter's identical `assigner==null` distinction in
		// ASTIdentifier.evaluateAssign(), test262
		// S11.13.2_A2.1_T3.*/S11.13.2_A6.*/S11.13.2_A7.* - "x *= 1" on a wholly
		// undeclared `x` must throw ReferenceError, not silently NaN it). Strict-
		// mode check is per the CURRENT script/function's own strictness
		// (context.isStrictMode()), not just the environment-wide
		// mustDeclareAllVariables() toggle (which exists for a harness that only
		// ever runs already-known-strict scripts, e.g. GaltaJS's own
		// JavaScriptStrictTestCase - test262 mixes strict/sloppy files under one
		// shared environment, so relying on that toggle alone missed the per-
		// script case, e.g. an onlyStrict-flagged test whose strictness comes
		// from its own "use strict" prologue). Matches the same condition already
		// used by RuntimeUtil.assignIdentifierOrCreateGlobal().
		boolean autoCreate = isPlainAssignment && !context.isStrictMode() && !context.getEnvironment().mustDeclareAllVariables();
		VarAccessor global = context.getGlobalContext().getGlobalThis().getOwnVariableAccessor(varName,autoCreate);
		if(global!=null) {
			// PutValue/SetMutableBinding's "stillExists" re-check (9.1.1.2.5):
			// the RHS of a plain assignment ("x = (delete this.x, 2)") can
			// delete a globalThis-backed binding as a side effect between
			// its own resolution and this write - strict-mode code must get
			// a ReferenceError instead of silently recreating the property.
			// A plain identifier assignment's generated code calls
			// setValue() directly (no external stillExists() wrapping the
			// way the interpreter's ASTIdentifier.evaluateAssign() has, or
			// compound assignment's assignXXX() runtime methods have), so
			// it must be checked HERE (test262 assignment-operator-calls-
			// putvalue-lref--rval--*.js, S11.13.1_A6_T*.js).
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
					if(context.isStrictMode() && !global.stillExists()) {
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
		throw RuntimeUtil.referenceError("Unknown identifier {0}",varName);
	}

	// A plain identifier assignment's generated code calls setValue()
	// directly on whatever VarAccessor getIdentifierAccessor() returns (no
	// external immutability check wrapping the way compound assignment's
	// assignXXX() runtime methods have, or the way ASTIdentifier's OWN
	// codegen already static-checks a compile-time-known VariableDef's
	// VAR_TYPE.CONST before ever reaching this method - see
	// getIdentifierWriteAccessor()) - so a binding that's only DYNAMICALLY
	// resolvable here (not this script's own compile-time-known
	// VariableDef, e.g. a `const`/`using` declared by an earlier,
	// separately-compiled $262.evalScript call sharing this same runtime
	// context's VariableMap - see the collision-validation fix elsewhere in
	// this class for why they share one) had no immutability enforcement at
	// all: context.getVariableEntry()'s raw VariableEntry.setValue()
	// (VariableMap.java) is type-unaware, unconditionally overwriting the
	// stored value. Mirrors ASTIdentifier.evaluateAssign()'s identical
	// interpreted-mode CONST/USING check (test262 language/global-code/
	// script-decl-lex.js: `test262const = 4` after
	// `$262.evalScript('const test262const = 3;')` must throw TypeError).
	// Deliberately narrower than RuntimeUtil.assignIdentifierOrCreateGlobal()'s
	// parallel TDZ+CONST/USING check: TDZ is not addressed here since a
	// dynamically-resolved binding reaching this method has, in every
	// reachable case, already finished its own declaration/initialization in
	// a prior evalScript call - adding it would be speculative, unverified
	// scope creep.
	private VarAccessor constGuarded(VarAccessor a, String varName) {
		VAR_TYPE t = a.getType();
		if(t!=VAR_TYPE.CONST && t!=VAR_TYPE.USING) {
			return a;
		}
		return new VarAccessor() {
			@Override
			public String getKey() {
				return a.getKey();
			}
			@Override
			public Object getValue() {
				return a.getValue();
			}
			@Override
			public VAR_TYPE getType() {
				return t;
			}
			@Override
			public boolean stillExists() {
				return a.stillExists();
			}
			@Override
			public Object setValue(Object value) {
				throw RuntimeUtil.typeError("Assignment to constant variable {0}", varName);
			}
		};
	}
	public VarAccessor getIdentifierAccessor(JSTranspiledRuntimeContext context, String varName, Object[] vars, int index, boolean create, Object...with) {
		for(int i=0; i<with.length; i++) {
			// See getIdentifierValue(...)'s identical comment: HasBinding
			// ([[HasProperty]]) and GetBindingValue/SetMutableBinding
			// ([[Get]]/[[Set]]) are separately observable Proxy trap calls -
			// this existence check must be a genuine [[HasProperty]], not a
			// "read and see if it's NOT_AVAILABLE" [[Get]].
			if(RuntimeUtil.hasProperty(context.getEnvironment(),with[i],varName) && !isUnscopable(with[i],varName)) {
				int idx = i;
				return new VarAccessor() {
					@Override
					public String getKey() {
						return varName;
					}
					@Override
					public Object getValue() {
						// GetBindingValue (9.1.1.2.6) re-checks HasProperty on its own,
						// independently of the HasBinding check that already ran to
						// even reach this VarAccessor - observably distinct for a Proxy
						// with-object (test262 get-binding-value-*-with-proxy-env.js).
						// S is the REFERENCING code's own strictness
						// (context.isStrictMode()), not the with-statement's own
						// (always non-strict) - a nested strict-mode function can
						// still resolve a free identifier through this with
						// environment (test262 get-mutable-binding-binding-deleted-
						// in-get-unscopables-strict-mode.js).
						if(!RuntimeUtil.hasProperty(context.getEnvironment(),with[idx],varName)) {
							if(context.isStrictMode()) {
								throw RuntimeUtil.referenceError("{0} is not defined", varName);
							}
							return RuntimeUtil.UNDEFINED;
						}
						Object v = RuntimeUtil.getProperty(context.getEnvironment(),with[idx],varName,RuntimeUtil.UNDEFINED);
						if(v instanceof Callable c) {
							return WithClosure.of(with[idx],c);
						}
						return v;
					}
					@Override
					public Object setValue(Object value) {
						// SetMutableBinding (9.1.1.2.5) re-checks HasProperty on its
						// own too (step 2), independently of the HasBinding check
						// above - same Proxy-observability reasoning as getValue()
						// (test262 set-mutable-binding-*-with-proxy-env.js). A PLAIN
						// assignment's generated code calls setValue() directly (no
						// surrounding stillExists() check the way compound
						// assignment's assignXXX() runtime methods have), so S (the
						// referencing code's own strictness) must be checked HERE -
						// test262 set-mutable-binding-binding-deleted-in-get-
						// unscopables-strict-mode.js: a nested "use strict" function
						// writing through this with environment to a binding its own
						// @@unscopables getter just deleted must get a
						// ReferenceError, not a silent recreate.
						if(!RuntimeUtil.hasProperty(context.getEnvironment(),with[idx],varName) && context.isStrictMode()) {
							throw RuntimeUtil.referenceError("{0} is not defined", varName);
						}
						RuntimeUtil.setProperty(context.getEnvironment(),with[idx],varName,value);
						return value;
					}
					@Override
					public boolean stillExists() {
						// Mirrors InterpretedWithRuntimeContext.resolveOwnIdentifierEntry's
						// identical override - a with-object-backed binding can be deleted
						// (e.g. by a self-deleting getter, or the RHS of the assignment
						// itself) between this accessor's resolution and the eventual
						// write; SetMutableBinding must re-check HasProperty rather than
						// rely on the interface default of "always exists".
						return RuntimeUtil.hasProperty(context.getEnvironment(),with[idx],varName);
					}
				};
			}
		}
		if(vars!=null) {
			return JSVarRef.of(varName,vars,index);
		}
		VarAccessor a = context.getGlobalContext().getGlobalThis().getOwnVariableAccessor(varName,create);
		if(a==null) {
			throw new IllegalStateException(StringFormat.format("Unknown variable {0}",varName));
		}
		return a;
	}
	
	public boolean deleteIdentifier(JSTranspiledRuntimeContext ctx, String varName, VAR_TYPE varType, Object[] vars, int index, Object...with) {
		for(int i=0; i<with.length; i++) {
			boolean v = RuntimeUtil.getOwnPropertyDescriptor(ctx.getEnvironment(),with[i],varName)!=null;
			if(v && !isUnscopable(with[i],varName)) {
				return RuntimeUtil.deleteProperty(ctx.getEnvironment(),with[i],varName);
			}
		}
		return deleteIdentifier(ctx, varName, varType, vars, index);
	}	
	public boolean deleteIdentifier(JSTranspiledRuntimeContext ctx, String varName, VAR_TYPE varType, Object[] vars, int index) {
		if(varType!=null) {
			return delete(varType,vars,index);
		}		
		return deleteIdentifier(ctx, varName);
	}	
	public boolean deleteIdentifier(JSTranspiledRuntimeContext ctx, String varName) {
		if(ctx.isStrictMode()) {
			throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
		}
		// Note: not gated on hasOwnProperty() first - deleteProperty() already
		// returns true for a non-existent key (nothing to delete), and correctly
		// checks standardObjects (e.g. NaN, Infinity) for configurability, unlike
		// hasOwnProperty() which only sees globalThis's own storage.
		return ctx.getGlobalContext().getGlobalThis().deleteProperty(varName);
	}

	private boolean delete(VAR_TYPE varType, Object[] vars, int index) {
		if(vars!=null) {
			if(!RuntimeUtil.isStrictMode()) {
				if(varType==VAR_TYPE.AUTO) {
					vars[index] = RuntimeUtil.UNDEFINED;
					return true;
				}
				// FUNCTION is a non-configurable global binding, same as VAR -
				// falls through to `return false` below.
				return false;
			}
			throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
		}
		return true;
	}

	
	
	//
	// Runtime support
	//
	
	public Object memberGet(Object instance, String memberName) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.UNDEFINED);
	}
	public Object memberGet(Object instance, Number memberName) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.UNDEFINED);
	}
	public Object memberGet(Object instance, Symbol memberName) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.UNDEFINED);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.UNDEFINED);
	}
	public Object memberGet(Object instance, Object memberName) {
		if(instance instanceof JSObject jo) {
			return jo.getProperty(memberName,RuntimeUtil.UNDEFINED);
		}
		// A dynamic (Object-typed, possibly needing ToPropertyKey coercion,
		// e.g. a custom toString()) member key must NOT be coerced before
		// the base is checked for null/undefined - JSAccessor.getProperty's
		// own generic Object-member dispatcher coerces first (correct for
		// most callers), so this check has to happen HERE, before reaching
		// it, to match spec (13.3.3 EvaluatePropertyAccessWithExpressionKey/
		// 6.2.5.5 GetValue: ToObject(base) precedes ToPropertyKey(name)).
		// Confirmed via test262 language/expressions/member-expression/
		// computed-reference-null-or-undefined.js.
		if(instance==null || instance==RuntimeUtil.UNDEFINED) {
			throw RuntimeUtil.typeError("Left part of member is null or undefined, {0}", instance);
		}
		JSAccessor accessor = env.getAccessor(instance);
		return accessor.getProperty(instance,memberName,RuntimeUtil.UNDEFINED);
	}

	// SuperProperty [[Get]] (spec 13.3.7.1 MakeSuperPropertyReference / 6.2.5.5
	// GetValue step 5b): the search for the property starts at the home
	// object's prototype (instance), but the receiver passed to an invoked
	// getter must remain the actual `this` - mirrors ASTMember/ASTArrayMember's
	// own isSuper readProperty() branch in the interpreter.
	public Object memberGetWithThis(Object instance, String memberName, Object thisArg) {
		return RuntimeUtil.getPropertyWithReceiver(env, instance, memberName, thisArg);
	}
	public Object memberGetWithThis(Object instance, Object memberName, Object thisArg) {
		return RuntimeUtil.getPropertyWithReceiver(env, instance, memberName, thisArg);
	}
	
	
//	public Object memberGet(Object instance, Function<Object,Object> memberFunc) {
//		if(instance instanceof JSObject jo) {
//			return jo.getProperty(memberFunc.apply(instance),RuntimeUtil.UNDEFINED);
//		}
//		return RuntimeUtil.getProperty(env, instance, memberFunc.apply(instance),RuntimeUtil.UNDEFINED);
//	}
//	public Object memberGet(Object instance, Function<Object,Object> memberFunc, boolean nullop) {
//		if(instance==null && nullop) {
//			return RuntimeUtil.UNDEFINED;
//		}
//		if(instance instanceof JSObject jo) {
//			return jo.getProperty(memberFunc.apply(instance),RuntimeUtil.UNDEFINED);
//		}
//		return RuntimeUtil.getProperty(env, instance, memberFunc.apply(instance), RuntimeUtil.UNDEFINED);
//	}
	
	
	//
	// Operators
	//

	public final Object add(Object o1, Object o2) {
		return RuntimeUtil.add(env, o1, o2);
	}
	// CharSequence-typed overloads: transpiled code binds these at compile time
	// when the emitted operand expression has Java static type CharSequence (a
	// String literal, which is a CharSequence, or a getReturnedType()==STRING
	// operand emitted with a (CharSequence) cast). CharSequence, not String,
	// because chained ASTAdd of STRING type produces a ConsString at runtime
	// (see ConsString.of / StaticConfiguration.ENABLE_CONSSTRING).
	public final Object add(CharSequence s1, Object o2) {
		return RuntimeUtil.add(env, s1, o2);
	}
	public final Object add(Object o1, CharSequence s2) {
		return RuntimeUtil.add(env, o1, s2);
	}
	public final Object add(CharSequence s1, CharSequence s2) {
		return RuntimeUtil.add(env, s1, s2);
	}
	public final Object sub(Object o1, Object o2) {
		return RuntimeUtil.sub(env, o1, o2);
	}
	public final Object mul(Object o1, Object o2) {
		return RuntimeUtil.mul(env, o1, o2);
	}
	public final Object mod(Object o1, Object o2) {
		return RuntimeUtil.mod(env, o1, o2);
	}
	public final Object div(Object o1, Object o2) {
		return RuntimeUtil.div(env, o1, o2);
	}
	public final Object power(Object o1, Object o2) {
		return RuntimeUtil.power(env, o1, o2);
	}
	public final boolean eq(Object o1, Object o2) {
		return RuntimeUtil.eq(env, o1, o2);
	}
	public final boolean ne(Object o1, Object o2) {
		return RuntimeUtil.ne(env,o1,o2);
	}
    public final boolean lt(Object v1, Object v2) {
		return RuntimeUtil.lt(env,v1,v2);
	}
    public final boolean lt(Object v1, Integer v2) {
		if(v1 instanceof Integer i1) {
			if(!RuntimeUtil.isBoxedNumber(env, v1)) {
					return i1.intValue()<(v2!=null?v2.intValue():0);
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = RuntimeUtil.toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = RuntimeUtil.toPrimitive(env,v2,HINT.NUMBER);
		return RuntimeUtil._lt(env,o1, o2, false);
	}
    public final boolean le(Object v1, Object v2) {
		return RuntimeUtil.le(env,v1,v2);
	}
    public final boolean le(Object v1, Integer v2) {
		if(v1 instanceof Integer i1) {
			if(!RuntimeUtil.isBoxedNumber(env, v1)) {
					return i1.intValue()<=(v2!=null?v2.intValue():0);
			}
		}
    	// Ensure the eval order is always 1 then 2
		Object o1 = RuntimeUtil.toPrimitive(env,v1,HINT.NUMBER);
		Object o2 = RuntimeUtil.toPrimitive(env,v2,HINT.NUMBER);
		return !RuntimeUtil._lt(env,o2, o1, true);
	}

    public final boolean gt(Object v1, Object v2) {
		return RuntimeUtil.gt(env,v1,v2);
	}
	public final boolean ge(Object v1, Object v2) {
		return RuntimeUtil.ge(env,v1,v2);
	}	
	public final boolean eqStrict(Object o1, Object o2) {
		return RuntimeUtil.eqStrict(env,o1,o2);
	}	
	public final boolean neStrict(Object o1, Object o2) {
		return RuntimeUtil.neStrict(env,o1,o2);
	}
	
	public final Object and(Object o1, Object o2) {
		return RuntimeUtil.and(env,o1,o2);
	}
	public final Object and(Object o1, Supplier<Object> o2) {
		return RuntimeUtil.and(env,o1,o2);
	}
	public final Object or(Object o1, Object o2) {
		return RuntimeUtil.or(env,o1,o2);
	}
	public final Object or(Object o1, Supplier<Object> o2) {
		return RuntimeUtil.or(env,o1,o2);
	}
	public final boolean xor(Object o1, Object o2) {
		return RuntimeUtil.xor(env,o1,o2);
	}

	public final Object nullCoalescing(Object leftValue, Object rightValue) {
		return RuntimeUtil.nullCoalescing(env,leftValue,rightValue);
	}	
	public final Object nullCoalescing(Object leftValue, Supplier<Object> rightValue) {
		return RuntimeUtil.nullCoalescing(env,leftValue,rightValue);
	}	
	
	public final Object bitAnd(Object o1, Object o2) {
		return RuntimeUtil.bitAnd(env,o1,o2);
	}
	public final Object bitOr(Object o1, Object o2) {
		return RuntimeUtil.bitOr(env,o1,o2);
	}
	public final Object bitXor(Object o1, Object o2) {
		return RuntimeUtil.bitXor(env,o1,o2);
	}
	
	public final Object lshift(Object o1, Object o2) {
		return RuntimeUtil.lshift(env,o1,o2);
	}
	public final Object rshift(Object o1, Object o2) {
		return RuntimeUtil.rshift(env,o1,o2);
	}
	public final Object runshift(Object o1, Object o2) {
		return RuntimeUtil.runshift(env,o1,o2);
	}
	
	public final Number plus(Object o1) {
		return RuntimeUtil.plus(env,o1);
	}
	public final Number minus(Object o1) {
		return RuntimeUtil.minus(env,o1);
	}
	
	public final Object not(Object o1) {
		return RuntimeUtil.not(env,o1);
	}
	public final Number bitNot(Object o1) {
		return RuntimeUtil.bitNot(env,o1);
	}

	public final String typeof(Object r) {
		return RuntimeUtil.typeof(env,r);
	}
	public final String typeof(Object base, Object index) {
		return RuntimeUtil.typeof(env,base,index);
	}
	public final boolean instanceOf(Object leftValue, Object rightValue) {
		return RuntimeUtil.instanceOf(env,leftValue,rightValue);
	}

	public final boolean in(Object leftValue, Object rightValue) {
		return RuntimeUtil.in(env,leftValue,rightValue);
	}
	public final boolean delete(Object base, Object index) {
		return RuntimeUtil.delete(env, base, index);
	}
	
	public final Object elvis(Object leftValue, Supplier<Object> rightValue) {
		return RuntimeUtil.elvis(env, leftValue, rightValue);
	}

	public final Number toNumber(Object v) {
		return RuntimeUtil.toNumber(env, v);
	}
	public final String toString(Object v) {
		return RuntimeUtil.toString(env, v);
	}
	public final boolean toBoolean(Object v) {
		return RuntimeUtil.toBoolean(env, v);
	}
	
	
	
	public final JSResult seq(BiFunction<JSEnvironment,Object,Object> unaryOp, Object o1) {
		return RuntimeUtil.seq(env, unaryOp, o1);
	}
	public final JSResult seq(BiFunction<JSEnvironment,Object,Object> unaryOp, Object o1, JSResult result) {
		return RuntimeUtil.seq(env, unaryOp, o1, result);
	}
	public final JSResult seq(BiFunction<JSEnvironment,Object,Object> unaryOp, JSResult r1, JSResult result) {
		return RuntimeUtil.seq(env, unaryOp, r1, result);
	}
	public final JSResult seq(TriFunction<JSEnvironment,Object,Object,Object> binaryOp, Object o1, Object o2) {
		return RuntimeUtil.seq(env, binaryOp, o1, o2);
	}
	public final JSResult seq(TriFunction<JSEnvironment,Object,Object,Object> binaryOp, Object o1, Object o2, JSResult result) {
		return RuntimeUtil.seq(env, binaryOp, o1, o2, result);
	}
	public final JSResult seq(TriFunction<JSEnvironment,Object,Object,Object> binaryOp, JSResult r1, JSResult r2, JSResult result) {
		return RuntimeUtil.seq(env, binaryOp, r1, r2, result);
	}
	public final boolean seqCmp(TriPredicate<JSEnvironment,Object,Object> binaryOp, Object o1, Object o2, RuntimeUtil.MODE mode) {
		return RuntimeUtil.seqCmp(env, binaryOp, o1, o2, mode);
	}
	public final boolean seqCmp(TriPredicate<JSEnvironment,Object,Object> binaryOp, JSResult r1, JSResult r2, RuntimeUtil.MODE mode) {
		return RuntimeUtil.seqCmp(env, binaryOp, r1, r2, mode);
	}

	public final Iterator<String> keyIterator(Object o) {
		return RuntimeUtil.keyIterator(env, o);
	}
	public final Iterator<Map.Entry<Object,Object>> keyValueIterator(Object o) {
		return RuntimeUtil.keyValueIterator(env, o);
	}
	public final Iterator<Object> valueIterator(Object o) {
		return RuntimeUtil.valueIterator(env, o);
	}
	public final void iteratorClose(Iterator<?> it) {
		RuntimeUtil.iteratorClose(env, it);
	}
	public final void iteratorCloseQuietly(Iterator<?> it) {
		RuntimeUtil.iteratorCloseQuietly(env, it);
	}

	// A DERIVED class constructor's own EXPLICIT `return <value>;` (10.2.2
	// [[Construct]] step 13, applied once the value is already known - see
	// ASTReturn's identical-purpose comment for why this can't be done via
	// the value's own eager Java `return` the way the implicit/fall-through
	// case is): an object is returned as-is (even if `this` was never
	// initialized - test262 derived-class-return-override-with-object.js);
	// undefined falls back to GetThisBinding() (may throw
	// ReferenceError, via checkThisBinding); anything else (a primitive,
	// including null) is always a TypeError, regardless of `this`'s state.
	public final Object checkDerivedConstructorReturn(Object value, Object _this) {
		if(RuntimeUtil.isObject(env, value)) {
			return value;
		}
		if(value==RuntimeUtil.UNDEFINED) {
			return RuntimeUtil.checkThisBindingOnReturn(_this);
		}
		throw org.monflabs.galtajs.rt.ConstructResultError.invalidReturnValue();
	}



	public Object assign(VarAccessor var, Object value) {
		var.setValue(value);
		return value;
	}
	// Every assignXXX() below implements a COMPOUND operator's PutValue step
	// (spec: SetMutableBinding's "still exists?" check) - the "if(RuntimeUtil.
	// isStrictMode() && !var.stillExists())" guard right before the final
	// var.setValue() call mirrors ASTIdentifier.evaluateAssign()'s identical
	// interpreted-mode check. Needed because `var.getValue()` (or, for &&=/
	// ||=/??=, the short-circuit-guarded RHS Supplier) can run arbitrary user
	// code (a getter, or a `with`-object's own property access) that deletes
	// the very binding this assignment is about to write back to - a strict-
	// mode compound assignment must then throw ReferenceError rather than
	// silently recreating the binding (test262 language/expressions/
	// compound-assignment/compound-assignment-operator-calls-putvalue-lref--
	// v--*.js).
	public Object assignAdd(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.add(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignAnd(VarAccessor var, Object value) {
		Object v = var.getValue();
		if( !RuntimeUtil.toBoolean(env,v) ) {
			return v;
		}
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(value);
		return value;
	}
	// Lazy variant: the RHS must not be evaluated at all when the short-circuit
	// check means the assignment never happens.
	public Object assignAnd(VarAccessor var, Supplier<Object> value) {
		Object v = var.getValue();
		if( !RuntimeUtil.toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(rv);
		return rv;
	}
	public Object assignBitAnd(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.bitAnd(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignBitOr(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.bitOr(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignBitXor(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.bitXor(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignDiv(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.div(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignMod(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.mod(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignMul(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.mul(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignPower(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.power(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignNullCoalescing(VarAccessor var, Object value) {
		Object v = var.getValue();
		if( v!=null ) {
			return v;
		}
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(value);
		return value;
	}
	// See assignAnd(VarAccessor,Supplier) above.
	public Object assignNullCoalescing(VarAccessor var, Supplier<Object> value) {
		Object v = var.getValue();
		if( v!=null ) {
			return v;
		}
		Object rv = value.get();
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(rv);
		return rv;
	}
	public Object assignOr(VarAccessor var, Object value) {
		Object v = var.getValue();
		if( RuntimeUtil.toBoolean(env,v) ) {
			return v;
		}
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(value);
		return value;
	}
	// See assignAnd(VarAccessor,Supplier) above.
	public Object assignOr(VarAccessor var, Supplier<Object> value) {
		Object v = var.getValue();
		if( RuntimeUtil.toBoolean(env,v) ) {
			return v;
		}
		Object rv = value.get();
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(rv);
		return rv;
	}
	public Object assignLShift(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.lshift(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignRShift(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.rshift(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignRunShift(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.runshift(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	public Object assignSub(VarAccessor var, Object value) {
		Object v = var.getValue();
		Object vi = RuntimeUtil.sub(env,v,value);
		if(RuntimeUtil.isStrictMode() && !var.stillExists()) {
			throw RuntimeUtil.referenceError("{0} is not defined", var.getKey());
		}
		var.setValue(vi);
		return vi;
	}
	
	
	public final Object assign(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assign(env, leftValue, member, value);
	}
	// SuperProperty PutValue (spec 6.2.5.6 PutValue step 6b / 13.3.7.1): the
	// search for an existing property starts at the home object's prototype
	// (leftValue), but the actual [[Set]] - creating a new own property when
	// nothing is found, or invoking an inherited setter - must target the
	// real `this`, not that prototype. Mirrors ASTMember/ASTArrayMember's own
	// isSuper evaluateAssign() branch in the interpreter. Only plain "="
	// assignment reaches this - super's compound-assignment forms (+=, ...)
	// aren't exercised by any currently-passing test and stay on the generic,
	// receiver-unaware assignAdd/etc. path.
	public final Object assignWithThis(Object leftValue, Object member, Object value, Object thisArg) {
		RuntimeUtil.setPropertyWithReceiver(env, leftValue, member, value, thisArg);
		return value;
	}
	public final Object assignAdd(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignAdd(env, leftValue, member, value);
	}
	public final Object assignAdd(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignAdd(env, leftValue, member, value);
	}
	public final Object assignAnd(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignAnd(env, leftValue, member, value);
	}
	public final Object assignAnd(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignAnd(env, leftValue, member, value);
	}
	public final Object assignBitAnd(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignBitAnd(env, leftValue, member, value);
	}
	public final Object assignBitAnd(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignBitAnd(env, leftValue, member, value);
	}
	public final Object assignBitOr(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignBitOr(env, leftValue, member, value);
	}
	public final Object assignBitOr(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignBitOr(env, leftValue, member, value);
	}
	public final Object assignBitXor(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignBitXor(env, leftValue, member, value);
	}
	public final Object assignBitXor(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignBitXor(env, leftValue, member, value);
	}
	public final Object assignDiv(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignDiv(env, leftValue, member, value);
	}
	public final Object assignDiv(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignDiv(env, leftValue, member, value);
	}
	public final Object assignMod(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignMod(env, leftValue, member, value);
	}
	public final Object assignMod(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignMod(env, leftValue, member, value);
	}
	public final Object assignMul(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignMul(env, leftValue, member, value);
	}
	public final Object assignMul(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignMul(env, leftValue, member, value);
	}
	public final Object assignPower(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignPower(env, leftValue, member, value);
	}
	public final Object assignPower(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignPower(env, leftValue, member, value);
	}
	public final Object assignNullCoalescing(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignNullCoalescing(env, leftValue, member, value);
	}
	public final Object assignNullCoalescing(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignNullCoalescing(env, leftValue, member, value);
	}
	public final Object assignOr(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignOr(env, leftValue, member, value);
	}
	public final Object assignOr(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignOr(env, leftValue, member, value);
	}
	public final Object assignLShift(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignLShift(env, leftValue, member, value);
	}
	public final Object assignLShift(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignLShift(env, leftValue, member, value);
	}
	public final Object assignRShift(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignRShift(env, leftValue, member, value);
	}
	public final Object assignRShift(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignRShift(env, leftValue, member, value);
	}
	public final Object assignRunShift(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignRunShift(env, leftValue, member, value);
	}
	public final Object assignRunShift(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignRunShift(env, leftValue, member, value);
	}
	public final Object assignSub(Object leftValue, Object member, Object value) {
		return RuntimeUtil.assignSub(env, leftValue, member, value);
	}
	public final Object assignSub(Object leftValue, Object member, Supplier<Object> value) {
		return RuntimeUtil.assignSub(env, leftValue, member, value);
	}

	public final Object preInc(Object leftValue, Object member, Object unused) {
		return RuntimeUtil.preInc(env, leftValue, member, unused);
	}
	public final Object postInc(Object leftValue, Object member, Object unused) {
		return RuntimeUtil.postInc(env, leftValue, member, unused);
	}
	public final Object preDec(Object leftValue, Object member, Object unused) {
		return RuntimeUtil.preDec(env, leftValue, member, unused);
	}
	public final Object postDec(Object leftValue, Object member, Object unused) {
		return RuntimeUtil.postDec(env, leftValue, member, unused);
	}

	// Private (#name) counterparts - see RuntimeUtil.private* for the actual
	// logic; ctx (not the instance's own env field) is what lets these resolve
	// the enclosing class evaluation's PrivateName token.
	public final Object privateGet(JSRuntimeContext ctx, Object instance, String member) {
		return RuntimeUtil.privateGet(ctx, instance, member);
	}
	public final boolean privateIn(JSRuntimeContext ctx, Object rightValue, String member) {
		return RuntimeUtil.privateIn(ctx, rightValue, member);
	}
	public final Object privateAssign(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssign(ctx, leftValue, member, value);
	}
	public final Object privateAssignAdd(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignAdd(ctx, leftValue, member, value);
	}
	public final Object privateAssignAnd(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		return RuntimeUtil.privateAssignAnd(ctx, leftValue, member, value);
	}
	public final Object privateAssignBitAnd(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignBitAnd(ctx, leftValue, member, value);
	}
	public final Object privateAssignBitOr(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignBitOr(ctx, leftValue, member, value);
	}
	public final Object privateAssignBitXor(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignBitXor(ctx, leftValue, member, value);
	}
	public final Object privateAssignDiv(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignDiv(ctx, leftValue, member, value);
	}
	public final Object privateAssignMod(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignMod(ctx, leftValue, member, value);
	}
	public final Object privateAssignMul(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignMul(ctx, leftValue, member, value);
	}
	public final Object privateAssignPower(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignPower(ctx, leftValue, member, value);
	}
	public final Object privateAssignNullCoalescing(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		return RuntimeUtil.privateAssignNullCoalescing(ctx, leftValue, member, value);
	}
	public final Object privateAssignOr(JSRuntimeContext ctx, Object leftValue, String member, Supplier<Object> value) {
		return RuntimeUtil.privateAssignOr(ctx, leftValue, member, value);
	}
	public final Object privateAssignLShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignLShift(ctx, leftValue, member, value);
	}
	public final Object privateAssignRShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignRShift(ctx, leftValue, member, value);
	}
	public final Object privateAssignRunShift(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignRunShift(ctx, leftValue, member, value);
	}
	public final Object privateAssignSub(JSRuntimeContext ctx, Object leftValue, String member, Object value) {
		return RuntimeUtil.privateAssignSub(ctx, leftValue, member, value);
	}
	public final Object privatePreInc(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		return RuntimeUtil.privatePreInc(ctx, leftValue, member, unused);
	}
	public final Object privatePostInc(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		return RuntimeUtil.privatePostInc(ctx, leftValue, member, unused);
	}
	public final Object privatePreDec(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		return RuntimeUtil.privatePreDec(ctx, leftValue, member, unused);
	}
	public final Object privatePostDec(JSRuntimeContext ctx, Object leftValue, String member, Object unused) {
		return RuntimeUtil.privatePostDec(ctx, leftValue, member, unused);
	}


	
	

	public Object incNumber(Object o1) {
		return RuntimeUtil.incNumber(env, o1);
	}
	public Object decNumber(Object o1) {
		return RuntimeUtil.decNumber(env, o1);
	}

	public Object preInc(VarAccessor var) {
		Object val = var.getValue();
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.incrementExact(env,i.intValue());
			var.setValue(vi);
			return vi;
		}
		Object vi = RuntimeUtil.incNumber(env,val);
		var.setValue(vi);
		return vi;
	}
	public Object postInc(VarAccessor var) {
		Object val = var.getValue();
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.incrementExact(env,i.intValue());
			var.setValue(vi);
			return val;
		}
		Object v = RuntimeUtil.toNumeric(env,val);
		Object vi = RuntimeUtil.incNumber(env,v);
		var.setValue(vi);
		return v;
	}

	public Object preDec(VarAccessor var) {
		Object val = var.getValue();
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.decrementExact(env,i.intValue());
			var.setValue(vi);
			return vi;
		}
		Object vi = RuntimeUtil.decNumber(env,val);
		var.setValue(vi);
		return vi;
	}
	public Object postDec(VarAccessor var) {
		Object val = var.getValue();
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.decrementExact(env,i.intValue());
			var.setValue(vi);
			return val;
		}
		Object v = RuntimeUtil.toNumeric(env,val);
		Object vi = RuntimeUtil.decNumber(env,v);
		var.setValue(vi);
		return v;
	}

	// Direct-field variants for local JSVar — bypass VarAccessor interface dispatch.
	public Object preIncVar(JSVar var) {
		Object val = var.value;
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.incrementExact(env,i.intValue());
			var.value = vi;
			return vi;
		}
		Object vi = RuntimeUtil.incNumber(env,val);
		var.value = vi;
		return vi;
	}
	public Object postIncVar(JSVar var) {
		Object val = var.value;
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.incrementExact(env,i.intValue());
			var.value = vi;
			return val;
		}
		Object v = RuntimeUtil.toNumeric(env,val);
		Object vi = RuntimeUtil.incNumber(env,v);
		var.value = vi;
		return v;
	}
	public Object preDecVar(JSVar var) {
		Object val = var.value;
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.decrementExact(env,i.intValue());
			var.value = vi;
			return vi;
		}
		Object vi = RuntimeUtil.decNumber(env,val);
		var.value = vi;
		return vi;
	}
	public Object postDecVar(JSVar var) {
		Object val = var.value;
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			Object vi = RuntimeUtil.decrementExact(env,i.intValue());
			var.value = vi;
			return val;
		}
		Object v = RuntimeUtil.toNumeric(env,val);
		Object vi = RuntimeUtil.decNumber(env,v);
		var.value = vi;
		return v;
	}
	
	// See preIncVar(JSVar)/postIncVar(JSVar)/etc's matching Integer fast
	// path above - mirrored here for the Object[]-backed slot variant
	// (closure-captured/hoisted locals), used just as hotly by e.g. a
	// transpiled for(let i=0;...;i++) loop.
	public Object preIncVar(Object[] vars, int index) {
		Object val = vars[index];
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			return vars[index] = RuntimeUtil.incrementExact(env,i.intValue());
		}
		return vars[index] = RuntimeUtil.incNumber(env,val);
	}
	public Object postIncVar(Object[] vars, int index) {
		Object val = vars[index];
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			vars[index] = RuntimeUtil.incrementExact(env,i.intValue());
			return val;
		}
		// Needs to be converted so it returns a number
		Object v = RuntimeUtil.toNumeric(env,val);
		vars[index] = RuntimeUtil.incNumber(env,v);
		return v;
	}
	public Object preDecVar(Object[] vars, int index) {
		Object val = vars[index];
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			return vars[index] = RuntimeUtil.decrementExact(env,i.intValue());
		}
		return vars[index] = RuntimeUtil.decNumber(env,val);
	}
	public Object postDecVar(Object[] vars, int index) {
		Object val = vars[index];
		if(val instanceof Integer i && !RuntimeUtil.isBoxedNumber(env,val)) {
			vars[index] = RuntimeUtil.decrementExact(env,i.intValue());
			return val;
		}
		// Needs to be converted so it returns a number
		Object v = RuntimeUtil.toNumeric(env,val);
		vars[index] = RuntimeUtil.decNumber(env,v);
		return v;
	}

	// Plain assignment to a let-typed slot: a real method call (not an inline
	// ternary) so `value`'s own expression is always evaluated first, matching
	// spec's evaluate-RHS-then-PutValue order. See ASTIdentifier.
	// getIdentifierWriteAccessor's LET branch.
	public Object putValueTDZCheck(Object[] vars, int index, Object value, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		return vars[index] = value;
	}

	// TDZ-checked variants of the four preIncVar/postIncVar/preDecVar/postDecVar
	// slot-array overloads above, for a let-typed target (see ASTIdentifier.
	// transpileJavaAssignment's PREINC/POSTINC/PREDEC/POSTDEC fast branches).
	public Object preIncVarChecked(Object[] vars, int index, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		return preIncVar(vars, index);
	}
	public Object postIncVarChecked(Object[] vars, int index, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		return postIncVar(vars, index);
	}
	public Object preDecVarChecked(Object[] vars, int index, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		return preDecVar(vars, index);
	}
	public Object postDecVarChecked(Object[] vars, int index, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		return postDecVar(vars, index);
	}

	// ++/-- (pre or post) on a const/using-typed slot - see ASTIdentifier's
	// PREINC/POSTINC/PREDEC/POSTDEC cases, which route a const/using target
	// here instead of any of the four Var(Checked) methods above (none of
	// which check immutability at all - confirmed via a genuine infinite
	// loop: `for (const i = 0; i < 1; i++) {}` never threw, so `i` climbed
	// past 1 as a real Java double despite the optimizer's own constant-
	// folding of the loop test believing `i` could never change). TDZ still
	// takes precedence (GetValue, which ++/-- performs first per spec, would
	// itself throw ReferenceError before PutValue's own immutable-binding
	// TypeError is ever reached).
	public Object incDecVarReadOnly(Object[] vars, int index, String name) {
		if(vars[index]==RuntimeUtil.TDZ) {
			throw RuntimeUtil.referenceError("Cannot access '{0}' before initialization", name);
		}
		throw RuntimeUtil.typeError("Assignment to constant variable '{0}'.", name);
	}

	
	// Short circuit operators
	public boolean andTest(Object val) {
		return !RuntimeUtil.toBoolean(env,val);
	}
	public boolean coalescingTest(Object val) {
		return !RuntimeUtil.isNullOrUndefined(val);
	}
	public boolean orTest(Object val) {
		return RuntimeUtil.toBoolean(env,val);
	}
	
	
	
	
	//
	//
	// TranspilerRuntimeUtil
	//
	//
	
	
	/////////////////////////////////////////////////////////////////////////
	// void operator
	/////////////////////////////////////////////////////////////////////////

	public static Object void_(Object v) {
		return RuntimeUtil.UNDEFINED;
	}	
	
	public static void statement(Object v) {
	}	
	public static Object comma(Object...exprs) {
		if(exprs.length>0) {
			return exprs[exprs.length-1];
		}
		return null;
	}   

	public static JSResult asResult(Object value) {
		if(value instanceof JSResult r) {
			return r;
		}
		return new JSResult(value);
	}

	
	
	/////////////////////////////////////////////////////////////////////////
	// Object literal helper
	/////////////////////////////////////////////////////////////////////////

	//
	// CallUtil
	//
	
	public static Object applyNotNullOrUndefined(Object v, Function<Object,Object> callback) {
		if(RuntimeUtil.isNotNullOrUndefined(v)) {
			return callback.apply(v);
		}
		return RuntimeUtil.UNDEFINED;
	}

	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object[] parameters) {
		// TODO: This seems redundant with RuntimeUtil.call()
		// Also, we do not need the context, just the environment
		if(function instanceof Callable c) {
			return c.call(RuntimeUtil.UNDEFINED, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// Arity-N invokeFunction overloads: transpiled call sites emit these directly
	// (instead of `new Object[]{...}`) when the call has <= Callable.MAX_DIRECT_ARITY
	// positional args and no spread. Each routes to the matching Callable.call(...) arity,
	// which BuiltinFunctionTranspiler overrides to hit the direct-arg fast path.
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8, p9);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeFunction(JSTranspiledRuntimeContext context, Object function, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		if(function instanceof Callable c) return c.call(RuntimeUtil.UNDEFINED, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
		throw RuntimeUtil.typeError("Object {0} is not a callable", RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object[] parameters) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) {
			return c.call(base, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// Arity-N invokeMethod (String) overloads - same pattern as invokeFunction above.
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5, p6);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5, p6, p7);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5, p6, p7, p8);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5, p6, p7, p8, p9);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(base, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// Per spec, Call()'s IsCallable check happens strictly AFTER
	// ArgumentListEvaluation, but the property resolution that locates the
	// callee (RuntimeUtil.getProperty - which may itself throw for a
	// null/undefined base, or trigger arbitrary JS execution via a Proxy
	// `get` trap or an accessor getter) must complete BEFORE the call's own
	// argument list is evaluated (test262 language/expressions/call/
	// 11.2.3-3_3.js: `o.bar.gar(foo())` must never call foo() when `o.bar`
	// is undefined). Bundles [base,function] into a single Object[] so a call site
	// (ASTCall.transpileChainingNode) can nest this call directly as the
	// first positional argument to invokeResolvedMethod(...), one plain
	// Java sub-expression that runs textually - and therefore, via Java's
	// own left-to-right argument evaluation - before the arguments below.
	// Unlike the reverted TEMP_VAR-based attempt (a single shared field,
	// clobbered whenever nested execution triggered by THIS resolution
	// itself performed another method call before the field was read
	// back), the returned array is just this call's own ordinary return
	// value - no shared mutable state of any kind, so every nested call
	// (including a reentrant one) gets its own independent invocation
	// with no risk of clobbering another's result.
	// A call in tail position (see TailCall): a JavaScript function is
	// returned as a TailCall, performed by the function returning it
	public static Object tailInvokeFunction(JSTranspiledRuntimeContext context, Object function, Object[] parameters) {
		org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(function, RuntimeUtil.UNDEFINED, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		if(tc!=null) {
			return tc;
		}
		return invokeFunction(context, function, parameters);
	}
	public static Object tailInvokeEvalCandidate(JSTranspiledRuntimeContext context, Object function, Object[] parameters, Object evalParameter) {
		org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(function, RuntimeUtil.UNDEFINED, parameters);
		if(tc!=null) {
			return tc;
		}
		Object[] all = java.util.Arrays.copyOf(parameters, parameters.length+1);
		all[parameters.length] = evalParameter;
		return invokeFunction(context, function, all);
	}
	public static Object tailInvokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object[] parameters) {
		Object[] r = (Object[])resolved;
		org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(r[1], r[0], parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		if(tc!=null) {
			return tc;
		}
		return invokeResolvedMethod(context, resolved, method, parameters);
	}

	public static Object[] resolveMethodTarget(JSTranspiledRuntimeContext context, Object base, String method) {
		return new Object[]{base, RuntimeUtil.getProperty(context.getEnvironment(), base, method)};
	}

	// Deferred instanceof Callable check only - the property resolution
	// (and its own possible throw/side effects) already ran, before the
	// arguments below, inside resolveMethodTarget - see its own doc.
	// `resolved` is typed Object (not Object[]) to match this method's
	// arity-N overloads below; cast back here.
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object[] parameters) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) {
			return c.call(r[0], parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	// Arity-N invokeResolvedMethod overloads - same direct-arg fast path as
	// invokeMethod above (see ASTBaseCall.transpileParams' allowDirect).
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0]);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5, p6);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5, p6, p7);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5, p6, p7, p8);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5, p6, p7, p8, p9);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeResolvedMethod(JSTranspiledRuntimeContext context, Object resolved, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		Object[] r = (Object[])resolved;
		Object function = r[1];
		if(function instanceof Callable c) return c.call(r[0], p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// Private (#name) method call (obj.#method(...)) - a MemberExpression's
	// PrivateIdentifier is never reachable via `super` (not valid grammar), so
	// unlike invokeMethod above, one Object[]-based overload covers every call
	// site (see ASTCall.transpileChainingNode); no arity-N fast path needed.
	public static Object privateInvokeMethod(JSRuntimeContext context, Object base, String method, Object[] parameters) {
		Object function = RuntimeUtil.privateGet(context, base, method);
		if(function instanceof Callable c) {
			return c.call(base, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// SuperProperty: the lookup base is the [[HomeObject]]'s prototype, but
	// `this` for the call must remain the current `this`, per spec.
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object[] parameters) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) {
			return c.call(thisArg, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5, p6);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5, p6, p7);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5, p6, p7, p8);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5, p6, p7, p8, p9);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, String method, Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		if(function instanceof Callable c) return c.call(thisArg, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10);
		throw RuntimeUtil.typeError("Method {0} is not a callable, {1}", method, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// Only support single index for now
	public static Object invokeMethod(JSTranspiledRuntimeContext context, Object base, Object index, Object[] parameters) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, index);
		if(function instanceof Callable c) {
			return c.call(base, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Index {0} is not a callable, {1}", index, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	// SuperProperty (computed): see invokeMethodWithThis(...,String,...) above.
	public static Object invokeMethodWithThis(JSTranspiledRuntimeContext context, Object base, Object thisArg, Object index, Object[] parameters) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, index);
		if(function instanceof Callable c) {
			return c.call(thisArg, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
		}
		throw RuntimeUtil.typeError("Index {0} is not a callable, {1}", index, RuntimeUtil.objectTypeName(context.getEnvironment(),function));
	}

	@SuppressWarnings("serial")
	public static class ParamBuilder extends ArrayList<Object> {
		private JSRuntimeContext context;
		private ParamBuilder(JSRuntimeContext context, int initialSize) {
			super(initialSize);
			this.context = context;
		}
		public ParamBuilder value(Object v) {
			add(v);
			return this;
		}
		public ParamBuilder spread(Object v) {
			// Spreading null/undefined is a TypeError (valueIterator throws it)
			{
				Iterator<Object> it=RuntimeUtil.valueIterator(context.getEnvironment(), v);
				if(it!=null) {
					while(it.hasNext()) {
						add(it.next());
					}
				} else {
					throw RuntimeUtil.typeError("Spread syntax requires an iterable");
				}
			}
			return this;
		}
	}
	
	public static ParamBuilder paramBuilder(JSRuntimeContext context, int initialSize) {
		return new ParamBuilder(context,initialSize);
	}

	
	//
	// String template
	// 
	public static Object templateTagFunction(JSRuntimeContext context, Callable callable, Object...parameters) {
		// A tag that isn't reached through a member access gets undefined "this",
		// like any other plain function call.
		return callable.call(RuntimeUtil.UNDEFINED, parameters);
	}

	// A tag reached through a member access (obj.tag`...`) binds "this" to the base
	// object, exactly like a normal method call. (Named distinctly from
	// templateTagFunction to avoid varargs overload ambiguity with it.)
	public static Object templateTagMethod(JSTranspiledRuntimeContext context, Object base, String method, Object...parameters) {
		return invokeMethod(context, base, method, parameters);
	}
	public static Object templateTagMethod(JSTranspiledRuntimeContext context, Object base, Object index, Object...parameters) {
		return invokeMethod(context, base, index, parameters);
	}
	// A tagged template in tail position - see tailInvokeFunction()
	public static Object tailTemplateTagFunction(JSRuntimeContext context, Callable callable, Object...parameters) {
		org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(callable, RuntimeUtil.UNDEFINED, parameters);
		if(tc!=null) {
			return tc;
		}
		return callable.call(RuntimeUtil.UNDEFINED, parameters);
	}
	public static Object tailTemplateTagMethod(JSTranspiledRuntimeContext context, Object base, String method, Object...parameters) {
		Object function = RuntimeUtil.getProperty(context.getEnvironment(), base, method);
		org.monflabs.galtajs.rt.TailCall tc = org.monflabs.galtajs.rt.TailCall.create(function, base, parameters);
		if(tc!=null) {
			return tc;
		}
		return invokeMethod(context, base, method, parameters);
	}
	// GetTemplateObject (spec): the SAME tagged-template literal (the same
	// call site, i.e. the same Template Literal parse node) must produce
	// the IDENTICAL (===) template object on every evaluation - a cache
	// keyed by call site, not by closure instance (two separate closure
	// instances of the same inner function, from two separate calls to an
	// outer function, still share the SAME cached object for a template
	// literal in the inner function's body - confirmed via test262
	// language/expressions/tagged-template/cache-different-functions-same-site.js).
	// cacheId is a compile-time-unique int per template-literal AST node
	// (ASTStringTemplate.transpileJavaExpression's own generateUniqueId()),
	// NOT per source text - two textually-identical tagged templates at
	// different positions get different cache entries, matching spec's
	// per-node (not per-content) semantics.
	private Map<Integer,JSArray> templateObjectCache;
	public JSArray templateStrings(int cacheId, Object[] strings) {
		return templateStrings(cacheId, strings,null);
	}
	public JSArray templateStrings(int cacheId, Object[] strings, Object[] rawStrings) {
		if(templateObjectCache==null) {
			templateObjectCache = new java.util.HashMap<>();
		}
		JSArray cached = templateObjectCache.get(cacheId);
		if(cached!=null) {
			return cached;
		}
		JSArray a = JSArray.create(getEnvironment());
		for(int i=0; i<strings.length; i++) {
			a.arrayAdd(strings[i]);
		}
		if(rawStrings!=null) {
			JSArray raw = JSArray.create(getEnvironment());
			for(int i=0; i<rawStrings.length; i++) {
				raw.arrayAdd(rawStrings[i]);
			}
			// "raw" is non-enumerable (per GetTemplateObject); freeze() then makes it
			// (and every array index, on both arrays) non-writable/non-configurable.
			a.getMembers(true).setOwnProperty("raw", raw, PropertyDescriptor.DESC_READONLY_HIDDEN_PROP);
			raw.freeze();
			a.freeze();
		}
		templateObjectCache.put(cacheId, a);
		return a;
	}


	//
	// NewUtil
	//
	public final Object newObject(Object value) {
		return RuntimeUtil.constructObject(env, value, RuntimeUtil.EMPTY_PARAMS);
	}
	public final Object newObject(Object value, Object parameters[]) {
		return RuntimeUtil.constructObject(env, value, parameters!=null ? parameters : RuntimeUtil.EMPTY_PARAMS);
	}

	public final Object newArray(Object value, int dimensions, Object sizeObject) {
		int size = RuntimeUtil.toInt32(env,sizeObject);
		return RuntimeUtil.constructArray(env, value, dimensions, size);
	}

	public static Object newTarget(JSTranspiledRuntimeContext context) {
		JSFunctionContext functionContext = context.getFunctionContext();
		if(functionContext!=null) {
			Constructor ctor = functionContext.getNewTarget();
			return ctor!=null ? ctor : RuntimeUtil.UNDEFINED;
		} else {
			return RuntimeUtil.UNDEFINED;
		}
	}
	
	
	//
	// Sequences
	//
	public static Object deref(JSResult r) {
		return r.deref();
	}

	
	//
	// START
	// 
	
	public static class ArrayRange {
		Integer start;
		Integer end;
		Integer step;
		private ArrayRange(Integer start, Integer end, Integer step) {
			this.start = start;
			this.end = end;
			this.step = step;
		}
	}
	public static ArrayRange range(Integer start, Integer end, Integer step) {
		return new ArrayRange(start,end,step);
	}
	
	public final JSResult memberSeq(JSResult result, boolean deepscan, String memberName) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			Object val = RuntimeUtil.getProperty(env, base, memberName, RuntimeUtil.NOT_AVAILABLE); 
			if(val!=RuntimeUtil.NOT_AVAILABLE) {
				res.addToSequence(getEnvironment(),val);
			}
		});
		return result;
	}
	@SafeVarargs
	public final JSResult memberSeq(JSResult result, boolean deepscan, Function<Object,Object>...members) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			for(int i=0; i<members.length; i++) {
				Object index = members[i].apply(base);
				addSequenceMember(base, index, false, (member) -> { 
					if((base instanceof CharSequence) && (index instanceof Number) ) {
						return;
					}
					Object v = RuntimeUtil.getProperty(env, base, member, RuntimeUtil.NOT_AVAILABLE);
					if(v!=RuntimeUtil.NOT_AVAILABLE) {
						res.addToSequence(getEnvironment(),v);
					}
				});
			}
		});
		return result;
	}
	
	public final JSResult memberTypeofSeq(JSResult result, boolean deepscan, String memberName) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			addSequenceMember(base, memberName, false, (member) -> { 
				Object v = RuntimeUtil.getProperty(env, base, member);
				res.addToSequence(getEnvironment(),RuntimeUtil.typeof(env,v));
			});
		});
		return result;
	}
	@SafeVarargs
	public final JSResult memberTypeofSeq(JSResult result, boolean deepscan, Function<Object,Object>...members) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			for(int i=0; i<members.length; i++) {
				Object idx = members[i].apply(base);
				addSequenceMember(base, idx, false, (member) -> { 
					if((base instanceof CharSequence) && (idx instanceof Number) ) {
						return;
					}
					Object v = RuntimeUtil.getProperty(env, base, member);
					res.addToSequence(getEnvironment(),RuntimeUtil.typeof(env,v));
				});
			}
		});
		return result;
	}
	
	public final boolean memberDeleteSeq(JSResult result, boolean deepscan, String memberName) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		AtomicBoolean res = new AtomicBoolean(true);
		result.forEach( (base) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			addSequenceMember(base, memberName, true, (member) -> { 
				boolean v = RuntimeUtil.deleteProperty(env, base, member);
				if(!v) {
					res.set(false);
				}
			});
		});
		return res.get();
	}
	@SafeVarargs
	public final boolean memberDeleteSeq(JSResult result, boolean deepscan, Function<Object,Object>...members) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		AtomicBoolean res = new AtomicBoolean(true);
		result.forEach( (base) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			for(int i=0; i<members.length; i++) {
				Object idx = members[i].apply(base);
				addSequenceMember(base, idx, true, (member) -> { 
					if((base instanceof CharSequence) && (idx instanceof Number) ) {
						res.set(true);
						return;
					}
					boolean v = RuntimeUtil.deleteProperty(env, base, member);
					if(!v) {
						res.set(false);
					}
				});
			}
		});
		return res.get();
	}

	public final JSResult assignMemberSeq(JSResult result, Object value, MemberAssigner valueProvider, boolean deepscan, Object member) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			// String should be be available this way in JsonPath
			if((base instanceof CharSequence) && (member instanceof Number) ) {
				return;
			}
			if(deepscan) {
				// Only when the property already exst
				Object v = RuntimeUtil.getProperty(env, base, member, RuntimeUtil.NOT_AVAILABLE);
				if(v!=RuntimeUtil.NOT_AVAILABLE) {
					Object val = valueProvider.apply(env, base, member, value);
					res.addToSequence(getEnvironment(),val);
				}
			} else {
				Object val = valueProvider.apply(env, base, member, value);
				res.addToSequence(getEnvironment(),val);
			}
		});
		return result;
	}
	@SafeVarargs
	public final JSResult assignMemberSeq(JSResult result, Object value, MemberAssigner valueProvider, boolean deepscan, Function<Object,Object>...members) {
		if(deepscan) {
			result.reduceToSequence( (v,res) -> {
				RuntimeUtil.deepScan(getEnvironment(),v,res);
			});
		}
		
		result.reduceToSequence( (base,res) -> {
			if(base==null) {
				return; // No-op with sequences
			}
			for(int i=0; i<members.length; i++) {
				Object idx = members[i].apply(base);
				addSequenceMember(base, idx, true, (member) -> {
					// String should be be available this way in JsonPath
					if((base instanceof CharSequence) && (idx instanceof Number) ) {
						return;
					}
					if(deepscan) {
						Object v = RuntimeUtil.getProperty(env, base, member, RuntimeUtil.NOT_AVAILABLE);
						if(v!=RuntimeUtil.NOT_AVAILABLE) {
							valueProvider.apply(env, base, member, value);
							res.addToSequence(getEnvironment(),value);
						}
					} else {
						valueProvider.apply(env, base, member, value);
						res.addToSequence(getEnvironment(),value);
					}
				});
			}
		});
		return result;
	}

	private final void addSequenceMember(Object base, Object index, boolean forUpdate, Consumer<Object> action) {
		if(index instanceof ArrayRange range) {
			Object start=null, end=null, step=null;
			if(range.start!=null) { // start can be null with [:]
				start = range.start;
				if(start!=null && !(start instanceof Number)) {
					throw RuntimeUtil.typeError("Start range is not a number, {0}", start);
				}
			}
			if(range.end!=null) {
				end = range.end;
				if(end!=null && !(end instanceof Number)) {
					throw RuntimeUtil.typeError("End range is not a number, {0}", end);
				}
			}
			if(range.step!=null) {
				step = range.step;
				if(step!=null && !(step instanceof Number)) {
					throw RuntimeUtil.typeError("Step is not a number, {0}", end);
				}
			}

			JSArray list = RuntimeUtil.getArrayLikeUnchecked(env, base, true);
			if(list!=null) {
				long ti = step!=null ? RuntimeUtil.toLong(env,step) : 1;
				if(ti==0) {
					throw RuntimeUtil.rangeError("Step cannot be zero, {0}", step);
				}
				// Note: the default value of si and ei depends on ti
				// If ti<0, then we should inverse them as ut starts from the end
				long size = list.arrayLength();
				long si = start!=null ? RuntimeUtil.toLong(env,start) : (ti>0?0:size);
				if(si<0) {
					si = Math.max( 0, size + si);
				}
				long ei = end!=null ? RuntimeUtil.toLong(env,end) : (ti>0?size:0);
				if(ei<0) {
					ei = Math.max( 0, size + ei);
				}
				if(!forUpdate) {
					// We optimize read as there is no need to check out side of the boundaries
					// For update, we can create indexes outside of the existing range
					si = Math.min(si, size);
					ei = Math.min(ei, size);
				}
				if(ti>0) {
					for(long i=si; i<ei; i+=ti) {
						action.accept(i); 
					}
				} else {
					for(long i=si; i>=ei; i+=ti) {
						action.accept(i); 
					}				
				}
			}
		} else {
			if(index instanceof Number n) {
				long idx = n.longValue();
				if(idx<0) {
					JSArray list = RuntimeUtil.getArrayLike(env, base);
					index = idx + list.arrayLength();
				}
			}
			action.accept(index); 
		}
	}
	
	public static JSResult seqInvokeMethod(JSRuntimeContext context, JSResult result, String methodName) {
		return seqInvokeMethod(context,result,methodName,RuntimeUtil.EMPTY_PARAMS);
	}
	public static JSResult seqInvokeMethod(JSRuntimeContext context, JSResult result, String methodName, Object[] params) {
		result.reduceToSequence( (base,res) -> {
			Object method = RuntimeUtil.getProperty(context.getEnvironment(), base, methodName); 
			if(method instanceof Callable callable) {
				Object v = callable.call(base, params);
				res.addToSequence(context.getEnvironment(),v);
			} else {
				// Silently fail in sequences?
			}
		});
		return result;
	}

    
    //
    // ArrayFilter
    //

    public static JSResult seqArrayFilter(JSRuntimeContext context, JSResult result, Function<Object,Object> cond) {
    	JSResult _seq = result.ejectAndSequence(); 
    	RuntimeUtil._arrayFilter(context, _seq, cond, (base,index,getter,setter,deleter) -> {
    		Object v = getter.get();
    		if(v!=RuntimeUtil.NOT_AVAILABLE) {
    			result.addToSequence(context.getEnvironment(),v);
    		}
    	}, false);
    	return result;
    }
    public static JSResult seqArrayFilterAssign(JSRuntimeContext context, JSResult result, Function<Object,Object> cond, Object value, MemberAssigner valueProvider) {
    	JSResult _seq = result.ejectAndSequence(); 
    	RuntimeUtil._arrayFilter(context, _seq, cond, (base,index,getter,setter,deleter) -> {
			Object newValue = valueProvider.apply(context.getEnvironment(),base,index,value);
			if(newValue!=RuntimeUtil.NOT_AVAILABLE) {
				result.addToSequence(context.getEnvironment(),newValue);
			}
    	}, false);
    	return result;
    }	

    public final JSResult arrayDeepScan(JSResult result) {
    	return RuntimeUtil.arrayDeepScan(env, result);
    }

    public final JSResult arrayFlatten(JSResult result, boolean deepscan) {
    	return RuntimeUtil.arrayFlatten(env, result, deepscan);
    }
}
