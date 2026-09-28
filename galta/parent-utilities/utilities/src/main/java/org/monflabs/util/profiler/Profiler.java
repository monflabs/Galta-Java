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
package org.monflabs.util.profiler;

import java.util.concurrent.Callable;

import org.monflabs.util.profiler.JavaProfiler.ProfileRunnable;
import org.monflabs.util.profiler.impl.JavaProfilerImpl;


/**
 * ProfilerFactory.
 */
public final class Profiler {
	
	private static JavaProfiler instance = new JavaProfilerImpl();

	public static JavaProfiler get() {
		return instance;
	}

	public static void set(JavaProfiler profiler) {
		Profiler.instance = profiler;
	}

	
	//
	// Shortcuts
	//
	
	public static void reset() {
		instance.reset();
	}
    public static void start() {
    	instance.start();
    }
    public static void stop() {
    	instance.stop();
    }

    public static boolean isStarted() {
        return instance.isStarted();
    }

    public static <T> T profile(String type, Callable<T> callable) {
    	return instance.profile(type, callable);
    }
    public static <T> T profile(String type, String param, Callable<T> callable) {
    	return instance.profile(type, param, callable);
    }

    public static <T> void profile(String type, ProfileRunnable runnable) {
    	instance.profile(type, runnable);
    }
    public static <T> void profile(String type, String param, ProfileRunnable runnable) {
    	instance.profile(type, param, runnable);
    }
    
	public static ProfilerSnapshot createSnapshot(String notes) {
		return instance.createSnapshot(notes);
	}
}
