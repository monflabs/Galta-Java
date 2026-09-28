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
package tests.util.sort;

import static org.junit.Assert.assertArrayEquals;

import org.monflabs.util.sort.QuickSort;

import tests.ProjectTestCase;

public final class QuickSortTest extends ProjectTestCase {

    public void testVersionNumbers() throws Exception {
    	int[] arr = new int[] {3,1,5,3,8,9,4};
    	
    	QuickSort.Accessor acc = new QuickSort.Accessor() {
			@Override
			public int size() {
				return arr.length;
			}
			@Override
			public void exchange(int idx1, int idx2) {
				int t = arr[idx1];
				arr[idx1] = arr[idx2];
				arr[idx2] = t;
			}
			@Override
			public int compare(int idx1, int idx2) {
				return arr[idx1]-arr[idx2];
			}
		};
		
		QuickSort.sort(acc);
		assertArrayEquals(new int[] {1,3,3,4,5,8,9}, arr);
    }

    private static QuickSort.Accessor accessor(int[] arr) {
    	return new QuickSort.Accessor() {
			@Override
			public int size() {
				return arr.length;
			}
			@Override
			public void exchange(int idx1, int idx2) {
				int t = arr[idx1];
				arr[idx1] = arr[idx2];
				arr[idx2] = t;
			}
			@Override
			public int compare(int idx1, int idx2) {
				return Integer.compare(arr[idx1],arr[idx2]);
			}
		};
    }

    public void testRange() throws Exception {
    	// sort(accessor, offset, length) was an instance method although the class has no state
    	int[] arr = new int[] {9,8,7,6,5,4,3,2,1,0,-1,-2};
    	QuickSort.sort(accessor(arr), 2, 9);
    	assertArrayEquals(new int[] {9,8,-1,0,1,2,3,4,5,6,7,-2}, arr);
    }

    public void testRandom() throws Exception {
    	java.util.Random r = new java.util.Random(42);
    	for(int n: new int[] {0,1,2,3,7,8,100,1000}) {
	    	int[] arr = new int[n];
	    	for(int i=0; i<n; i++) {
	    		arr[i] = r.nextInt(50);
	    	}
	    	int[] expected = arr.clone();
	    	java.util.Arrays.sort(expected);
	    	QuickSort.sort(accessor(arr));
	    	assertArrayEquals(expected, arr);
    	}
    }
}
