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
package org.monflabs.galtajs.rt.executors;

import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.util.Callstack;

/**
 * Base class for async tasks.
 */
public abstract class AsyncTask implements Runnable {

	// Async stack traces keep at most this many scheduling tasks: every task
	// referencing the one that scheduled it would otherwise build an unbounded
	// chain (a `for(;;) await x` loop), kept alive until the last task is done
	private static final int MAX_ASYNC_DEPTH = 16;

	private String name;
	private AsyncTask parent;
	private int depth;
	private JSRuntimeContext context;
	private ASTNode node;
	private Callstack callStack;

	public AsyncTask(String name, JSRuntimeContext context, ASTNode node) {
		this.name = name;
		if(context!=null) {
			AsyncTask p = context.getGlobalContext().getExecutor().getCurrentAsyncTask();
			if(p!=null && p.depth<MAX_ASYNC_DEPTH) {
				this.parent = p;
				this.depth = p.depth+1;
			}
			// Capturing `context`/`node` here instead of eagerly building the
			// Callstack is safe because InterpretedRuntimeContext's own
			// callerNode/parent chain is set once at construction and never
			// mutated afterward (confirmed: `callerNode` is assigned exactly
			// once, in InterpretedRuntimeContext's constructor) - so walking
			// it now or later produces an identical result. This constructor
			// runs for EVERY promise reaction (every .then()/await
			// resumption in the engine - see BuiltinPromise's MicroTask
			// construction), but getCallstack() below is only ever consulted
			// from JSRuntimeException's stack-trace formatting - i.e. only
			// once something has actually gone wrong. Deferring the walk
			// (context-chain traversal + one Entry allocation per frame)
			// out of the common, no-error case is the whole point.
			this.context = context;
			this.node = node;
		}
	}

	public String getName() {
		return name;
	}

	public AsyncTask getParent() {
		return parent;
	}

	public Callstack getCallstack() {
		if(callStack==null && context!=null) {
			callStack = new Callstack(context, node);
		}
		return callStack;
	}
	
	public void setCallstack(Callstack callStack) {
		this.callStack = callStack;
	}
}