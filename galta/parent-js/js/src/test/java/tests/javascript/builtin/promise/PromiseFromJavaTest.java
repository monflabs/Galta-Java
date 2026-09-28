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
package tests.javascript.builtin.promise;

import org.eclipse.jdt.annotation.NonNull;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromiseConstructor;
import org.monflabs.galtajs.rt.executors.MicroTask;
import org.monflabs.galtajs.rt.interpreter.InterpretedGlobalRuntimeContext;

import tests.javascript.JavaScriptStrictTestCase;

public class PromiseFromJavaTest extends JavaScriptStrictTestCase {
	
	public void testPromise() throws Exception {
		InterpretedGlobalRuntimeContext context = new InterpretedGlobalRuntimeContext(getEnvironment(),getEnvironment().createProgramExecutor());
    	context.with( () -> {
    		return context.getGlobalContext().getExecutor().execute(() -> {
    	        BuiltinPromiseConstructor ctor = (BuiltinPromiseConstructor)getEnvironment().getStandardObjects().getConstructor(BuiltinPromiseConstructor.CLASSNAME);

	            BuiltinPromise p = (BuiltinPromise)ctor.constructObject(new Object[] { new Callable() {
	            	@Override
	    			public Object call(Object _this, @NonNull Object[] a) {
	    	            Callable resolve = (Callable) a[0];
	    	            @SuppressWarnings("unused")
						Callable reject  = (Callable) a[1];
	    				context.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Test",null,null) {
	    					@Override
	    					public void run() { resolve.call(null, "hello"); }
	    				});
	    	            return null;
	            	}
	            }},null);

	            BuiltinPromise chained = (BuiltinPromise) p.then_((Callable) (t, a2) -> {
	                System.out.println("Fulfilled with: " + a2[0]);
	                return a2[0] + " world";
	            }, (Callable) (t, a2) -> {
	                System.err.println("Rejected: " + a2[0]);
	                return null;
	            });
	            chained.then_((Callable) (t, a3) -> {
	                System.out.println("Chained value: " + a3[0]);
	                return null;
	            }, null);
	            
	            return null;
    		});
         });    	
   }
}