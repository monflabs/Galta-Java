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
package org.monflabs.galtajs.library.java;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.NativeObject;

/**
 * Java bridge.
 */
public class Java extends NativeObject {

	public static final String OBJECTNAME = "Java";

	private JavaLibrary javaLibrary;
	
	public Java(JSEnvironment env, JavaLibrary javaLibrary) {
		super(env);
		this.javaLibrary = javaLibrary;
		setOwnMethod(new Method(env,MethodId.type,1));
	}

	@Override
	public String getClassName() {
		return "Java";
	}	
	
	private static enum MethodId {
		type,
	}
	
	private final class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		protected Object invoke(final Object obj, final Object[] args) {
	        switch(methodId){
            	case type:{
        			String arg0 = paramString(args, 0);
        			Object r = javaLibrary.loadClass(getEnvironment(),arg0);
            		return r;
            	}
	            
	            default: {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}
}