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
package org.monflabs.galtajs.rt;

import java.io.PrintStream;

import org.monflabs.galtajs.JSModule;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.jsonfactory.JSValue;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.modules.JSScriptUnit;
import org.monflabs.galtajs.rt.builtins.GlobalThis;
import org.monflabs.galtajs.rt.builtins.standard.performance.PerformanceData;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 * Contains the globals used by the execution context.
 * 
 * A global context can be reused across expressions so it does not have a reference to the module.
 * Should we separate this global context from the code MainContext?
 * Or should the module change when this is executed>
 */
public interface JSGlobalContext extends JSModuleContext {
	
	public static enum RunningState {
		STOPPED, RUNNING, SUSPENDED, STOPPING,
	}
	public RunningState getRunningState();
	public void setRunningState(RunningState runningState);
	
	// Should it be added the unit context??
	public void setScriptUnit(JSScriptUnit scriptUnit);
	
	public PerformanceData getPerformanceData();

	public void checkInterrupted();

	public PrintStream getOutStream();
	public PrintStream getErrStream();
	public void setOutStream(PrintStream out);
	public void setErrStream(PrintStream err);
	
	public Object getProperty(String name);
	public JSGlobalContext putProperty(String name, Object value);
	public JSGlobalContext removeProperty(String name);
	
	public boolean isEvalExecution();
	public void setEvalExecution(boolean eval);

	public JSExecutor getExecutor();

	public GlobalThis getGlobalThis();
	

	/////////////////////////////////////////////////////////////////////////
	// Modules
	/////////////////////////////////////////////////////////////////////////

	public JSModule importModule(JSUnitContext unitContext, String name);

	// Import/export attributes (`with { type: "json" }`) - a SEPARATE
	// entry point from importModule() above, rather than an overload of
	// it, since an attributed import resolves to a fundamentally
	// different KIND of module record (a synthetic one, per-spec
	// ParseJSONModule - default export only, no named exports, no JS
	// parsing at all) cached under a DIFFERENT key than a plain-JS import
	// of the same specifier would use (the same path imported both
	// attributed and un-attributed are, per spec, two DISTINCT Module
	// Records). Default (for any JSGlobalContext implementation that
	// doesn't support this - e.g. the transpiled backend, a separate,
	// not-yet-attempted effort) throws, matching "this host doesn't
	// support this attribute" per spec HostGetSupportedImportAttributes.
	public default JSModule importAttributedModule(JSUnitContext unitContext, String name, java.util.Map<String,String> attributes) {
		throw RuntimeUtil.typeError("Import attributes are not supported by this module runtime");
	}

	// Source-phase import (`import source x from '...'` / `import.source()`,
	// source-phase-imports proposal): the Module Source Object of the named
	// module, WITHOUT loading, linking or evaluating it. Throws a SyntaxError
	// for a module kind that has no source-phase representation (a JS
	// source text module - spec GetModuleSource; see
	// JSModuleDescriptor.getModuleSourceText()) and the usual "Cannot find
	// module" TypeError when the specifier doesn't resolve at all. One
	// object per resolved name (spec: one [[ModuleSource]] per Module
	// Record), cached by the implementing context - see
	// ModuleSource.resolve(). Default (for a JSGlobalContext that doesn't
	// support this) throws.
	public default org.monflabs.galtajs.rt.builtins.standard.module.ModuleSource importModuleSource(JSUnitContext unitContext, String name) {
		throw RuntimeUtil.typeError("Source phase imports are not supported by this module runtime");
	}

	// `import defer * as ns from '...'` (import-defer proposal) - loads
	// (parses) the target module WITHOUT evaluating its body, returning
	// its Module Namespace Exotic Object immediately; the module's own
	// body only runs on the FIRST genuine access (property get/has/etc)
	// on that returned namespace object. See
	// InterpretedGlobalRuntimeContext.importDeferredNamespace()'s own doc
	// comment for the implementation. Default (for a JSGlobalContext that
	// doesn't support this - e.g. the transpiled backend) throws.
	public default JSObject importDeferredNamespace(JSUnitContext unitContext, String name, java.util.Map<String,String> attributes) {
		throw RuntimeUtil.typeError("import defer is not supported by this module runtime");
	}

