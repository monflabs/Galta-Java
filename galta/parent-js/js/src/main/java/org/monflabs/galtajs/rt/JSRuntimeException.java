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
package org.monflabs.galtajs.rt;

import org.monflabs.galtajs.JSContext;
import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSParseException;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.node.ASTNode;
import org.monflabs.galtajs.rt.builtins.errors.BaseError;
import org.monflabs.galtajs.rt.builtins.errors.Error;
import org.monflabs.galtajs.rt.executors.AsyncTask;
import org.monflabs.galtajs.rt.util.Callstack;
import org.monflabs.util.StringFormat;
import org.monflabs.util.StringUtil;

/**
 * @author Philippe Riand
 */
@SuppressWarnings("serial")
public class JSRuntimeException extends JSException  {
	
	public static Object exceptionObject(Throwable ex) {
		if(ex instanceof JSRuntimeException) {
			return ((JSRuntimeException)ex).getJavascriptException();
		}
		// A parse-time failure (e.g. a module's own early-error static
		// semantics check, or - already handled the same way elsewhere -
		// `new Function("...")`'s body) is a raw Java exception, never a
		// JSRuntimeException - without this, it leaked through as-is into
		// JS-visible code (a promise rejection reason, etc.), which isn't a
		// recognized object type at all ("Unknown object type class
		// ...JSParseException"). Converts it into a genuine JS SyntaxError,
		// matching the same pattern BuiltinFunctionConstructor already uses
		// for its own parse errors.
		if(ex instanceof JSParseException) {
			return RuntimeUtil.syntaxError(ex.getLocalizedMessage()).getJavascriptException();
		}
		return ex;
	}
	// We do a throw here to lure the compiler and it accepts statements afterwards
	public static void throwJavaException(Object jsException) {
		throw asJavascriptException(null,jsException);
	}
	public static JSRuntimeException asJavascriptException(Throwable cause, Object jsException) {
		if(cause==null && jsException instanceof JSObject jo) {
			Object errorCause = jo.getProperty(Error.JAVA_EXCEPTION);
			if(errorCause instanceof Throwable t) {
				return new JSRuntimeException(t,jsException);
			}
		}
		return new JSRuntimeException(cause,jsException);
	}
	
	public static final class StackEntry {
		private StackEntry next;
		private ASTNode node;
		public StackEntry(StackEntry next, ASTNode node) {
			this.next = next;
			this.node = node;
		}
	}

	
	private StackEntry stackTrace;
	private String stackTraceMessage;
	private Object javascriptException;
	
    // The message is built lazily: reading "message" on a thrown object can run
    // user code (a getter, a Proxy trap), which must not happen at throw time
    private String message;
    private boolean messageComputed;

    protected JSRuntimeException(Throwable nextException, Object jsException) {
        super(nextException,null);
        this.javascriptException = jsException;
    }

    private String baseMessage() {
    	if(!messageComputed) {
    		try {
    			message = msg(javascriptException);
    		} catch(Throwable t) {
    			message = "Uncaught exception";
    		}
    		messageComputed = true;
    	}
    	return message;
    }

    private static final String msg(Object jsException) {
    	if(RuntimeUtil.isNullOrUndefined(jsException)) {
    		return null;
    	}
    	if(jsException instanceof BaseError) {
            return jsException.toString();
    	}
    	if(jsException instanceof JSObject jo) {
			Object n = RuntimeUtil.objectTypeName(jo.getEnvironment(), jo);
    		if(jo.hasProperty("message")) {
    			Object m = jo.getProperty("message");
    			return StringFormat.format("{0}: {1}",n,m);
    		}
			return StringFormat.format("{0}: {1}",n,jo.toString());
    	}
		return StringFormat.format("{0}: {1}",jsException.getClass().getSimpleName(),jsException.toString());
    }
    
    // TEMP 
    public Object getValue() {
		return getMessage();
	}

    @Override
	public String getMessage() {
    	// Not super.getMessage(): the message passed up is a placeholder, the
    	// real one is computed (lazily) from the JavaScript exception
    	String m = baseMessage();
    	if(m==null) {
    		m = super.getMessage();
    	}
    	if(StringUtil.isNotEmpty(stackTraceMessage)) {
    		return '\n' + m + '\n' + stackTraceMessage;
    	}
        return m;
    }

	public String getStackTraceMessage() {
        return this.stackTraceMessage;
    }

	public void setStackTraceMessage(String stackTraceMessage) {
        this.stackTraceMessage = stackTraceMessage;
    }
    
    public ASTNode errorNode() { 
    	for(StackEntry e=stackTrace; e!=null; e=e.next) {
    		if(e.next==null) {
    			return e.node;
    		}
    	}
    	return null;
    }
    
    public Object getJavascriptException() {
		return javascriptException;
	}

	public void setJavascriptException(Object javascriptException) {
		this.javascriptException = javascriptException;
	}

	public void fillStackTrace(ASTNode node) {
		if(getSourceNode()==null) {
			setSourceNode(node);
		}
    	if(stackTraceMessage==null) {
    		JSContext c = JSContext.getUnchecked();
    		if(c instanceof JSRuntimeContext ctx) {
	    		// Runtime execution
	    		Callstack cs = new Callstack(ctx, node);
				fillStackTrace(cs,"Stack Trace");

    			AsyncTask m = ctx.getGlobalContext().getExecutor().getCurrentAsyncTask();
    			if(m!=null) {
    				fillStackTrace(m);
    			}
    		} else {
    			// Source code (compiler, ...)
    			fillSourceCode(node);
    		}
    	}
    }
	
	// Runtime error
	private void fillStackTrace(AsyncTask asyncTask) {
		for(AsyncTask m=asyncTask; m!=null; m=m.getParent()) {
			Callstack cs = m.getCallstack();
			if(cs!=null && !cs.getEntries().isEmpty()) {
				fillStackTrace(cs, m.getName());
			}
		}
    }
	private void fillStackTrace(Callstack cs, String name) {
		StringBuilder builder = new StringBuilder();
		if(stackTraceMessage!=null) {
			builder.append(stackTraceMessage);
		}
		builder.append("\n----------------- ");
		builder.append(name);
		ASTNode previousNode = null;
		for(Callstack.Entry e: cs.getEntries()) {
			ASTNode n = e.getCallerNode();
    		if(n!=previousNode) {
        		builder.append(StringFormat.format("\nAt script line {0}, column {1}\n", n.getBeginLine(), n.getBeginCol()));
    			n.extractSourceCode(builder);
    		} else {
        		builder.append(StringFormat.format("\n    At script line {0}, column {1}... (recursive)", n.getBeginLine(), n.getBeginCol()));
    		}
    		previousNode = n;
		}
		builder.append("\n");
		this.stackTraceMessage = builder.toString();
    }
	
	// Source code error - compiler
	private void fillSourceCode(ASTNode n) {
		StringBuilder builder = new StringBuilder();
		if(stackTraceMessage!=null) {
			builder.append(stackTraceMessage);
		}
		builder.append(StringFormat.format("\nAt script line {0}, column {1}\n", n.getBeginLine(), n.getBeginCol()));
		n.extractSourceCode(builder);
		builder.append("\n");
		this.stackTraceMessage = builder.toString();
    }
}
