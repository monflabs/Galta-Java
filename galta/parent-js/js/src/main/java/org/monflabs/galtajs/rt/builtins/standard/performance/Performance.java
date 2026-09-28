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
package org.monflabs.galtajs.rt.builtins.standard.performance;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * performance object.
 */
public class Performance extends NativeObject {

	public static final String OBJECTNAME = "performance";

	public Performance(JSEnvironment env) {
		super(env);
		setOwnMethod(new Method(env,MethodId.clearMarks,0));
		setOwnMethod(new Method(env,MethodId.clearMeasures,0));
		setOwnMethod(new Method(env,MethodId.getEntries,0));
		setOwnMethod(new Method(env,MethodId.getEntriesByName,1));
		setOwnMethod(new Method(env,MethodId.getEntriesByType,1));
		setOwnMethod(new Method(env,MethodId.mark,1));
		setOwnMethod(new Method(env,MethodId.measure,1));
		setOwnMethod(new Method(env,MethodId.now,0));
		setOwnMethod(new Method(env,MethodId.toJSON,0));
	}
	
	@Override
	public String getClassName() {
		return OBJECTNAME;
	}
	
	private static enum MethodId {
		clearMarks,
		clearMeasures,
		getEntries,
		getEntriesByName,
		getEntriesByType,
		mark,
		measure,
		now,
		toJSON
	}

	private static final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
            	case clearMarks -> {
            		String arg0 = paramString(args, 0, null);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		data.clearMarks(arg0);
            		return null;
            	}
            	case clearMeasures -> {
            		String arg0 = paramString(args, 0, null);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		data.clearMeasures(arg0);
            		return null;
            	}
            	case getEntries -> {
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.getEntries();
            	}
            	case getEntriesByName -> {
            		String arg0 = paramString(args, 0);
            		String arg1 = paramString(args, 1, null);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.getEntriesByName(arg0,arg1);
            	}
            	case getEntriesByType -> {
            		String arg0 = paramString(args, 0, null);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.getEntriesByType(arg0);
            	}
            	case mark -> {
            		String arg0 = paramString(args, 0);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.mark(arg0);
            	}
            	case measure -> {
            		String arg0 = paramString(args, 0);
            		String arg1 = paramString(args, 1, null);
            		String arg2 = paramString(args, 2, null);
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.measure(arg0,arg1,arg2);
            	}
            	case now -> {
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.now();
            	}
            	case toJSON -> {
            		PerformanceData data = JSRuntimeContext.get().getGlobalContext().getPerformanceData();
            		return data.toJSON();
            	}
            	default -> {
        		    throw new IllegalStateException(); // Should never be here 
            	}
	        }
	    }
	}
}