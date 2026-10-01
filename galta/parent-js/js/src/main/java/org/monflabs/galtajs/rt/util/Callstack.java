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
package org.monflabs.galtajs.rt.util;

import java.util.ArrayList;
import java.util.List;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSBoundaryContext;
import org.monflabs.galtajs.rt.JSFunctionContext;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedFunctionRuntimeContext;
import org.monflabs.galtajs.rt.interpreter.InterpretedRuntimeContext;
import org.monflabs.galtajs.rt.transpiler.TranspiledFunctionRuntimeContext;
import org.monflabs.util.StringUtil;

/**
 * Call stack for debugging purpose
 */
public class Callstack {

	public static final class Entry {
		private JSRuntimeContext context;
		private ASTNode callerNode;
		private String label;
		private Entry(JSRuntimeContext context, ASTNode callerNode) {
			this.context = context;
			this.callerNode = callerNode;
		}
		public JSRuntimeContext getContext() {
			return context;
		}
		public ASTNode getCallerNode() {
			return callerNode;
		}
		@Override
		public String toString() {
			if(label==null) {
				label = composeLabel();
			}
			return label;
		}
		private String composeLabel() {
			String callerReference=null;
			if(context instanceof InterpretedRuntimeContext ic) {
				// The location this frame was called from - a local: the
				// entry's own callerNode (getCallerNode()) is kept as given
				ASTNode caller = ic.getCallerNode();
				if(caller!=null) {
					String fileName = ic.getMainContext().getScriptUnit().getDescriptor().getName();
					int line = caller.getBeginLine();
					if(line>=0) {
						callerReference = " (" + fileName +  " :" + line + ")";
					} else {
						callerReference =  " (" + fileName +  ")";
					}
				}
			}
			
			// Resolve via getFunctionContext() rather than an instanceof check
			// directly on `context`: when the throwing/calling context is a
			// hasParameterExpressions function's split BODY frame (see
			// InterpretedFunctionBodyRuntimeContext), `context` itself is a
			// plain block-like frame, not the InterpretedFunctionRuntimeContext -
			// walking to the nearest JSFunctionContext ancestor finds it in
			// exactly one hop. For every other context (including the common,
			// unsplit case, where `context` already IS the function's own
			// frame) getFunctionContext() returns `context` itself on its very
			// first check, so this is a no-op there.
			JSFunctionContext funcCtx = context.getFunctionContext();
			if(funcCtx instanceof InterpretedFunctionRuntimeContext fc) {
				String entryName = fc.getFunctionNode().getFunctionName();
				if (StringUtil.isEmpty(entryName)) {
					entryName = "Lambda () => {}";
				}
				if(callerReference!=null) {
					entryName += callerReference;
				}
				return entryName;
			} else if(funcCtx instanceof TranspiledFunctionRuntimeContext tfc) {
				// No AST node available at runtime for a transpiled function
				// (see TranspiledRuntimeContext.getDebugCallParent()'s own
				// doc) - the name is read back from the already-compiled-in
				// "name" own property instead, via the existing
				// BuiltinFunction/BaseCallableObject accessor - no new
				// compile-time constant needed.
				String entryName = tfc.getFunction().getFunctionName();
				if (StringUtil.isEmpty(entryName)) {
					entryName = "Lambda () => {}";
				}
				return entryName;
			} else {
				String entryName = "<main>";
				if(callerReference!=null) {
					entryName += callerReference;
				}
				return entryName;
			}
		}
	}	
	
	private List<Entry> entries;
	
	public Callstack(JSRuntimeContext context, ASTNode node) {
		this.entries = gatherCallstack(context, node);
	}
	
	public List<Entry> getEntries() {
		return entries;
	}
	
	
	private static List<Entry> gatherCallstack(JSRuntimeContext context, ASTNode node) {
		List<Entry> entries = new ArrayList<Callstack.Entry>();
		if(node!=null) {
			entries.add(new Entry(context,node));
		}
		for (JSRuntimeContext c = context; c!=null ; ) {
			JSRuntimeContext bc = getBoundaryContext(c);
			
			ASTNode callerNode = null;
			if(c instanceof InterpretedRuntimeContext ic) {
				callerNode = ic.getCallerNode();
			}
			
			if(bc instanceof InterpretedFunctionRuntimeContext || bc instanceof TranspiledFunctionRuntimeContext) {
				if(callerNode!=null) {
					entries.add(new Entry(c,callerNode));
				}
				c = bc.getDebugCallParent();
			} else {
				if(callerNode!=null) {
					entries.add(new Entry(c,callerNode));
				}
				c = null;
			}
		}
		return entries;
	}
	private static JSBoundaryContext getBoundaryContext(JSRuntimeContext from) {
		for( JSRuntimeContext c=from; c!=null; c=c.getParent()) {
			if(c instanceof JSBoundaryContext bc) {
				return bc;
			}
		}
		return null;
	}
}