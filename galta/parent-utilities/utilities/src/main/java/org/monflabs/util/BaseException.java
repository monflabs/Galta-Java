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
package org.monflabs.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public abstract class BaseException extends RuntimeException {
	
	public BaseException(Throwable t) {
		super("Forwarded exception", t);
	}

	public BaseException(Throwable t, String message) {
		super(message!=null ? message : "Forwarded exception", t);
	}

	public BaseException(Throwable t, String message, Object...parameters) {
		super(message!=null ? StringFormat.format(message, parameters) : "Forwarded exception", t);
	}

	
	//
	// Utility methods
	//
	
    public static Throwable getCause(Throwable t) {
    	Throwable cause = null;
    	if(t.getClass().getName().equals("javax.servlet.ServletException")) {
    		try {
	    		Method m = t.getClass().getMethod("getRootCause");
	    		if(m!=null) {
	    			cause = (Throwable)m.invoke(t);
	    		}
    		} catch(Throwable t2) {}
    	}
        if(cause==null) {
	        if(t instanceof SQLException) {
	            cause = ((SQLException)t).getNextException();
	        }
	        if(cause==null) {
	            cause = t.getCause();
	        }
        }
        if(cause==t) {
        	cause=null;
        }
        return cause;
    }
    
    public static String getStackAsString(Throwable t) {
	    StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw);
	    t.printStackTrace(pw);
	    String s = sw.toString();
	    return s;
    }
    
    public static String getMessages(Throwable t) {
    	List<String> messages = new ArrayList<>();
    	java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    	for(Throwable e=t; e!=null && seen.add(e); e=getCause(e)) {
    		String m = e.getLocalizedMessage();
    		if(m!=null && !messages.contains(m)) {
    			messages.add(m);
    		}
    	}
    	if(messages.size()==0) {
    		return "";
    	}
    	if(messages.size()==1) {
    		return messages.get(0);
    	}
    	StringBuilder b = new StringBuilder();
    	for(int i=0; i<messages.size(); i++) {
    		if(i>0 ) {
    			b.append('\n');
    		}
    		b.append(messages.get(i));
    	}
    	return b.toString();
    }
}
