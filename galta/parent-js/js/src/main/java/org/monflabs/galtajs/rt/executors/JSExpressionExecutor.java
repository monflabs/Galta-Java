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

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.util.generators.Generator;
import org.monflabs.util.generators.Yielder;

public class JSExpressionExecutor implements JSExecutor {
	
	// Can be shared across environments
	private static JSExpressionExecutor instance = new JSExpressionExecutor();
	public static JSExpressionExecutor get() {
		return instance;
	}
	
	protected JSExpressionExecutor() {
	}
	
    @Override
	public void queueMicrotask(MicroTask task) {
    	throw RuntimeUtil.error("This executor does not support micro-tasks");
    }

    @Override
	public void queueMicrotask(MicroTask task, long atTimeMs) {
    	throw RuntimeUtil.error("This executor does not support micro-tasks");
    }

    @Override
	public void queueMacrotask(MacroTask task) {
    	throw RuntimeUtil.error("This executor does not support macro-tasks");
    }

    @Override
    public void performMicrotaskCheckpoint() {
    	// No microtasks...
    }

    @Override
    public Object execute(@NonNull Supplier<Object> code, boolean async) {
    	if(async) {
        	throw RuntimeUtil.error("This executor does not support asynchronous execution");
    	}
    	return code.get();
    }

    @Override
	public AsyncTask getCurrentAsyncTask() {
    	return null;
    }

	@Override
	public Generator<Object,Object> generator(Function<Yielder<Object>,Object> body) {
    	throw RuntimeUtil.error("This executor does not support generators");
	}

	@Override
	public BuiltinPromise asyncFunction(Callable<Object> body) {
    	throw RuntimeUtil.error("This executor does not support async functions");
	}

	@Override
	public BuiltinPromise runAsyncBody(Callable<Object> body) {
    	throw RuntimeUtil.error("This executor does not support async functions");
	}

	@Override
	public Object await(Object value) {
    	throw RuntimeUtil.error("This executor does not support await");
	}
}