	// GatherAsynchronousTransitiveDependencies (import-defer proposal) -
	// see InterpretedGlobalRuntimeContext's own doc comment on its
	// implementation, and RuntimeUtil.dynamicImportDefer()'s own doc for
	// why `import.defer()` needs this at all. Default (for a
	// JSGlobalContext with no such concept, e.g. the transpiled backend)
	// returns an empty list - never any dependency to eagerly evaluate.
	public default java.util.List<JSInterpretedUnit> gatherAsynchronousTransitiveDependencies(JSUnitContext unitContext, String name) {
		return java.util.Collections.emptyList();
	}

	// Spec EnsureDeferredNamespaceEvaluation (10.4.6.14) - see
	// InterpretedGlobalRuntimeContext's and TranspiledGlobalRuntimeContext's
	// own doc comments on their implementations (JSModule, not
	// JSInterpretedUnit specifically, since TranspiledGlobalRuntimeContext's
	// own override needs to handle a JSTranspiledUnit target too - see
	// JSTranspiledUnit.readyForSyncExecution()'s own doc comment). Default
	// (for a JSGlobalContext with no such concept at all) is a no-op -
	// matches gatherAsynchronousTransitiveDependencies's own default of
	// "nothing to evaluate" above.
	public default void ensureModuleEvaluated(JSModule unit, JSGlobalContext globalContext) {
	}

	// See InterpretedGlobalRuntimeContext's own doc comment - starts a
	// gathered-but-not-yet-started async dependency running, without the
	// synchronous-readiness gate ensureModuleEvaluated() above has.
	// Default (for a JSGlobalContext with no such concept) is a no-op.
	public default void startAsyncDependencyEvaluation(JSModule unit, JSGlobalContext globalContext) {
	}

	// Pure cache lookup for JSInterpretedUnit.readyForSyncExecution()'s
	// dependency walk on a module that hasn't started executing yet - see
	// InterpretedGlobalRuntimeContext.peekModule()'s own doc comment for why
	// this must NEVER load/evaluate anything, unlike importModule() above.
	// Default (for a JSGlobalContext with no module cache to peek, e.g. one
	// that never tracks dependency modules this way) returns null, which
	// JSInterpretedUnit's caller already treats as "not cached yet, assume
	// ready" - matching this method's own contract of never itself causing
	// a load.
	public default JSModule peekModule(JSModule self, String name) {
		return null;
	}

	// A module executed as the ROOT of a run (never itself reached via
	// importModule()) is otherwise never entered into a global context's own
	// module cache - see InterpretedGlobalRuntimeContext.registerRootModule()'s
	// own doc comment for the full rationale (a root file's self-/circular-
	// import must find THIS SAME instance, not recurse into loading a
	// duplicate). Called unconditionally by JSInterpretedUnit.
	// executeWithContext() whenever program.isModule() is true - a module's
	// body always executes this way regardless of whether the OVERALL
	// program/environment is running in transpiled mode (modules aren't
	// ahead-of-time transpiled per file), so this must be implemented by
	// every JSGlobalContext that tracks a module cache at all, not just the
	// interpreted one. Default (for a JSGlobalContext with no such cache) is
	// a no-op.
	public default void registerRootModule(JSModule module) {
	}


	/////////////////////////////////////////////////////////////////////////
	// Helpers
	/////////////////////////////////////////////////////////////////////////

    public default JSValue global(String member) {
    	return with( () -> {
    		VarAccessor a = getVariableEntry(member);
    		if(a!=null) {
    			return JSValue.of(this,a.getValue());
    		}
        	return JSValue.of(this,getGlobalThis().getOwnProperty(member,RuntimeUtil.UNDEFINED));
    	});
    }
    public default JSValue value(Object value) {
    	return JSValue.of(this,value);
    }
    public default JSValue createObject() {
    	return JSValue.of(this,JSObject.create(getEnvironment()));
    }
    public default JSValue createArray() {
    	return JSValue.of(this,JSArray.create(getEnvironment()));
    }
    
    
}