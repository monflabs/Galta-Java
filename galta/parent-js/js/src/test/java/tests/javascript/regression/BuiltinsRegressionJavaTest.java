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
package tests.javascript.regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.rt.builtins.BuiltinUtil;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayJavaArray;
import org.monflabs.galtajs.rt.builtins.primitives.array.arraylike.JSArrayList;
import org.monflabs.galtajs.rt.builtins.standard.promise.BuiltinPromise;
import org.monflabs.tests.__BaseTestCase;

/**
 * Built-in objects regressions (rt/builtins review) checked through the Java API.
 */
public class BuiltinsRegressionJavaTest extends __BaseTestCase {

	// The merge sort never validates the comparator, is stable and keeps every element
	public void testMergeSort() {
		Random rnd = new Random(42);
		for(int n : new int[] {0, 1, 2, 15, 16, 17, 100, 2000}) {
			Integer[] a = new Integer[n];
			for(int i=0; i<n; i++) a[i] = i;
			BuiltinUtil.mergeSort(a, (x,y) -> rnd.nextInt(3)-1);
			Integer[] sorted = a.clone();
			Arrays.sort(sorted);
			for(int i=0; i<n; i++) assertEquals(i, sorted[i].intValue());
		}
		// Stable: equal keys keep their order
		Integer[] b = new Integer[1000];
		for(int i=0; i<b.length; i++) b[i] = (i*7919)%1000;
		BuiltinUtil.mergeSort(b, Comparator.comparingInt(x -> x/10));
		for(int i=1; i<b.length; i++) {
			assertTrue(b[i-1]/10 < b[i]/10 || (b[i-1]/10==b[i]/10 && indexOf(b[i-1]) < indexOf(b[i])));
		}
	}
	private static int indexOf(int v) {
		// Position of v in the initial (i*7919)%1000 sequence
		for(int i=0; i<1000; i++) if((i*7919)%1000==v) return i;
		return -1;
	}

	// Wrapped Java arrays and lists sort with an inconsistent comparator
	public void testArrayLikeSortInconsistentComparator() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Random rnd = new Random(7);
		int[] ints = new int[2000];
		for(int i=0; i<ints.length; i++) ints[i] = i;
		JSArrayJavaArray.of(env, ints).arraySort((x,y) -> rnd.nextInt(3)-1);
		assertEquals(1999000L, Arrays.stream(ints).asLongStream().sum());
		List<Object> list = new ArrayList<>();
		for(int i=0; i<2000; i++) list.add(i);
		JSArrayList.of(env, list).arraySort((x,y) -> rnd.nextInt(3)-1);
		assertEquals(1999000L, list.stream().mapToLong(o -> ((Integer)o).longValue()).sum());
	}

	// PerformPromiseThen marks the promise handled, with or without onRejected
	public void testPromiseHandledByThen() {
		JSEnvironment env = JavaScriptEnvironment.create();
		Object p = env.createScript("var p = Promise.reject(1); p.then(() => 0); p", "t.js").execute();
		assertTrue(((BuiltinPromise)p).isHandled());
		Object q = env.createScript("var q = Promise.reject(1); q.catch(() => 0); q", "t.js").execute();
		assertTrue(((BuiltinPromise)q).isHandled());
		Object r = env.createScript("Promise.resolve(1)", "t.js").execute();
		assertFalse(((BuiltinPromise)r).isHandled());
	}
}
