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

import java.io.InputStream;
import java.io.PrintStream;

/**
 * Simple Console utilities
 */ 
public class Console {

	
	public static final void rawLog(String s) {
		System.out.println(s);
	}
	public static final void rawErr(String s) {
		System.err.println(s);
	}
	public static final void rawException(Throwable t, String s) {
		if(t==null) {
			return;
		}
		if(s!=null) {
			System.err.println(s);
		}
		t.printStackTrace(System.err);
	}
	
	public static InputStream inStream() {
		return System.in;
	}
	public static PrintStream outStream() {
		return System.out;
	}
	public static PrintStream errStream() {
		return System.err;
	}

	
	public static void log(String msg) {
		rawLog(msg);
	}
	public static void log(String msg, Object...params) {
		rawLog(StringFormat.format(msg, params));
	}
	public static void err(String msg, Object...params) {
		rawErr(StringFormat.format(msg, params));
	}

	public static void nolog(String msg) {
	}
	public static void nolog(String msg, Object...params) {
	}
	public static void noerr(String msg, Object...params) {
	}

	
	public static void log(Throwable t) {
		if(t!=null) {
			exception(t, null);
		}
	}
	public static void log(Throwable t, String msg, Object...params) {
		rawException(t, StringFormat.format(msg, params));
	}
	
	public static void exception(Throwable t) {
		if(t!=null) {
			exception(t, null);
		}
	}
	public static void exception(Throwable t, String msg) {
		if(t!=null) {
			rawException(t, msg);
		}
	}
	public static void exception(Throwable t, String msg, Object...params) {
		if(t!=null) {
			rawException(t, msg!=null ? StringFormat.format(msg, params) : "");
		}
	}
	
	public static String getMessage(Throwable t) {
		if(t.getCause()==null || t.getCause()==t) {
			return t.getLocalizedMessage();
		}
		StringBuilder b = new StringBuilder();
		// Guard against a cyclic cause chain (A -> B -> A), which used to loop forever
		java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for(Throwable e=t; e!=null && seen.add(e); e=getCause(e)) {
			if(!b.isEmpty()) {
				b.append('\n');
			}
			b.append(e.getLocalizedMessage());
		}
		return b.toString();
	}
	public static Throwable getCause(Throwable t) {
		Throwable c = t.getCause();
		return c!=t ? c : null;
	}
}
