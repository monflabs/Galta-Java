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

/**
 * Describes a timer with an intended resolution of a nano second.
 */
public interface HighResolutionTimer {

	public static enum Type {
    	WALL_TIME_COUNTER,
    	CPU_TIME_COUNTER,
	}

    /**
     * Get the timer type.
     */
    public Type getTimerType();

    /**
     * Get the mesured time in nano seconds (10e-6).
     */
    public long getTime();
}

