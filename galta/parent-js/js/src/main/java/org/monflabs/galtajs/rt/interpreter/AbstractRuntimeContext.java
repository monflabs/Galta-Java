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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.DisposableResource;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSUnitContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.Constructor;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap.VariableEntry;
import org.monflabs.galtajs.rt.transpiler.VarAccessor;

/**
 * Base abstract Runtime context used by the interpreter and the transpiler.
 */
public abstract class AbstractRuntimeContext implements JSRuntimeContext {

	protected JSEnvironment env;
	protected AbstractRuntimeContext parent;
	protected JSUnitContext mainContext;
	protected JSGlobalContext globalContext;
	protected JSFunctionContext functionContext;

	protected VariableMap variables;
	protected List<DisposableResource> disposableResources;

	protected Constructor newTarget;

	// For program context
	protected AbstractRuntimeContext() {
	}

	protected AbstractRuntimeContext(JSRuntimeContext parent) {
		this.parent = (AbstractRuntimeContext)parent;
		this.env = parent.getEnvironment();
		this.mainContext = parent.getMainContext();
		this.globalContext = parent.getGlobalContext();
	}
	
	// For functions
	// The global context, in case of a module, comes from the caller module and not the module itself 
	protected AbstractRuntimeContext(JSRuntimeContext parent, JSGlobalContext globalContext) {
		this.parent = (AbstractRuntimeContext)parent;
		this.mainContext = parent.getMainContext();
		this.globalContext = globalContext;
		this.env = parent.getEnvironment();
	}
	
	@Override
	public final JSEnvironment getEnvironment() {
		return env;
	}

	@Override
	public final AbstractRuntimeContext getParent() {
		return parent;
	}
	
	@Override
	public final JSUnitContext getMainContext() {
		return mainContext;
	}

	@Override
	public final JSGlobalContext getGlobalContext() {
		return globalContext;
	}

	@Override
	public  JSFunctionContext getFunctionContext() {
		if(functionContext==null) {
			for(JSRuntimeContext c=this; c!=null; c=c.getParent()) {
				if(c instanceof JSFunctionContext fc) {
					return functionContext = fc;
				}
			}
		}
		return functionContext;
	}

	@Override
	public Object getThis() {
		return parent!=null ? parent.getThis() : null;
	}

	public void reset() {
		variables.clear();
	}
	
	
	@Override
	public Object getVariableValue(String varName, Object defaultValue) {
		for( AbstractRuntimeContext ctx=this; ctx!=null; ctx=ctx.getParent()) {
			if(ctx.variables!=null) {
				VariableEntry e = ctx.variables.getEntry(varName);
				if(e!=null) {
					// A genuinely-declared binding's raw stored value can itself be the
					// internal RuntimeUtil.NOT_AVAILABLE sentinel (e.g. a destructuring
					// rest-into-object-pattern binding for a property that doesn't exist
					// in the source - see BuiltinFunctionInterpreter's declare() callback,
					// which stores exactly that into the slot). Must convert to the real
					// JS `undefined` here, same as every other read path - otherwise a
					// caller using NOT_AVAILABLE as ITS OWN "not found" sentinel (like
					// RuntimeUtil.getIdentifierValue) would wrongly treat this found-but-
					// unavailable-valued binding as if it didn't exist at all.
					Object v = RuntimeUtil.checkTDZ(e.getValue(), varName);
					return v!=RuntimeUtil.NOT_AVAILABLE ? v : RuntimeUtil.UNDEFINED;
				}
			}
			Object v = ctx.resolveOwnIdentifierValue(varName,RuntimeUtil.NOT_AVAILABLE);
			if(v!=RuntimeUtil.NOT_AVAILABLE) {
				return v;
			}
		}
		return defaultValue;
	}
	
	@Override
	public VarAccessor getVariableEntry(String varName) {
		for(AbstractRuntimeContext ctx=this; ctx!=null; ctx=ctx.getParent()) {
			VarAccessor a = ctx.getLocalVariableEntry(varName);
			if(a!=null) {
				return a;
			}
		}
		return null;
	}

