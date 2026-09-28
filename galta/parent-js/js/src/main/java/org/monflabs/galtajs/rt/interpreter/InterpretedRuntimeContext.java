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

import java.util.function.Supplier;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSBoundaryContext;
import org.monflabs.galtajs.rt.JSGlobalContext;
import org.monflabs.galtajs.rt.JSResult;
import org.monflabs.galtajs.rt.JSRuntimeContext;

/**
 * Runtime context used by the interpreter.
 */
public abstract class InterpretedRuntimeContext extends AbstractRuntimeContext implements JSInterpretedRuntimeContext {
	
	protected JSBoundaryContext boundaryContext;
	protected Object filterContext;
	protected ASTNode callerNode;


	// All ctors
	protected InterpretedRuntimeContext() {
	}
	protected InterpretedRuntimeContext(JSRuntimeContext parent) {
		super(parent);
	}
	protected InterpretedRuntimeContext(JSRuntimeContext parent, JSGlobalContext globalContext) {
		super(parent,globalContext);
	}

	@Override
	public ASTNode getCallerNode() {
		return callerNode;
		
	}
	@Override
	public ASTNode setCallerNode(ASTNode node) {
		ASTNode oldNode = this.callerNode;
		this.callerNode = node;
		return oldNode;
	}
	
	@Override
	public JSRuntimeContext getDebugCallParent() {
		return getParent();
	}

	@Override
	public final JSBoundaryContext getDebugCallBoundaryContext() {
		if(boundaryContext==null) {
			for(JSRuntimeContext c=this; c!=null; c=c.getDebugCallParent()) {
				if(c instanceof JSBoundaryContext cc) {
					boundaryContext = cc;
					break;
				}
			}
		}
		return boundaryContext;
	}

	@Override
	public JSRuntimeContext getVarDeclContext() {
		return this;
	}

	@Override
	public final Object getFilterContext() {
		return filterContext;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> T executeWithFilterContext(Object filterContext, ASTNode node, JSResult result) {
		Object oldFilterContext = this.filterContext;
		this.filterContext = filterContext;
		Object res = node.evaluateValue(this, result);
		this.filterContext = oldFilterContext;
		return (T)res;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> T executeWithFilterContext(Object filterContext, Supplier<Object> callback) {
		// No need for try/finally as an exception will anyway stop the evaluation
		Object oldFilterContext = this.filterContext;
		//try {
			this.filterContext = filterContext;
			Object res = callback.get();
		//} finally {
		this.filterContext = oldFilterContext;
		// }
		return (T)res;
	}
	
	@Override
	public boolean isCompiled() {
		return false;
	}
}
