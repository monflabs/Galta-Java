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
package org.monflabs.galtajs.rt.builtins.standard.performance;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSObject;

/**
 * https://developer.mozilla.org/en-US/docs/Web/API/PerformanceEntry
 */
public abstract class PerformanceEntry {
	
	private JSEnvironment env;
	
	private double startTime;
	private double duration;
	
	public PerformanceEntry(JSEnvironment env, double startTime, double duration) {
		this.env = env;
		this.startTime = startTime;
		this.duration = duration;
	}
	
	public JSEnvironment getEnvironment() {
		return env;
	}
	
	public abstract String getName();
	public abstract String getEntryType();
	
	public double getStartTime() {
		return startTime;
	}
	public double getDuration() {
		return duration;
	}
	
	public JSObject toJSON() {
		return JSObject.of(getEnvironment(),"name",
				getName(), "entryType", 
				getEntryType(),  "startTime",
				getStartTime(), "duration",
				getDuration()
			);
	}
}
