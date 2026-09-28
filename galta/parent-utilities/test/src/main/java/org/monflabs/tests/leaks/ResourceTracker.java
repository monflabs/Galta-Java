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

import static org.monflabs.tests.leaks.BootstrapHelper.debug;
import static org.monflabs.tests.leaks.BootstrapHelper.info;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks resource allocations and their stack traces to detect leaks.
 * The tracked resources are held with strong references while tracking is on: a resource that
 * was never closed is a leak even if it became garbage. {@link #stopTracking()} releases them.
 * 
 * How it works:
 * - recordAllocation(): Adds resource to allocations map
 * - recordClosure(): Removes resource from allocations map  
 * - detectLeaks(): Anything still in allocations = leak!
 */
public class ResourceTracker {
    private static final ResourceTracker INSTANCE = new ResourceTracker();
    
    // Use IdentityHashMap for identity-based tracking (uses == not equals)
    // This is correct for tracking object instances, not values
    private final Map<Object, AllocationInfo> allocations = Collections.synchronizedMap(new IdentityHashMap<>());
    
    private volatile boolean tracking = false;
    // When set, only the allocations made by this thread are tracked
    private volatile Thread trackedThread;
    
    // Guard against recursive tracking (e.g., when ClassLoader opens FileInputStream during tracking)
    private static final ThreadLocal<Boolean> insideTracking = ThreadLocal.withInitial(() -> false);

    
    private ResourceTracker() {}
    
    public static ResourceTracker getInstance() {
        return INSTANCE;
    }
    
    /**
     * Start tracking resource allocations.
     */
    public void startTracking() {
    	startTracking(false);
    }
    
    /**
     * Start tracking resource allocations.
     * @param currentThreadOnly true to only track the resources allocated by the calling thread,
     * so resources opened concurrently by other threads (other tests, background tasks) are not
     * reported as leaks. The closures are recorded whatever the thread.
     */
    public void startTracking(boolean currentThreadOnly) {
        debug("[ResourceTracker] >>> startTracking() called");
        allocations.clear();
        trackedThread = currentThreadOnly ? Thread.currentThread() : null;
        tracking = true;
        debug("[ResourceTracker] >>> Tracking started, allocations cleared");
    }
    
    /**
     * Stop tracking and clear all tracked resources.
     */
    public void stopTracking() {
    	debug("[ResourceTracker] >>> stopTracking() called, had " + allocations.size() + " unclosed allocations");
        tracking = false;
        trackedThread = null;
        allocations.clear();
    }
    
    /**
     * Tell if the tracking is on.
     * @return true if tracking
     */
    public boolean isTracking() {
    	return tracking;
    }
    
    /**
     * Record a resource allocation with context (e.g., file path).
     * Protected against recursion (e.g., when ClassLoader opens FileInputStream during tracking).
     */
    public void recordAllocation(Object resource, String resourceType, String context) {
    	debug("[ResourceTracker] >>> recordAllocation() called for: " + 
                resourceType + " @ " + Integer.toHexString(System.identityHashCode(resource)) +
                (context != null && !context.isEmpty() ? " [" + context + "]" : "") +
                ", tracking=" + tracking);
        
        // Guard against recursion (e.g., ClassLoader opening FileInputStream during tracking)
        if (insideTracking.get()) {
        	debug("[ResourceTracker] >>> SKIPPED (recursive call detected)");
            return;  // ← Skip ClassLoader's FileInputStream
        }
        
        if (!tracking || resource == null) {
            debug("[ResourceTracker] >>> SKIPPED (tracking=" + tracking + ", resource=" + resource + ")");
            return;
        }
        Thread only = trackedThread;
        if (only != null && only != Thread.currentThread()) {
            debug("[ResourceTracker] >>> SKIPPED (allocated by another thread)");
            return;
        }
        
        insideTracking.set(true);
        try {
            // Check if already tracked (to avoid duplicates from constructor chaining)
            if (allocations.containsKey(resource)) {
                debug("[ResourceTracker] >>> ALREADY TRACKED - ignoring duplicate");
                return;  // Already tracking this resource (by identity)
            }
            
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            AllocationInfo info = new AllocationInfo(resourceType, context, stackTrace);
            
            allocations.put(resource, info);
            info("[ResourceTracker] >>> RECORDED allocation (total: " + allocations.size() + ")");
        } finally {
            insideTracking.set(false);  // Always clear flag
        }    	    	
    }
    
    /**
     * Record a resource closure - removes it from tracking.
     * Protected against recursion.
     */
    public void recordClosure(Object resource) {
    	if (resource == null) {
    		return;
    	}
    	debug("[ResourceTracker] >>> recordClosure() called for: " + 
            resource.getClass().getSimpleName() + " @ " + 
            Integer.toHexString(System.identityHashCode(resource)) +
            ", tracking=" + tracking);
        
    	// Guard against recursion
        if (insideTracking.get()) {
            debug("[ResourceTracker] >>> SKIPPED (recursive call detected)");
            return;
        }
        
        if (!tracking || resource == null) {
            debug("[ResourceTracker] >>> SKIPPED (tracking=" + tracking + ")");
            return;
        }
        
        insideTracking.set(true);
        try {
            // Remove the resource (by identity)
            AllocationInfo removed = allocations.remove(resource);
            
            if (removed != null) {
                debug("[ResourceTracker] >>> REMOVED from tracking (remaining: " + allocations.size() + ")");
            } else {
                info("[ResourceTracker] >>> NOT FOUND in allocations (size=" + allocations.size() + ")");
                info("[ResourceTracker] >>> This might be because:");
                info("[ResourceTracker] >>>   1. Resource was never tracked (created before startTracking)");
                info("[ResourceTracker] >>>   2. Resource was already closed");
                info("[ResourceTracker] >>>   3. Recursive call was skipped during allocation");
            }
        } finally {
            insideTracking.set(false);
        }
    }
    
    /**
     * Check for resource leaks and return a list of leaked resources.
     * Anything still in the allocations map is considered a leak (closed resources are removed).
     * 
     * IMPORTANT: Must synchronize on allocations during iteration to prevent ConcurrentModificationException.
     * Also sets insideTracking guard to prevent recordAllocation/recordClosure during iteration.
     */
    public List<LeakInfo> detectLeaks() {
        debug("[ResourceTracker] >>> detectLeaks() called");
        debug("[ResourceTracker] >>> Allocations remaining: " + allocations.size());
        
        List<LeakInfo> leaks = new ArrayList<>();
        
        // CRITICAL: Set recursion guard to prevent modifications during iteration!
        // Without this, class loading during iteration (e.g., creating LeakInfo objects)
        // can trigger recordAllocation(), which modifies the map during iteration.
        insideTracking.set(true);
        try {
            // Also synchronize during iteration for thread safety
            synchronized (allocations) {
                // Everything still in the map is a leak
                for (Map.Entry<Object, AllocationInfo> entry : allocations.entrySet()) {
                    Object resource = entry.getKey();
                    AllocationInfo info = entry.getValue();
                    
                    info("[ResourceTracker] >>> LEAK FOUND: " + info.getResourceType() + " @ " + Integer.toHexString(System.identityHashCode(resource)));
                    leaks.add(new LeakInfo(resource, info));
                }
            }
        } finally {
            insideTracking.set(false);
        }
        
        debug("[ResourceTracker] >>> Total leaks detected: " + leaks.size());
        return leaks;
    }
    
    /**
     * Information about when and where a resource was allocated.
     */
    public static class AllocationInfo {
        private final String resourceType;
        private final String context;
        private final StackTraceElement[] stackTrace;
        private final long timestamp;
        
        public AllocationInfo(String resourceType, String context, StackTraceElement[] stackTrace) {
            this.resourceType = resourceType;
            this.context = context;
            this.stackTrace = stackTrace;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getResourceType() {
            return resourceType;
        }
        
        public String getContext() {
            return context;
        }
        
        public StackTraceElement[] getStackTrace() {
            return stackTrace;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
    
    /**
     * Information about a detected resource leak.
     */
    public static class LeakInfo {
        private final Object resource;
        private final AllocationInfo allocationInfo;
        
        public LeakInfo(Object resource, AllocationInfo allocationInfo) {
            this.resource = resource;
            this.allocationInfo = allocationInfo;
        }
        
        public Object getResource() {
            return resource;
        }
        
        public AllocationInfo getAllocationInfo() {
            return allocationInfo;
        }
        
        public String formatReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("Resource Leak Detected!\n");
            sb.append("  Resource Type: ").append(allocationInfo.getResourceType()).append("\n");
            
            // Show context (e.g., file path) if available
            String context = allocationInfo.getContext();
            if (context != null && !context.isEmpty()) {
                sb.append("  Resource Context: ").append(context).append("\n");
            }
            
            sb.append("  Resource Instance: ").append(resource.getClass().getName()) .append("@").append(Integer.toHexString(System.identityHashCode(resource))).append("\n");
            sb.append("Allocated at:\n");
            
            // Format stack trace, skipping internal frames
            StackTraceElement[] stack = allocationInfo.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                StackTraceElement element = stack[i];
                String className = element.getClassName();
                
                // Skip internal tracking and instrumentation frames
                if (className.startsWith("org.monflabs.tests.leaks") ||
                    className.startsWith("net.bytebuddy") ||
                    className.equals("java.lang.Thread")) {
                    continue;
                }
                
                sb.append("  at ").append(element.toString()).append("\n");
            }
            
            return sb.toString();
        }
    }
}
