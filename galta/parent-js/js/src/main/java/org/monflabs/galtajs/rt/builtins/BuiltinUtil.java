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
package org.monflabs.galtajs.rt.builtins;

import java.util.Comparator;

/**
 * Helpers shared by the built-in objects.
 */
public final class BuiltinUtil {

	private BuiltinUtil() {
	}

	/**
	 * Spec IsCallable(value). Implementing {@link Callable} is not enough: every
	 * Proxy implements it, whether its target is callable or not.
	 */
	public static boolean isCallable(Object value) {
		return value instanceof Callable c && c.isCallable();
	}

	/**
	 * Returns the value as a Callable when IsCallable(value) is true, else null.
	 */
	public static Callable asCallable(Object value) {
		return value instanceof Callable c && c.isCallable() ? c : null;
	}

	/**
	 * Resolves a relative index (at(), with(), ...) against a length, without
	 * clamping: the result can be negative or &gt;= len, and is then out of range.
	 * The relative index is the result of ToIntegerOrInfinity (+/-Infinity included).
	 */
	public static double relativeIndex(long len, double relative) {
		return relative>=0 ? relative : len+relative;
	}

	/**
	 * Sorts a[0..n) with a stable merge sort. Unlike Arrays.sort/List.sort, it never
	 * validates the comparator: an inconsistent comparator (e.g. a random one) gives
	 * an implementation-defined order, as the spec requires, never an exception.
	 * Every element ends up in the result exactly once whatever the comparator returns.
	 */
	public static <T> void mergeSort(T[] a, int n, Comparator<? super T> c) {
		if(n<2) {
			return;
		}
		// Insertion sort for small arrays
		if(n<=16) {
			insertionSort(a, 0, n, c);
			return;
		}
		@SuppressWarnings("unchecked")
		T[] tmp = (T[])new Object[n];
		// Bottom-up: sort runs of 16 by insertion, then merge doubling widths
		final int RUN = 16;
		for(int lo=0; lo<n; lo+=RUN) {
			insertionSort(a, lo, Math.min(lo+RUN, n), c);
		}
		T[] src = a;
		T[] dst = tmp;
		for(int width=RUN; width<n; width*=2) {
			for(int lo=0; lo<n; lo+=2*width) {
				int mid = Math.min(lo+width, n);
				int hi = Math.min(lo+2*width, n);
				merge(src, dst, lo, mid, hi, c);
			}
			T[] t = src; src = dst; dst = t;
		}
		if(src!=a) {
			System.arraycopy(src, 0, a, 0, n);
		}
	}

	public static <T> void mergeSort(T[] a, Comparator<? super T> c) {
		mergeSort(a, a.length, c);
	}

	private static <T> void insertionSort(T[] a, int lo, int hi, Comparator<? super T> c) {
		for(int i=lo+1; i<hi; i++) {
			T v = a[i];
			int j = i-1;
			// Strictly greater: equal elements keep their order (stable)
			while(j>=lo && c.compare(a[j], v)>0) {
				a[j+1] = a[j];
				j--;
			}
			a[j+1] = v;
		}
	}

	private static <T> void merge(T[] src, T[] dst, int lo, int mid, int hi, Comparator<? super T> c) {
		int i = lo;
		int j = mid;
		int k = lo;
		// Already ordered runs are copied as is
		if(mid<hi && mid>lo && c.compare(src[mid-1], src[mid])<=0) {
			System.arraycopy(src, lo, dst, lo, hi-lo);
			return;
		}
		while(i<mid && j<hi) {
			// Take from the right run only when strictly smaller (stable)
			if(c.compare(src[j], src[i])<0) {
				dst[k++] = src[j++];
			} else {
				dst[k++] = src[i++];
			}
		}
		while(i<mid) {
			dst[k++] = src[i++];
		}
		while(j<hi) {
			dst[k++] = src[j++];
		}
	}
}
