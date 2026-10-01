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
package org.monflabs.tests.leaks;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bridge between the instrumented JDK classes and the {@link ResourceTracker}.
 * <p>
 * {@link ResourceLeakAgent#install()} appends this class to the bootstrap class path, so the
 * advice inlined in the JDK classes (<code>FileInputStream</code>, <code>ZipFile</code>...) can
 * call it. The bootstrap copy of the class cannot see the application classes: the agent hands
 * it the tracker as JDK-typed callbacks ({@link #wire(BooleanSupplier, BiConsumer, Consumer)}),
 * on both the bootstrap copy and the application copy of this class. Nothing is looked up by
 * name, so the tracker works whatever the class loader that loaded the test library.
 * <p>
 * The callbacks are checked first: while the tracker does not record, an allocation or a closure
 * costs a volatile read and a call; nothing is formatted.
 * <p>
 * This class must only use JDK types.
 */
public class BootstrapHelper {

	/**
	 * Trace level: 0 silent (default), 1 errors and main events, 2 debug.
	 * The agent copies it to the bootstrap copy when installed; use
	 * {@link ResourceLeakAgent#setTraceLevel(int)} to change both later.
	 */
	public static volatile int TRACE_LEVEL = 0;

	// The callbacks, set by ResourceLeakAgent.install()
	private static volatile BooleanSupplier recording;
	private static volatile BiConsumer<Object,String[]> allocationSink;
	private static volatile Consumer<Object> closureSink;

	public static void debug(String message) {
		if(TRACE_LEVEL>=2) {
			System.out.println("DEBUG: " + message);
		}
	}
	public static void info(String message) {
		if(TRACE_LEVEL>=1) {
			System.out.println("INFO:  " + message);
		}
	}
	public static void error(String message) {
		if(TRACE_LEVEL>=1) {
			System.err.println("ERROR:  " + message);
		}
	}
	public static void error(String message, Throwable t) {
		if(TRACE_LEVEL>=1) {
			System.err.println("ERROR:  " + message);
			t.printStackTrace();
		}
	}
	public static void log(String message) {
		System.out.println(message);
	}
	public static boolean isDebug() {
		return TRACE_LEVEL>=2;
	}

	/**
	 * Connect the helper to a tracker.
	 * @param recording tells if an allocation made by the current thread is recorded
	 * @param allocationSink receives the allocated resource and {type, context}
	 * @param closureSink receives the closed resource
	 */
	public static void wire(BooleanSupplier recording, BiConsumer<Object,String[]> allocationSink, Consumer<Object> closureSink) {
		BootstrapHelper.allocationSink = allocationSink;
		BootstrapHelper.closureSink = closureSink;
		BootstrapHelper.recording = recording;
	}

	/**
	 * Tell if the helper is connected to a tracker.
	 * @return true if wired
	 */
	public static boolean isWired() {
		return recording!=null && allocationSink!=null && closureSink!=null;
	}

	/**
	 * Called by the instrumented constructors.
	 * @param resource the resource being constructed
	 * @param args the constructor arguments
	 */
	public static void constructed(Object resource, Object[] args) {
		BooleanSupplier r = recording;
		if(r==null || resource==null || !r.getAsBoolean()) {
			return;
		}
		record(resource, resource.getClass().getSimpleName(), extractFilePathFromArgs(args));
	}

	/**
	 * Record a resource allocation, the context (like a file path) being extracted from the
	 * constructor arguments.
	 */
	public static void recordAllocationWithArgs(Object resource, String resourceType, Object[] args) {
		BooleanSupplier r = recording;
		if(r==null || resource==null || !r.getAsBoolean()) {
			return;
		}
		record(resource, resourceType, extractFilePathFromArgs(args));
	}

	/**
	 * Record a resource allocation with a context.
	 */
	public static void recordAllocation(Object resource, String resourceType, String context) {
		BooleanSupplier r = recording;
		if(r==null || resource==null || !r.getAsBoolean()) {
			return;
		}
		record(resource, resourceType, context);
	}

	private static void record(Object resource, String resourceType, String context) {
		BiConsumer<Object,String[]> sink = allocationSink;
		if(sink!=null) {
			try {
				sink.accept(resource, new String[] {resourceType, context});
			} catch(Throwable t) {
				// Never break the application
				error("Error recording an allocation", t);
			}
		}
	}

	/**
	 * Extract the context from the constructor arguments: the strings and paths (quoted), and the
	 * files. The other arguments are ignored.
	 */
	private static String extractFilePathFromArgs(Object[] args) {
		if(args==null || args.length==0) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		for(Object arg: args) {
			if(arg instanceof CharSequence || arg instanceof Path) {
				if(!b.isEmpty()) {
					b.append(",");
				}
				b.append("\"").append(arg.toString()).append("\"");
			} else if(arg instanceof File f) {
				if(!b.isEmpty()) {
					b.append(",");
				}
				b.append(f.getClass().getSimpleName()).append("@").append(Integer.toHexString(System.identityHashCode(f)))
				 .append(":").append(f.getPath());
			}
		}
		return b.toString();
	}

	/**
	 * Record a resource closure. Called from the instrumented close() methods.
	 * The closures are recorded whatever the thread.
	 */
	public static void recordClosure(Object resource) {
		Consumer<Object> sink = closureSink;
		if(sink==null || resource==null) {
			return;
		}
		try {
			sink.accept(resource);
		} catch(Throwable t) {
			error("Error recording a closure", t);
		}
	}
}
