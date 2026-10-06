/*
 * Copyright (c) 2023-2026 Philippe Riand
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
package playground.impl.engine.util;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.monflabs.playground.ExecutionContext;

// Only retain the console log going to the current thread
// Not that this can be extended tyo 
public class StreamThreadDispatcher extends PrintStream {
	
	public static synchronized void install() {
		if(!(System.out instanceof StreamThreadDispatcher)) {
			System.setOut(new StreamThreadDispatcher(System.out));
		}
		if(!(System.err instanceof StreamThreadDispatcher)) {
			System.setErr(new StreamThreadDispatcher(System.err));
		}
	}
	
	public static synchronized void installThread(Thread t, ExecutionContext context) {
		if(t!=null) {
			((StreamThreadDispatcher)System.out).streams.put(t, context.getConsoleOut());
			((StreamThreadDispatcher)System.err).streams.put(t, context.getConsoleErr());
		}
	}
	
	public static synchronized void uninstallThread(Thread t, ExecutionContext context) {
		if(t!=null) {
			((StreamThreadDispatcher)System.out).streams.remove(t);
			((StreamThreadDispatcher)System.err).streams.remove(t);
		}
	}
	
	// Read on every print, from any thread
	private Map<Thread,PrintStream> streams = new ConcurrentHashMap<>();
	private PrintStream standard;
	
	public StreamThreadDispatcher(PrintStream standard) {
		super(standard);
		this.standard = standard;
	}
	
	private PrintStream getPrintStream() {
		// Should we better use a ThreadLocal?
		PrintStream ps = streams.get(Thread.currentThread());
		return ps!=null ? ps : standard;
	}
	
    @Override
    public void flush() {
    	getPrintStream().flush();
    }

    @Override
    public void close() {
    	getPrintStream().close();
    }

    @Override
    public void write(int b) {
    	getPrintStream().write(b);
    }

    @Override
    public void write(byte buf[], int off, int len) {
    	getPrintStream().write(buf,off,len);
    }

    @Override
    public void write(byte buf[]) throws IOException {
    	getPrintStream().write(buf);
    }

    @Override
	public void writeBytes(byte buf[]) {
    	getPrintStream().writeBytes(buf);
    }

    @Override
	public void print(boolean b) {
    	getPrintStream().print(b);
    }
    @Override
	public void print(char c) {
    	getPrintStream().print(c);
    }
    @Override
	public void print(int i) {
    	getPrintStream().print(i);
    }
    @Override
	public void print(long l) {
    	getPrintStream().print(l);
    }
    @Override
	public void print(float f) {
    	getPrintStream().print(f);
    }
    @Override
	public void print(double d) {
    	getPrintStream().print(d);
    }
    @Override
	public void print(char s[]) {
    	getPrintStream().print(s);
    }
    @Override
	public void print(String s) {
    	getPrintStream().print(s);
    }
    @Override
	public void print(Object obj) {
    	 getPrintStream().print(obj);
    }
    
    @Override
	public void println() {
    	getPrintStream().println();
    }
    @Override
	public void println(boolean x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(char x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(int x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(long x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(float x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(double x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(char[] x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(String x) {
    	getPrintStream().println(x);
    }
    @Override
	public void println(Object x) {
    	getPrintStream().println(x);
    }

    @Override
	public PrintStream printf(String format, Object ... args) {
    	return getPrintStream().printf(format, args);
    }
    @Override
	public PrintStream printf(Locale l, String format, Object ... args) {
    	return getPrintStream().printf(l, format, args);
    }
    @Override
	public PrintStream format(String format, Object ... args) {
    	return getPrintStream().format(format, args);
    }
    @Override
	public PrintStream format(Locale l, String format, Object ... args) {
    	return getPrintStream().format(l, format, args);
    }

    @Override
	public PrintStream append(CharSequence csq) {
    	return getPrintStream().append(csq);
    }
    @Override
	public PrintStream append(CharSequence csq, int start, int end) {
    	return getPrintStream().append(csq, start, end);
    }
    @Override
	public PrintStream append(char c) {
    	return getPrintStream().append(c);
    }

}