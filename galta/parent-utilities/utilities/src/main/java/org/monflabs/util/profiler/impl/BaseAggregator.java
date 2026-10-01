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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.monflabs.util.profiler.Aggregator;


/**
 * Profiler aggregator.
 */
public abstract class BaseAggregator<T extends BaseAggregator<?>> implements Aggregator {

    protected T parent;
    protected String type;
    protected String param;
    
    protected int count;
    // The number of measures that had a CPU time: none means the CPU times are not available
    protected int cpuCount;
    
    protected long minCpuTime;
    protected long maxCpuTime;
    protected long totalCpuTime;
    
    protected long minWallTime;
    protected long maxWallTime;
    protected long totalWallTime;
    
    public BaseAggregator(T parent, String type, String param) {
        this.parent = parent;
        this.type = type;
        this.param = param;
    }
    
    @Override
	public T getParent() {
        return parent;
    }

    @Override
	public String getType() {
        return type;
    }

    @Override
	public String getParam() {
        return param;
    }
    
    
    //
    // Children
    //

    private record Key(String type, String param) {}

    // Indexed by (type, param); the list keeps the insertion order
    private Map<Key, BaseAggregator<?>> index = new HashMap<>();
    private List<BaseAggregator<?>> children = new ArrayList<>();

	@SuppressWarnings("unchecked")
	public synchronized T getChild(RuntimeAggregator parent, String type, String param) {
        Key key = new Key(type, param);
        BaseAggregator<?> a = index.get(key);
        if(a==null) {
            a = new RuntimeAggregator(parent, type, param);
            index.put(key, a);
            children.add(a);
        }
        return (T)a;
    }
    public synchronized void appendChild(T v) {
        index.putIfAbsent(new Key(v.getType(), v.getParam()), v);
        children.add(v);
    }

	@Override
	public synchronized List<Aggregator> getChildren(){
        return new ArrayList<Aggregator>(children);
    }

    public synchronized void clearChildren() {
    	this.index = new HashMap<>();
    	this.children = new ArrayList<>();
    }



    //
    // Execution 
    //
	
    // The counters are updated under the aggregator lock (RuntimeAggregator.addInfo()):
    // they are read under it too, so a reader sees consistent and up to date values.
    // The CPU times are -1 when they are not available (measured on virtual threads).

    @Override
	public synchronized int getCount() {
        return count;
    }

    @Override
	public synchronized long getMinCpuTime() {
        return cpuCount>0 ? minCpuTime : -1;
    }
    @Override
	public synchronized long getMaxCpuTime() {
        return cpuCount>0 ? maxCpuTime : -1;
    }
    @Override
	public synchronized long getTotalCpuTime() {
        return cpuCount>0 ? totalCpuTime : -1;
    }
    @Override
	public long getChildrenCpuTime() {
    	long ts = 0;
    	boolean available = false;
    	for(Aggregator a: getChildren() ) {
    		long t = a.getTotalCpuTime();
    		if(t>=0) {
    			ts += t;
    			available = true;
    		}
    	}
        return available ? ts : -1;
    }
    @Override
	public long getSpecificCpuTime() {
    	long total = getTotalCpuTime();
    	if(total<0) {
    		return -1;
    	}
    	long children = getChildrenCpuTime();
        return children>0 ? total-children : total;
    }
    @Override
	public synchronized long getAvgCpuTime() {
        return cpuCount>0 ? totalCpuTime/cpuCount : -1;
    }

    @Override
	public synchronized long getMinWallTime() {
        return minWallTime;
    }
    @Override
	public synchronized long getMaxWallTime() {
        return maxWallTime;
    }
    @Override
	public synchronized long getTotalWallTime() {
        return totalWallTime;
    }
    @Override
	public long getChildrenWallTime() {
    	long ts = 0;
    	for(Aggregator a: getChildren() ) {
    		ts += a.getTotalWallTime();
    	}
        return ts;
    }
    @Override
	public long getSpecificWallTime() {
        return getTotalWallTime()-getChildrenWallTime();
    }
    @Override
	public synchronized long getAvgWallTime() {
        return count>0 ? totalWallTime/count : 0;
    }
    
    @Override
	public void dump(PrintStream pw) {
   		dump(pw,this,0);
    }

    private static void dump(PrintStream pw, Aggregator a, int level) {
        if( a.getCount()>0 ) {
	        for( int i=0; i<level; i++ ) {
	            pw.print( "  " );
	        }
	        if( a.getParam()!=null ) {
	            String str = a.getParam();
	            if( str.length()>128 ) {
	                str = str.substring( 0, 128 ) + "...";
	            }
	            pw.print(a.getType()+"["+str+"], ");
	        } else {
	            pw.print(a.getType()+", ");
	        }
            pw.println("Count="+a.getCount()
                      +", total="+formatTime(a.getTotalWallTime())+" ("+formatTime(a.getTotalCpuTime())+")"
            		  +", average="+formatTime(a.getAvgWallTime())+" ("+formatTime(a.getAvgCpuTime())+")"
            		  +", minimum="+formatTime(a.getMinWallTime())+" ("+formatTime(a.getMinCpuTime())+")"
            		  +", maximum="+formatTime(a.getMaxWallTime())+" ("+formatTime(a.getMaxCpuTime())+")"
            );
        }
        for( Aggregator child: a.getChildren()) {
            dump( pw, child, level+1 );
        }
    }
    
    protected static String formatTime(long nanos) {
    	// A negative time is a CPU time that is not available
    	return nanos<0 ? "n/a" : nanos/1_000_000L + "ms";
    }
}
