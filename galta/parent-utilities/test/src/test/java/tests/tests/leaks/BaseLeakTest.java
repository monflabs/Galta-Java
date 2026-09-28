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
package tests.tests.leaks;

import java.util.List;

import org.monflabs.tests.__BaseTestCase;
import org.monflabs.tests.leaks.ResourceLeakAgent;
import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Comprehensive tests for traditional File I/O resource leak detection.
 * Tests FileInputStream, FileOutputStream, RandomAccessFile, FileReader, FileWriter.
 */
public abstract class BaseLeakTest extends __BaseTestCase {
	
	// Makes sure that resource leak tracking is enabled for these tests
	static {
		ResourceLeakAgent.install();
	}
	
    public static void logIgnore(Object s) {
    	// Nothing
    }
    
	protected void traceLeaks(List<ResourceTracker.LeakInfo> leaks) {
	    for (ResourceTracker.LeakInfo leak : leaks) {
	        System.out.println(leak.formatReport());
	    }
    }
}
