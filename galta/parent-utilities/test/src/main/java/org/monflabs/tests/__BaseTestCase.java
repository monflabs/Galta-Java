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
package org.monflabs.tests;

import java.util.List;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;
import org.monflabs.tests.leaks.ResourceLeakAgent;
import org.monflabs.tests.leaks.ResourceTracker;

import junit.framework.TestCase;

public abstract class __BaseTestCase extends TestCase {
	
	/**
	 * Install the leak detection agent for all the test cases, from the system property
	 * <code>monflabs.tests.trackLeaks</code> (<code>-Dmonflabs.tests.trackLeaks=true</code>).
	 * Without it, the leaks are only checked when the agent was installed by other means,
	 * for example by {@link ResourceLeakAgent#install()} in a static block of the test class.
	 */
	public static final boolean TRACK_LEAKS = Boolean.getBoolean("monflabs.tests.trackLeaks");
	
// use setUp() and tearDown() instead of a Rule
//  @Rule
//  public ResourceLeakRule leakDetector = new ResourceLeakRule();
	static {
		// Runs first
		// 1. **Static block** → Installs agent BEFORE classes load → No retransformation needed (most reliable)
		// 2. **RETRANSFORMATION strategy** → Handles edge cases where classes load anyway → Fallback protection
		if(TRACK_LEAKS) {
			ResourceLeakAgent.install();
		}
	}


	protected UnitTestSupport support;

	// JUnit 4
//	@Rule(order = Integer.MIN_VALUE)
//	public TestRule watcher = new TestWatcher() {
//	   @Override
//	   protected void starting(Description description) {
//	      support.print("Executing Test: {0}{1}", description.getMethodName(), getExtraDescription());
//	   }
//	};
	
	protected __BaseTestCase() {
		// https://stackoverflow.com/questions/9863625/difference-between-est-and-america-new-york-time-zones
		// To simplify the error reporting, as all the date/time are GMT
		//TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
		//TimeZone.setDefault(TimeZone.getTimeZone("EST"));
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
		this.support = new UnitTestSupport(this.getClass());
	}
	
	public String getExtraDescription() {
		return "";
	}
	
	
	public static void sleep() {
		sleep(10);
	}

	public static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	
    @Override
	@Before
	public void setUp() throws Exception {
    	super.setUp();
    	if(shouldTrackResources() && ResourceLeakAgent.isInstalled()) {
    		ResourceTracker.getInstance().startTracking();
    	}
    	//support.print("--------------- START " + getName() + getExtraDescription() + "  ("+getClass().getName()+")");
    }
    protected boolean shouldTrackResources() {
    	return true;
    }
    
    @Override
	@After
	public void tearDown() throws Exception {
    	//support.print("--------------- END " + getName() + getExtraDescription() + "  ("+getClass().getName()+")");
    	try {
	    	if(shouldTrackResources() && ResourceLeakAgent.isInstalled()) {
		        try {
		            checkForLeaks();
		        } finally {
		            ResourceTracker.getInstance().stopTracking();
		        }
	    	}
    	} finally {
    		super.tearDown();
    	}
    }	
    
    protected void checkForLeaks() {
//        System.gc();
//        System.runFinalization();
//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
        if(shouldTrackResources()) {
	        List<ResourceTracker.LeakInfo> leaks = ResourceTracker.getInstance().detectLeaks();
	        if(!leaks.isEmpty()) {
	            StringBuilder msg = new StringBuilder();
	            for (ResourceTracker.LeakInfo leak : leaks) {
	            	if(shouldReportLeak(leak)) {
	                    if(msg.isEmpty()) {
	                        msg.append("\nResource leaks detected:\n");
	                    }
	                	msg.append(leak.formatReport()).append("\n");
	            	}
	            }
	            if(!msg.isEmpty()) {
	            	fail(msg.toString());
	            }
	        }
        }
    }    
    protected boolean shouldReportLeak(ResourceTracker.LeakInfo leakInfo) {
    	// JAR connections when using openStream() are keeping the jar file open
    	// We try to prevent this by ignoring the resources opened on a .jar file
    	return !isJarFileContext(leakInfo.getAllocationInfo().getContext());
    }
    
    /**
     * Tell if an allocation context designates a jar file (a path ending with ".jar", possibly quoted,
     * or an entry inside a jar). A path merely containing ".jar" (e.g. "/data/my.jarvis/file.txt")
     * is not a jar file.
     * @param ctx the allocation context
     * @return true for a jar file
     */
    public static boolean isJarFileContext(String ctx) {
    	if(ctx==null || ctx.isEmpty()) {
    		return false;
    	}
    	String s = ctx.trim();
    	if(s.endsWith("\"")) {
    		s = s.substring(0, s.length()-1);
    	}
    	String lower = s.toLowerCase(java.util.Locale.ROOT);
    	return lower.endsWith(".jar") || lower.contains(".jar!/");
    }
}
