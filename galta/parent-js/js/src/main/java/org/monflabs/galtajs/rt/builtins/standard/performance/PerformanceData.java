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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.jsonfactory.JSArray;
import org.monflabs.galtajs.jsonfactory.JSObject;
import org.monflabs.galtajs.rt.RuntimeUtil;
import org.monflabs.util.StringUtil;

/**
 * Object holding the performance data for an execution context.
 * Note that it does not contains what is specific to the browser.
 */
public class PerformanceData {

	private JSEnvironment env;
	
	private long timeOrigin;
	private List<PerformanceEntry> entries = new ArrayList<>();
	
	public PerformanceData(JSEnvironment env) {
		this.env = env;
		this.timeOrigin = System.nanoTime();
	}
	
	public JSEnvironment getEnvironment() { 
		return env;
	}
	
	public long getTimeOrigin() {
		return timeOrigin;
	}

	public double now() {
		// Milliseconds as a double
		return (System.nanoTime()-timeOrigin)/1_000_000.0;
	}

	private PerformanceMark findMark(String name) {
		for(int i=entries.size()-1; i>=0; i++) {
			PerformanceEntry e = entries.get(i);
			if(e instanceof PerformanceMark pm) {
				return pm;
			}
		}
		return null;
	}
	
	public List<PerformanceEntry> getEntries() {
		return entries;
	}
	
	public synchronized PerformanceMark mark(String name) {
		PerformanceMark m = new PerformanceMark(env,name,now());
		entries.add(m);
		return m;
	}
	
	public synchronized PerformanceMeasure measure(String name) {
		return measure(name, null, null);
	}
	public synchronized PerformanceMeasure measure(String name, String startMark) {
		return measure(name, startMark, null);
	}
	public synchronized PerformanceMeasure measure(String name, String startMark, String endMark) {
		double startTime;
		if(StringUtil.isNotEmpty(startMark)) {
			PerformanceMark pm = findMark(startMark);
			if(pm==null) {
				throw RuntimeUtil.error("Failed to execute 'measure' on 'Performance': The mark '{0}' does not exist.",startMark);
			}
			startTime = pm.getStartTime();
		} else {
			startTime = now();
		}
		double endTime = 0.0;
		if(StringUtil.isNotEmpty(endMark)) {
			PerformanceMark pm = findMark(endMark);
			if(pm==null) {
				throw RuntimeUtil.error("Failed to execute 'measure' on 'Performance': The mark '{0}' does not exist.",endMark);
			}
			endTime = pm.getStartTime();
		} else {
			endTime = now();
		}
		PerformanceMeasure m = new PerformanceMeasure(env,name,startTime,endTime-startTime);
		entries.add(m);
		return m;
	}
	
	public synchronized void clearMarks(String name) {
		for(Iterator<PerformanceEntry> it=entries.iterator(); it.hasNext(); ) {
			PerformanceEntry e = it.next();
			if(e instanceof PerformanceMark pm) {
				if(StringUtil.isEmpty(name) || name.equals(pm.getName())) {
					it.remove();
				}
			}
		}
	}
	
	public synchronized void clearMeasures(String name) {
		for(Iterator<PerformanceEntry> it=entries.iterator(); it.hasNext(); ) {
			PerformanceEntry e = it.next();
			if(e instanceof PerformanceMeasure pm) {
				if(StringUtil.isEmpty(name) || name.equals(pm.getName())) {
					it.remove();
				}
			}
		}
	}
	
	public synchronized List<PerformanceEntry> getEntriesByName(String name, String type) {
		List<PerformanceEntry> list = new ArrayList<>();
		for(Iterator<PerformanceEntry> it=entries.iterator(); it.hasNext(); ) {
			PerformanceEntry e = it.next();
			if(e.getName().equals(name) && (type==null || type.equals(e.getEntryType())) ) {
				list.add(e);
			}
		}
		return list;
	}
	
	public synchronized List<PerformanceEntry> getEntriesByType(String type) {
		List<PerformanceEntry> list = new ArrayList<>();
		for(Iterator<PerformanceEntry> it=entries.iterator(); it.hasNext(); ) {
			PerformanceEntry e = it.next();
			if(type.equals(e.getEntryType())) {
				list.add(e);
			}
		}
		return list;
	}
	
	public JSObject toJSON() {
		JSObject o = JSObject.create(getEnvironment());
		o.setOwnProperty("timeOrigin", (double)getTimeOrigin());
		JSArray a = JSArray.create(getEnvironment());
		for(PerformanceEntry e: entries) {
			a.arrayAdd(e.toJSON());
		}
		o.setOwnProperty("entries", a);
		return o;
	}
}