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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.List;

import org.monflabs.tests.__BaseTestCase;
import org.monflabs.tests.leaks.BootstrapHelper;
import org.monflabs.tests.leaks.ResourceTracker;

/**
 * Tests of the tracker life cycle, the thread filter and the context extraction.
 */
public class ResourceTrackerTest extends BaseLeakTest {

	private File file;

	@Override
	public void setUp() throws Exception {
		file = new File(support.getTargetTempDirectory("tracker-tests", true), "data.txt");
		Files.writeString(file.toPath(), "data");
		super.setUp();
	}

	public void testStopTrackingReleasesResources() throws Exception {
		ResourceTracker tracker = ResourceTracker.getInstance();
		FileInputStream in = new FileInputStream(file);
		try {
			assertEquals(1, tracker.detectLeaks().size());
			tracker.stopTracking();
			assertFalse(tracker.isTracking());
			assertTrue(tracker.detectLeaks().isEmpty());
		} finally {
			in.close();
			tracker.startTracking();
		}
	}

	public void testCurrentThreadOnly() throws Exception {
		ResourceTracker tracker = ResourceTracker.getInstance();
		tracker.startTracking(true);
		FileInputStream[] other = new FileInputStream[1];
		Thread t = new Thread(() -> {
			try {
				other[0] = new FileInputStream(file);
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		});
		t.start();
		t.join();
		try {
			assertTrue(tracker.detectLeaks().isEmpty());
			try(FileInputStream mine = new FileInputStream(file)) {
				assertEquals(1, tracker.detectLeaks().size());
			}
			assertTrue(tracker.detectLeaks().isEmpty());
		} finally {
			other[0].close();
			tracker.startTracking();
		}
	}

	public void testContextWithTraceLevel() {
		ResourceTracker tracker = ResourceTracker.getInstance();
		int old = BootstrapHelper.TRACE_LEVEL;
		BootstrapHelper.TRACE_LEVEL = 1;
		Object r1 = new Object(), r2 = new Object();
		try {
			BootstrapHelper.recordAllocationWithArgs(r1, "Test", new Object[] {"/a/b.txt"});
			// No path argument: used to throw StringIndexOutOfBounds (swallowed) with TRACE_LEVEL>0
			BootstrapHelper.recordAllocationWithArgs(r2, "Test", new Object[] {42});
		} finally {
			BootstrapHelper.TRACE_LEVEL = old;
		}
		List<ResourceTracker.LeakInfo> leaks = tracker.detectLeaks();
		assertEquals(2, leaks.size());
		for(ResourceTracker.LeakInfo l: leaks) {
			if(l.getResource()==r1) {
				assertEquals("\"/a/b.txt\"", l.getAllocationInfo().getContext());
			} else {
				assertSame(r2, l.getResource());
				assertEquals("", l.getAllocationInfo().getContext());
			}
		}
		tracker.recordClosure(r1);
		tracker.recordClosure(r2);
		tracker.recordClosure(null);
		assertTrue(tracker.detectLeaks().isEmpty());
	}

	public void testJarFileContext() {
		assertTrue(__BaseTestCase.isJarFileContext("\"/m2/repo/lib-1.0.jar\""));
		assertTrue(__BaseTestCase.isJarFileContext("File@1a2b:/m2/repo/lib-1.0.JAR"));
		assertTrue(__BaseTestCase.isJarFileContext("jar:file:/lib.jar!/META-INF/MANIFEST.MF"));
		assertFalse(__BaseTestCase.isJarFileContext("\"/data/my.jarvis/file.txt\""));
		assertFalse(__BaseTestCase.isJarFileContext("\"/tmp/archive.jar.txt\""));
		assertFalse(__BaseTestCase.isJarFileContext(""));
		assertFalse(__BaseTestCase.isJarFileContext(null));
	}
}
