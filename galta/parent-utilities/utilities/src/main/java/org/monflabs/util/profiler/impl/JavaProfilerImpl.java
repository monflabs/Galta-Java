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
package org.monflabs.util.profiler.impl;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Callable;

import org.monflabs.util.Console;
import org.monflabs.util.profiler.JavaProfiler;
import org.monflabs.util.profiler.ProfilerException;
import org.monflabs.util.profiler.ProfilerSnapshot;


/**
 * Profiler implementation that measure a block execution time.
 */
public final class JavaProfilerImpl implements JavaProfiler {
	
	// Read by every profiled thread without a lock
	private volatile boolean started;
	private volatile RuntimeAggregator mainAggregator;
	private volatile ThreadLocal<RuntimeAggregator> aggregators;

	// timer to measure the CPU time
	private ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

	
	public JavaProfilerImpl() {
		this.mainAggregator = new RuntimeAggregator(null,"Profiler",null);
		this.aggregators = new ThreadLocal<RuntimeAggregator>();
	}
	
	// The CPU time of the current thread, or -1 when it is not available: the JVM
	// returns -1 for a virtual thread
	private long cpuTime() {
		try {
			return threadMXBean.getCurrentThreadCpuTime();
		} catch(UnsupportedOperationException ex) {
			return -1;
		}
	}

	RuntimeAggregator getCurrentAggregator() {
		RuntimeAggregator agg = aggregators.get();
		return agg!=null ? agg : mainAggregator;
	}
	
    RuntimeAggregator getMainAggregator() {
   		return mainAggregator;
    }

	@Override
	public ProfilerSnapshot createSnapshot(String notes) {
		return getMainAggregator().getSnapshotAggregator(notes);
	}

	@Override
	public synchronized void reset() {
		this.mainAggregator = new RuntimeAggregator(null,"Profiler",null);
		this.aggregators = new ThreadLocal<RuntimeAggregator>();
    }
	
    @Override
	public synchronized void start() {
    	if(!started) {
    		this.started = true;
            Console.log( "Starting profiler" );
    	}
    }

    @Override
	public synchronized void stop() {
    	if(started) {
    		this.started = false;
			Console.log( "Profiler is stopped" );
    	}
    }

    @Override
	public synchronized boolean isStarted() {
        return started;
    }

    @Override
	public <T> T profile(String type, Callable<T> callable) {
    	return profile(type,null,callable);
    }
    @Override
	public <T> T profile(String type, String param, Callable<T> callable) {
        if(started) {
        	// The ThreadLocal and the root are read once: reset() replaces them, and a profile()
        	// that was in flight must restore the state of the ThreadLocal it modified - reading
        	// the fields again in finally used to leak the old tree into the new ThreadLocal
        	ThreadLocal<RuntimeAggregator> tl = aggregators;
        	RuntimeAggregator main = mainAggregator;
	    	RuntimeAggregator current = tl.get();
	    	RuntimeAggregator parent = current!=null ? current : main;
	    	RuntimeAggregator child = parent.getChild(parent,type,param);
	        tl.set(child);

	        long startWallTime = System.nanoTime();
	        long startCpuTime = cpuTime();
            try {
            	return callable.call();
            } catch(Exception e) {
            	if(e instanceof RuntimeException rt) {
            		throw rt;
            	}
            	throw new ProfilerException(e);
            } finally {
	        	long endCpuTime = startCpuTime>=0 ? cpuTime() : -1;
	        	// -1: not available (a virtual thread, or no CPU time support), not a zero
	        	child.addInfo(System.nanoTime()-startWallTime, endCpuTime>=0 ? endCpuTime-startCpuTime : -1);
	            if(current==null) {
	                tl.remove();
	            } else {
	            	tl.set(current);
	            }
            }
        } else {
            try {
            	return callable.call();
            } catch(Exception e) {
            	if(e instanceof RuntimeException rt) {
            		throw rt;
            	}
            	throw new ProfilerException(e);
            }
        }
    }

    @Override
	public <T> void profile(String type, ProfileRunnable runnable) {
    	profile(type,null,runnable);
    }
    @Override
	public <T> void profile(String type, String param, ProfileRunnable runnable) {
    	profile(type, param, () -> {
    		runnable.run();
    		return null;
    	});
    }    
}
