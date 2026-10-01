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
package org.monflabs.galtajs.library.rhino;

import java.io.File;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.modules.JSInterpretedUnit;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.JSRuntimeInterruptException;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.interpreter.JSInterpretedRuntimeContext;
import org.monflabs.util.FileUtil;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * RhinoShell objects.
 * 
 * https://github.com/mozilla/rhino/blob/master/examples/Shell.java
 * https://p-bakker.github.io/rhino/tools/shell/
 * https://stackoverflow.com/questions/12399462/rhino-print-function
 * 
 */
public class RhinoShellLibrary extends RhinoLibrary {
	
	public static final String PROPERTY_VERSION = "rhino.shell.version";
	public static final String PROPERTY_BASEDIR = "rhino.shell.basedir";

	public static final String RS_VERSION = "version";
	public static final String RS_PRINT   = "print";
	
	private String version = "200";
	
	public RhinoShellLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		// Global functions
		standardObjects.setOwnMethod(new Method(env,MethodId.version,1));
		standardObjects.setOwnMethod(new Method(env,MethodId.print,0));
		standardObjects.setOwnMethod(new Method(env,MethodId.options,0));
		standardObjects.setOwnMethod(new Method(env,MethodId.quit,0));
		standardObjects.setOwnMethod(new Method(env,MethodId.gc,0));
		standardObjects.setOwnMethod(new Method(env,MethodId.load,1));
		// escape()/unescape() come from the standard library (Annex B) when
		// deprecated APIs are on - no shell-specific copies
	}

	private static enum MethodId {
		version,
		print,
		options,
		quit,
		gc,
		load,
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
	        	case version:{
	        		if(args.length==1) {
		        		String version = paramString(args, 0);
		        		if(StringUtil.isNotEmpty(version)) {
		        			RhinoShellLibrary.this.version = version;
		        		}
	        		}
	        		String version = RhinoShellLibrary.this.version;
        			return StringUtil.isNotEmpty(version) ? version : "200"; // VERSION_ES6
	        	}
            	case print:{
            		for(int i=0; i<args.length; i++) {
            			String s = RuntimeUtil.toString(getEnvironment(),args[i]);
            			JSRuntimeContext.get().getGlobalContext().getOutStream().print(s);
            		}
            		JSRuntimeContext.get().getGlobalContext().getOutStream().println();
	        		return RuntimeUtil.UNDEFINED;
            	}
            	case options:{
            		if(args.length>=1) {
            			// Don't set anything...
            			return RuntimeUtil.UNDEFINED;
            		}
            		StringBuilder b = new StringBuilder();
                    b.append("opt level: " + 0);
                    b.append(", strict mode: " + RuntimeUtil.isStrictMode());
                    b.append(", language version: " + 1);
            		return b.toString();
            	}
	        	case quit:{
	        		throw new JSRuntimeInterruptException();
	        	}
	        	case gc:{
	        		System.gc();
	        		return RuntimeUtil.UNDEFINED;
	        	}
	        	case load:{
	        		JSEnvironment env = getEnvironment();
	        		File baseDir = (File)env.getProperty(PROPERTY_BASEDIR);
	        		if(baseDir==null) {
	        			throw new IllegalStateException(StringFormat.format("Missing Rhino Shell base directory. {0}",PROPERTY_BASEDIR));
	        		}
	        		for(int i=0; i<args.length; i++) {
	        			String fn = paramString(args, i);
	        			File f = resolveFile(baseDir, fn);
		        		String code = FileUtil.readContent(f);
		        		// Like Rhino, the file runs in the global scope
		        		JSRuntimeContext global = JSRuntimeContext.get().getGlobalContext();
		        		if(global instanceof JSInterpretedRuntimeContext) {
			        		JSInterpretedUnit sc = env.createScript(code, fn);
			        		sc.executeWithContext(global);
		        		} else {
		        			// A transpiled caller: an indirect eval runs the code in
		        			// that same global scope
		        			Object eval = env.getStandardObjects().getOwnProperty("eval");
		        			((Callable)eval).call(RuntimeUtil.UNDEFINED, new Object[] {code});
		        		}
	        		}
	        		
	        		return RuntimeUtil.UNDEFINED;
	        	}
	        	
	            default: {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	
	    // Only a file inside the base directory, symbolic links resolved
	    private File resolveFile(File baseDir, String fn) {
	    	try {
	    		File base = baseDir.getCanonicalFile();
	    		File f = new File(base,fn).getCanonicalFile();
	    		if(!f.toPath().startsWith(base.toPath()) || !f.isFile()) {
	    			throw RuntimeUtil.error("File '{0}' is not a valid file", fn);
	    		}
	    		return f;
	    	} catch(java.io.IOException ex) {
	    		throw RuntimeUtil.error(ex, "File '{0}' is not a valid file", fn);
	    	}
	    }
}
}