	// Checks ONLY this context's own bindings (its own VariableMap, then the
	// resolveOwnIdentifierEntry() hook `with`/globalThis-backed contexts use)
	// - unlike getVariableEntry(), never walks to the parent. Needed anywhere
	// "does THIS exact scope already declare this name" must be answered
	// without risking a false-positive match on an unrelated ancestor
	// binding of the same name - e.g. BaseEvalContext.createVariable()'s own
	// "is this already declared in this scope" check (a direct eval's own
	// var declarations must never be conflated with a same-named outer
	// binding just because a full walk happens to reach one).
	@Override
	public VarAccessor getLocalVariableEntry(String varName) {
		if(variables!=null) {
			VariableEntry e = variables.getEntry(varName);
			if(e!=null) {
				return e;
			}
		}
		return resolveOwnIdentifierEntry(varName);
	}

	@Override
	public void registerDisposableResource(DisposableResource resource) {
		if(disposableResources==null) {
			disposableResources = new ArrayList<>();
		}
		disposableResources.add(resource);
	}
	@Override
	public List<DisposableResource> getOwnDisposableResources() {
		return disposableResources!=null ? disposableResources : Collections.emptyList();
	}

	//
	// Global context have the ability to add dynamic values.
	// These are not available in GlobalThis
	//
	@Override
	public Object resolveOwnIdentifierValue(String varName, Object defaultValue) {
		VarAccessor acc = resolveOwnIdentifierEntry(varName);
		return acc!=null ? acc.getValue() : defaultValue;
	}
	public VarAccessor resolveOwnIdentifierEntry(String varName) {
		return null;
	}


 	
	//
	// Handling local variables
	//


	@Override
	public final VariableMap getVariableMap() {
		return variables;
	}
	@Override
	public final VariableMap getVariableMap(boolean autoCreate) {
		if(variables==null && autoCreate) {
			variables = new VariableMap();
		}
		return variables;
	}

	@Override
	public final boolean hasVariable(String varName) {
		return getVariableEntry(varName)!=null;
	}
	
	@Override
	public final void setVariable(String varName, Object value) {
		VarAccessor a = getVariableEntry(varName);
		if(a!=null) {
			a.setValue(value);
			return;
		}
		throw RuntimeUtil.referenceError("{0} is not defined", varName);
	}

	@Override
	public boolean deleteVariable(String varName) {
		if(variables!=null) {
			VariableEntry e = variables.getEntry(varName);
			if(e!=null) {
				if(!isStrictMode()) {
					// AUTO bindings are always deletable; a VAR/FUNCTION binding is deletable
					// only when it was itself hoisted into this scope by a direct eval (flagged
					// at creation time - see BaseEvalContext.createVariable). Every other binding
					// (ordinary local var/function/let/const/etc.) is non-configurable per spec.
					if(e.type==VAR_TYPE.AUTO || ((e.type==VAR_TYPE.VAR || e.type==VAR_TYPE.FUNCTION) && e.isConfigurable())) {
						if(variables.delete(varName)) {
							return true;
						}
					} else {
						return false;
					}
				} else {
					throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
				}
			}
		}
		if(parent==null) {
			if(isStrictMode()) {
				throw RuntimeUtil.syntaxError("Cannot delete a local variable in strict mode");
			}
			return true;
		}
		return parent.deleteVariable(varName);
	}

	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type) {
		return getVariableMap(true).createVariable(varName, value, type);
	}

	@Override
	public VarAccessor createVariable(String varName, Object value, VAR_TYPE type, boolean configurable) {
		return getVariableMap(true).createVariable(varName, value, type, configurable);
	}
	
	@Override
	public final Constructor getNewTarget() {
		return newTarget;
	}
	@Override
	public final void setNewTarget(Constructor newTarget) {
		this.newTarget = newTarget;
	}
}
