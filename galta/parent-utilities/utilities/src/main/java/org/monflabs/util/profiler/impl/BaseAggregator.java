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
import java.util.List;

import org.monflabs.util.profiler.Aggregator;


/**
 * Profiler aggregator.
 */
public abstract class BaseAggregator<T extends BaseAggregator<?>> implements Aggregator {

    protected T parent;
    protected String type;
    protected String param;
    
    protected int count;
    
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
        this.hashCode = type.hashCode() + (param!=null ? param.hashCode() : 0);
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
    // Children HashMap
    //
    
	private static final int SLOT_COUNT = 11;
    private BaseAggregator<?> entries[] = new BaseAggregator[SLOT_COUNT]; 

    // HashMap Entry fields
    int hashCode;
    BaseAggregator<?> next;


	@SuppressWarnings("unchecked")
	public synchronized T getChild(RuntimeAggregator parent, String type, String param) {
        int hashCode = type.hashCode() + (param!=null ? param.hashCode() : 0);
        int slot = (hashCode & 0x7FFFFFFF) % SLOT_COUNT;
        for(BaseAggregator<?> a = entries[slot]; a!=null; a=a.next) {
        	if(a.hashCode==hashCode) {
        		if(type.equals(a.getType())) {
            		if(param==null) {
            			if(a.getParam()==null) {
            				return (T)a;
            			}
            		} else {
            			if(param.equals(a.getParam())) {
            				return (T)a;
            			}
            		}
        		}
        	}
        }
        RuntimeAggregator a = new RuntimeAggregator(parent, type, param);
        a.next = entries[slot];
        entries[slot] = a;
        return (T)a;
    }
    public synchronized void appendChild(T v) {
        int slot = (v.hashCode & 0x7FFFFFFF) % SLOT_COUNT;
        v.next = entries[slot];
        entries[slot] = v;
    }
    
    @SuppressWarnings("unchecked")
	@Override
	public synchronized List<Aggregator> getChildren(){
        List<Aggregator> items = new ArrayList<Aggregator>();
        for(BaseAggregator<?> a: entries) {
        	if(a!=null) {
                for(BaseAggregator<?> b=a; b!=null; b=b.next) {
        			items.add((T)b);
        		}
        	}
        }
        return items;
    }

    public synchronized void clearChildren() {
    	this.entries = new BaseAggregator[SLOT_COUNT];
    }    

    
    
    //
    // Execution 
    //
	
    @Override
	public int getCount() {
        return count;
    }

    @Override
	public long getMinCpuTime() {
        return minCpuTime;
    }
    @Override
	public long getMaxCpuTime() {
        return maxCpuTime;
    }
    @Override
	public long getTotalCpuTime() {
        return totalCpuTime;
    }
    @Override
	public long getChildrenCpuTime() {
    	long ts = 0;
    	for(Aggregator a: getChildren() ) {
    		ts += a.getTotalCpuTime();
    	}
        return ts;
    }
    @Override
	public long getSpecificCpuTime() {
        return getTotalCpuTime()-getChildrenCpuTime();
    }
    @Override
	public long getAvgCpuTime() {
        return count>0 ? totalCpuTime/count : 0;
    }

    @Override
	public long getMinWallTime() {
        return minWallTime;
    }
    @Override
	public long getMaxWallTime() {
        return maxWallTime;
    }
    @Override
	public long getTotalWallTime() {
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
	public long getAvgWallTime() {
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
    	return nanos/1_000_000L + "ms";
    }
}
