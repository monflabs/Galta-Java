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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.util.profiler.ProfilerSnapshot;

/**
 * Profiler aggregator.
 */
public final class RuntimeAggregator extends BaseAggregator<RuntimeAggregator> {
	
    public RuntimeAggregator(RuntimeAggregator parent, String type, String param) {
    	super(parent,type,param);
    }

    public ProfilerSnapshot getSnapshotAggregator(String notes) {
    	SnapshotAggregator agg = new SnapshotAggregator(null, this);
    	aggregateHierachy(this,agg);
    	return ProfilerSnapshot.create(OffsetDateTime.now(),notes,agg);
    }
    // Post process the hierarchy
    // We aggregate all the children of a same type at each level
    @SuppressWarnings({ "unchecked", "rawtypes" })
	static void aggregateHierachy(RuntimeAggregator rt, SnapshotAggregator sn) {
    	Map<String, SnapshotAggregator> aggChildren = new HashMap<>();
    	for(RuntimeAggregator rtc: (List<RuntimeAggregator>)(List)rt.getChildren()) {
    		String type = rtc.getType();
    		SnapshotAggregator aggParent = aggChildren.get(type);
    		if(aggParent==null) {
    			aggParent = new SnapshotAggregator(sn, type, null);
    			aggChildren.put(type, aggParent);
    		}
    		SnapshotAggregator snc = new SnapshotAggregator(aggParent, rtc);
    		aggParent.appendChild(snc);
    		aggParent.merge(snc);
    		// recursively do the children
    		aggregateHierachy(rtc,snc);
    	}
    	
    	// Remove the unnecessary aggregators
    	for(SnapshotAggregator agg: aggChildren.values()) {
    		if(agg.getChildren().size()==1) {
    			SnapshotAggregator first = ((List<SnapshotAggregator>)(List)agg.getChildren()).iterator().next();
    			first.parent = sn;
    			sn.appendChild(first);
    		} else {
    			agg.parent = sn;
    			sn.appendChild(agg);
    		}
    	}
    }
    
    void reinit() {
        this.clearChildren();
    }

    // Aggregators are shared by all the profiled threads
    // A negative cpuTime means "not available" (e.g. measured on a virtual thread): it is
    // left out of the CPU statistics instead of being counted as zero
    synchronized void addInfo( long wallTime , long cpuTime) {
        if( count>0 ) {
            if( wallTime<minWallTime ) minWallTime = wallTime;
            if( wallTime>maxWallTime ) maxWallTime = wallTime;
        } else {
        	minWallTime = wallTime;
        	maxWallTime = wallTime;
        }
        count++;
        totalWallTime += wallTime;
        if( cpuTime>=0 ) {
            if( cpuCount>0 ) {
                if( cpuTime<minCpuTime ) minCpuTime = cpuTime;
                if( cpuTime>maxCpuTime ) maxCpuTime = cpuTime;
            } else {
            	minCpuTime = cpuTime;
            	maxCpuTime = cpuTime;
            }
            cpuCount++;
            totalCpuTime += cpuTime;
        }
    }
}
