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

import org.monflabs.util.profiler.Aggregator;


/**
 * Profiler aggregator.
 */
public final class SnapshotAggregator extends BaseAggregator<SnapshotAggregator> {

	private long avgWallTime; 
	private long childrenWallTime; 
	private long specificWallTime;
	private long avgCpuTime; 
	private long childrenCpuTime; 
	private long specificCpuTime;
	
    public SnapshotAggregator(SnapshotAggregator parent, String type, String param) {
    	super(parent,type,param);
    }
    public SnapshotAggregator(SnapshotAggregator parent, int id, String type, String param, int count, long minWallTime, long maxWallTime, long totalWallTime, long avgWallTime, long childrenWallTime, long specificWallTime, long minCpuTime, long maxCpuTime, long totalCpuTime, long avgCpuTime, long childrenCpuTime, long specificCpuTime) {
    	super(parent,type,param);
    	this.count = count;

    	this.minWallTime = minWallTime;
    	this.maxWallTime = maxWallTime;
    	this.totalWallTime = totalWallTime;
    	this.avgWallTime = avgWallTime;
    	this.childrenWallTime = childrenWallTime;
    	this.specificWallTime = specificWallTime;
    	
    	this.minCpuTime = minCpuTime;
    	this.maxCpuTime = maxCpuTime;
    	this.totalCpuTime = totalCpuTime;
    	this.avgCpuTime = avgCpuTime;
    	this.childrenCpuTime = childrenCpuTime;
    	this.specificCpuTime = specificCpuTime;
    }
    SnapshotAggregator(SnapshotAggregator parent, Aggregator src) {
    	super(parent,src.getType(),src.getParam());
    	// A consistent copy: a RuntimeAggregator is updated (synchronized) by the profiled threads
    	synchronized(src) {
    	this.count = src.getCount();

    	this.minWallTime = src.getMinWallTime();
    	this.maxWallTime = src.getMaxWallTime();
    	this.totalWallTime = src.getTotalWallTime();
    	this.avgWallTime = src.getAvgWallTime();
    	this.childrenWallTime = src.getChildrenWallTime();
    	this.specificWallTime = src.getSpecificWallTime();

    	this.minCpuTime = src.getMinCpuTime();
    	this.maxCpuTime = src.getMaxCpuTime();
    	this.totalCpuTime = src.getTotalCpuTime();
    	this.avgCpuTime = src.getAvgCpuTime();
    	this.childrenCpuTime = src.getChildrenCpuTime();
    	this.specificCpuTime = src.getSpecificCpuTime();
    	}
    }
    
    @Override
	public long getChildrenCpuTime() {
    	return childrenCpuTime;
    }
    @Override
	public long getSpecificCpuTime() {
    	return specificCpuTime;
    }
    @Override
	public long getAvgCpuTime() {
    	return avgCpuTime;
    }

    @Override
	public long getChildrenWallTime() {
    	return childrenWallTime;
    }
    @Override
	public long getSpecificWallTime() {
    	return specificWallTime;
    }
    @Override
	public long getAvgWallTime() {
    	return avgWallTime;
    }

    
    // This is used by the profiler application to temporarily aggregate the
    // similar aggregator into a single one. For example, we are aggregating all
    // the calls to the same JS expression so we have the whole total for this
    // expression.
    public void merge( SnapshotAggregator aggregator ) {
        if(aggregator!=null && aggregator.count>0) {
            if(this.count>0) {
                if( aggregator.minWallTime<minWallTime ) {
                    this.minWallTime = aggregator.minWallTime;
                }
                if( aggregator.maxWallTime>maxWallTime ) {
                    this.maxWallTime = aggregator.maxWallTime;
                }
                if( aggregator.minCpuTime<minCpuTime ) {
                    this.minCpuTime = aggregator.minCpuTime;
                }
                if( aggregator.maxCpuTime>maxCpuTime ) {
                    this.maxCpuTime = aggregator.maxCpuTime;
                }
            } else {
                this.minWallTime = aggregator.minWallTime;
                this.maxWallTime = aggregator.maxWallTime;
                this.minCpuTime = aggregator.minCpuTime;
                this.maxCpuTime = aggregator.maxCpuTime;
            }
            
            this.count += aggregator.count;
            
            this.totalWallTime += aggregator.totalWallTime;
        	this.childrenWallTime += aggregator.getChildrenWallTime();
        	this.specificWallTime += aggregator.getSpecificWallTime();
        	this.avgWallTime = totalWallTime/count;

        	this.totalCpuTime += aggregator.totalCpuTime;
        	this.childrenCpuTime += aggregator.getChildrenCpuTime();
        	this.specificCpuTime += aggregator.getSpecificCpuTime();
        	this.avgCpuTime = totalCpuTime/count;
        }
    }
}
