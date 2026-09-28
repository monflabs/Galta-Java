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
package tests.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.monflabs.tests.JavaAccessor;
import org.monflabs.tests.__BaseTestCase;

/**
 * {@link JavaAccessor} used concurrently, and with null arguments.
 */
public class JavaAccessorConcurrencyTest extends __BaseTestCase {

	static class Target {
		private int value = 1;
		private int add(int n) { return value + n; }
		private String describe(Object o) { return "obj:"+o; }
		private String name;
		private Target() {}
		private Target(String name) { this.name = name; }
	}

	public void testConcurrentAccess() throws Exception {
		ExecutorService ex = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> futures = new ArrayList<>();
			for(int t=0; t<8; t++) {
				futures.add(ex.submit(() -> {
					for(int i=0; i<500; i++) {
						// A fresh class each time is not possible: stress the shared per-class caches
						JavaAccessor a = support.getObjectAccessor(new Target());
						assertEquals(1, a.getInt("value"));
						assertEquals(1+i, a.callInt("add", i));
						JavaAccessor c = support.getClassAccessor(Target.class);
						Object o = c.newObject("n"+i);
						assertEquals("n"+i, support.getObjectAccessor(o).get("name"));
					}
					return null;
				}));
			}
			for(Future<?> f: futures) {
				f.get();
			}
		} finally {
			ex.shutdown();
		}
	}

	public void testNullArgument() {
		JavaAccessor a = support.getObjectAccessor(new Target());
		assertEquals("obj:null", a.call("describe", (Object)null));
	}
}
