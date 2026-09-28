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
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Minimal bootstrap helper class.
 * This class will be added to the bootstrap classloader and uses reflection
 * to call ResourceTracker (which stays in the application classloader).
 * 
 * IMPORTANT: Update TRACKER_CLASS_NAME to match your actual package!
 * 
 * This version initializes LAZILY when first called from the bootstrap classloader,
 * avoiding the double-loading issue.
 */
public class BootstrapHelper {
    public static int TRACE_LEVEL = 0;
    
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
    public static void log(String message) {
   		System.out.println(message);
    }
    public static boolean isDebug(String message) {
    	return TRACE_LEVEL>=2;
    }
	
	
    // UPDATE THIS TO MATCH YOUR ACTUAL PACKAGE!️
    private static final String TRACKER_CLASS_NAME = "org.monflabs.tests.leaks.ResourceTracker";
    
    private static Class<?> trackerClass;
    private static Object trackerInstance;
    private static Method recordAllocationMethod;
    private static Method recordClosureMethod;
    private static volatile boolean initialized = false;
    private static volatile boolean initializing = false;
    
    /**
     * Initialize the helper with reflection references to ResourceTracker.
     * This is called LAZILY on first use from the bootstrap classloader.
     */
    private static synchronized void ensureInitialized() {
        if (initialized || initializing) {
            return;
        }
        
        initializing = true;
        
        try {
            info ("Initializing BootstrapHelper (from " + BootstrapHelper.class.getClassLoader() + ")...");
            debug("Looking for ResourceTracker class: " + TRACKER_CLASS_NAME);
            
            // Get ResourceTracker class from the application classloader
            // Use the system classloader to find it
            ClassLoader systemCL = ClassLoader.getSystemClassLoader();
            trackerClass = Class.forName(TRACKER_CLASS_NAME, true, systemCL);
            debug("✓ Found ResourceTracker class: " + trackerClass);
            debug("  ResourceTracker loaded by: " + trackerClass.getClassLoader());
            
            // Get the singleton instance
            Method getInstanceMethod = trackerClass.getMethod("getInstance");
            trackerInstance = getInstanceMethod.invoke(null);
            debug("✓ Got ResourceTracker instance: " + trackerInstance);
            
            // Get the methods we need
            recordAllocationMethod = trackerClass.getMethod("recordAllocation", 
                Object.class, String.class, String.class);
            recordClosureMethod = trackerClass.getMethod("recordClosure", Object.class);
            debug("✓ Got methods: recordAllocation and recordClosure");
            
            initialized = true;
            debug("✓ BootstrapHelper initialized successfully!");
            
        } catch (ClassNotFoundException e) {
            error("   BootstrapHelper ERROR: Could not find ResourceTracker class: " + TRACKER_CLASS_NAME);
            error("   Make sure TRACKER_CLASS_NAME in BootstrapHelper matches your actual package!");
            error("   BootstrapHelper is loaded by: " + BootstrapHelper.class.getClassLoader());
            e.printStackTrace();
        } catch (Exception e) {
        	error("   BootstrapHelper ERROR: Failed to initialize: " + e.getMessage());
        	error("   BootstrapHelper is loaded by: " + BootstrapHelper.class.getClassLoader());
            e.printStackTrace();
        } finally {
            initializing = false;
        }
    }
    
    /**
     * Record a resource allocation with additional context.
     * Called from instrumented constructors.
     */
    public static void recordAllocation(Object resource, String resourceType, String context) {
        ensureInitialized();
        
        if (!initialized) {
            return;
        }
        
        if (recordAllocationMethod != null && trackerInstance != null) {
            try {
                recordAllocationMethod.invoke(trackerInstance, resource, resourceType, context);
                debug("Recorded allocation: " + resourceType + " @ " + Integer.toHexString(System.identityHashCode(resource)) + " [" + context + "]");
            } catch (Exception e) {
                // Silently ignore to avoid breaking the application
                error("Error recording allocation: " + e.getMessage());
            }
        }
    }
    
    /**
     * Record a resource allocation with constructor arguments.
     * Extracts context (like file path) from the arguments.
     * Called from instrumented file stream constructors.
     */
    public static void recordAllocationWithArgs(Object resource, String resourceType, Object[] args) {
        String context = extractFilePathFromArgs(args);
        recordAllocation(resource, resourceType, context);
    }
    
    /**
     * Extract file path from constructor arguments.
     * Handles: String path, File object, FileDescriptor, etc.
     */
    private static String extractFilePathFromArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

    	StringBuilder b = new StringBuilder();
    	for(int i=0; i<args.length; i++) {
	        Object arg = args[i];
	        
	        // Only interesting param types - ignore the others
	        // (a Path is reported like a string, quoted)
	        if (arg instanceof CharSequence || arg instanceof Path) {
	    		if(!b.isEmpty()) {
	    			b.append(",");
	    		}
				b.append("\"");
	            b.append(arg.toString());
				b.append("\"");
	            continue;
	        }
	        if (arg instanceof File) {
	    		if(!b.isEmpty()) {
	    			b.append(",");
	    		}
		        b.append(arg.getClass().getSimpleName());
				b.append("@");
				b.append(Integer.toHexString(System.identityHashCode(arg)));
				b.append(":");
	            b.append(((File)arg).getPath());
	            continue;
	        }
//	        // Handle File object: new FileInputStream(new File("/path"))
//	        if (arg.getClass().getName().equals("java.io.File")) {
//	            try {
//	                // Use reflection to call getPath()
//	                java.lang.reflect.Method getPath = arg.getClass().getMethod("getPath");
//	                Object path = getPath.invoke(arg);
//	                return path != null ? path.toString() : "unknown";
//	            } catch (Exception e) {
//	                return "File@" + Integer.toHexString(System.identityHashCode(arg));
//	            }
//	        }
    	}
        
        // Handle FileDescriptor or other types
        return b.toString();
    }

    /**
     * Record a resource closure.
     * Called from instrumented close() methods.
     */
    public static void recordClosure(Object resource) {
        info("[BootstrapHelper] recordClosure() called for: " + resource.getClass().getSimpleName() + " @ " + Integer.toHexString(System.identityHashCode(resource)));
        
        ensureInitialized();
        
        if (!initialized) {
            error("[BootstrapHelper] NOT initialized - cannot record closure");
            return;
        }
        
        if (recordClosureMethod != null && trackerInstance != null) {
            try {
                debug("[BootstrapHelper] Invoking recordClosure on ResourceTracker...");
                recordClosureMethod.invoke(trackerInstance, resource);
                debug("[BootstrapHelper] ✓ recordClosure invoked successfully");
                info("   Recorded closure: " + resource.getClass().getSimpleName() + " @ " + Integer.toHexString(System.identityHashCode(resource)));
            } catch (Exception e) {
                error("[BootstrapHelper] ERROR invoking recordClosure: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
        	error("[BootstrapHelper] recordClosureMethod or trackerInstance is NULL!");
        	error("  recordClosureMethod: " + recordClosureMethod);
        	error("  trackerInstance: " + trackerInstance);
        }
    }
}
