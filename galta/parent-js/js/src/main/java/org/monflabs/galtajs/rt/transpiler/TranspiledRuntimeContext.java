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

import org.monflabs.galtajs.rt.JSBoundaryContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.interpreter.AbstractRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext.VAR_TYPE;
import org.monflabs.galtajs.rt.interpreter.VariableMap;

/**
 * Runtime context used by the interpreter.
 */
public abstract class TranspiledRuntimeContext extends AbstractRuntimeContext implements JSTranspiledRuntimeContext {

	private Object returnValue = RuntimeUtil.UNDEFINED;

	// All ctors
	protected TranspiledRuntimeContext() {
	}
	protected TranspiledRuntimeContext(JSRuntimeContext parent) {
		super(parent);
	}
	protected TranspiledRuntimeContext(JSRuntimeContext parent, JSGlobalContext globalContext) {
		super(parent,globalContext);
	}
	

	@Override
	public void initGlobalVariables(Object[] variables, String[] varNames, String[] varTypes) {
		if(varNames!=null) {
			VariableMap globalVars = getVariableMap(true);
			int l = varNames.length;
			for(int i=0; i<l; i++) {
				VAR_TYPE t = (varTypes!=null && varTypes[i]!=null) ? VAR_TYPE.valueOf(varTypes[i]) : VAR_TYPE.AUTO;
				globalVars.initVariable(varNames[i],variables,i,t);
			}
		}
	}


	@Override
	public Object getReturnValue() {
		return returnValue;
	}
	@Override
	public void setReturnValue(Object returnValue) {
		this.returnValue = returnValue;
	}
	
	@Override
	public JSRuntimeContext getVarDeclContext() {
		return this;
	}

	// v1 approximation (see the plan's own documented transpiled-mode call-
	// stack scope): getParent() is the LEXICAL parent (the closure scope a
	// function was DEFINED in), not the dynamic caller - a real call stack
	// would need a separate, newly-threaded "who called this" field at
	// every generated call site, which is out of scope for now. This is
	// still strictly better than throwing: it at least lets Callstack-style
	// walking terminate cleanly (finite, acyclic lexical nesting) instead of
	// crashing the moment anything asks for a transpiled frame's parent -
	// DebuggerImpl's own pause/scope-inspection path only ever needs the
	// CURRENT frame, so this is sufficient for that.
	@Override
	public JSRuntimeContext getDebugCallParent() {
		return getParent();
	}

	@Override
	public JSBoundaryContext getDebugCallBoundaryContext() {
		for(JSRuntimeContext c=this; c!=null; c=c.getDebugCallParent()) {
			if(c instanceof JSBoundaryContext bc) {
				return bc;
			}
		}
		return null;
	}
	
	@Override
	public Object getFilterContext() {
		return null;
	}

	@Override
	public boolean isCompiled() {
		return true;
	}
}
