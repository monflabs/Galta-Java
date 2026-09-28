package org.monflabs.galtajs.test.rhino;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.library.rhino.RhinoLibrary;
import org.monflabs.galtajs.rt.JSRuntimeContext;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.galtajs.rt.builtins.BaseMethod;
import org.monflabs.galtajs.rt.builtins.Callable;
import org.monflabs.galtajs.rt.builtins.standard.global.StandardObjects;
import org.monflabs.galtajs.rt.executors.JSExecutor;
import org.monflabs.galtajs.rt.executors.MicroTask;

/**
 * Rhino Test objects.
 * 
 * https://github.com/mozilla/rhino/blob/master/examples/Shell.java
 * https://p-bakker.github.io/rhino/tools/shell/
 * https://stackoverflow.com/questions/12399462/rhino-print-function
 * 
 */
public class RhinoTestLibrary extends RhinoLibrary {
	
	public RhinoTestLibrary() {
	}
	
	@Override
	public void configureStandardObjects(JSEnvironment env, StandardObjects standardObjects) {
		// Global functions
		standardObjects.setOwnMethod(new Method(env,MethodId.AbortJS,1));
		standardObjects.setOwnMethod(new Method(env,MethodId.EnqueueMicrotask,0));
		standardObjects.setOwnMethod(new Method(env,MethodId.PerformMicrotaskCheckpoint,0));
	}

	private static enum MethodId {
		AbortJS,
		EnqueueMicrotask,
		PerformMicrotaskCheckpoint,
	}
	
	private final static class Method extends BaseMethod {
		private MethodId methodId;
		
		private Method(JSEnvironment env, MethodId methodId, int length) {
			super(env,methodId.name(),length);
			this.methodId = methodId;
		}
		
	    @Override
		public Object call(final Object obj, final Object[] args) {
	        switch(methodId){
	        	case AbortJS:{
					throw RuntimeUtil.uncatchable("Rhino AbortJS");
	        	}
            	case EnqueueMicrotask:{
            		JSRuntimeContext ctx = JSRuntimeContext.get();
            		Callable cb = paramCallable(args, 0);
    				ctx.getGlobalContext().getExecutor().queueMicrotask(new MicroTask("Rhino - EnqueueMicrotask",ctx,null) {
    					@Override
    					public void run() { 
    						cb.call(null);
    					}
    				});
	        		return RuntimeUtil.UNDEFINED;
            	}
            	case PerformMicrotaskCheckpoint:{
            		JSRuntimeContext ctx = JSRuntimeContext.get();
            		JSExecutor ex = ctx.getGlobalContext().getExecutor();
            		ex.performMicrotaskCheckpoint();
	        		return RuntimeUtil.UNDEFINED;
            	}
	            
	            default: {
	    		    throw new IllegalStateException(); // Should never be here 
	            }
	        }
	    }
	}
}